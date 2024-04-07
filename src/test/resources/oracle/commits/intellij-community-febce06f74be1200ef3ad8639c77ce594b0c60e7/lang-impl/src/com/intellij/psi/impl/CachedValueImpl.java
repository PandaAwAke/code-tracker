/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 6, 2002
 * Time: 5:41:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.reference.SoftReference;
import com.intellij.util.TimedReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

public class CachedValueImpl<T> implements CachedValue<T> {
  private static final Object NULL = new Object();
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.CachedValueImpl");
  
  private final PsiManager myManager;
  private final CachedValueProvider<T> myProvider;
  private final boolean myTrackValue;

  private final MyTimedReference<T> myData = new MyTimedReference<T>();

  private long myLastPsiTimeStamp = -1;
  private final JBLock r;
  private final JBLock w;

  public CachedValueImpl(@NotNull PsiManager manager, @NotNull CachedValueProvider<T> provider, boolean trackValue) {
    myManager = manager;
    myProvider = provider;
    myTrackValue = trackValue;
    JBReentrantReadWriteLock rw = LockFactory.createReadWriteLock();
    r = rw.readLock();
    w = rw.writeLock();
  }

  private static class Data<T> implements Disposable {
    private final T myValue;
    private final Object[] myDependencies;
    private final long[] myTimeStamps;

    public Data(final T value, final Object[] dependencies, final long[] timeStamps) {
      myValue = value;
      myDependencies = dependencies;
      myTimeStamps = timeStamps;
    }

    public void dispose() {
      if (myValue instanceof Disposable) {
        Disposer.dispose((Disposable)myValue);
      }
    }
  }

  @Nullable
  public T getValue() {

    r.lock();

    T value;
    try {
      value = getUpToDateOrNull();
      if (value != null) {
        return value == NULL ? null : value;
      }
    } finally {
      r.unlock();
    }

    w.lock();

    try {
      value = getUpToDateOrNull();
      if (value != null) {
        return value == NULL ? null : value;
      }

      CachedValueProvider.Result<T> result = myProvider.compute();
      value = result == null ? null : result.getValue();

      setValue(value, result);

      return value;
    }
    finally {
      w.unlock();
    }
  }

  private void setValue(final T value, final CachedValueProvider.Result<T> result) {
    myData.setData(computeData(value == null ? (T) NULL : value, result == null ? null : result.getDependencyItems()));
    if (result != null) {
      myData.setIsLocked(result.isLockValue());
    }
    else {
      myData.setIsLocked(false);
    }
  }

  public void clear() {
    myData.set(null);
  }

  public void setDataLocked(boolean value) {
    myData.setIsLocked(value);
  }

  public boolean hasUpToDateValue() {
    r.lock();

    try {
      return getUpToDateOrNull() != null;
    }
    finally {
      r.unlock();
    }
  }

  @Nullable
  private T getUpToDateOrNull() {
    final Data<T> data = myData.getData();

    if (data != null) {
      T value = data.myValue;
      if (isUpToDate(data)) {
        return value;
      }
      if (value instanceof Disposable) {
        Disposer.dispose((Disposable)value);
      }
    }
    return null;
  }

  private boolean isUpToDate(@NotNull Data data) {
    if (data.myTimeStamps == null) return true;
    if (myManager.isDisposed()) return false;

    for (int i = 0; i < data.myDependencies.length; i++) {
      Object dependency = data.myDependencies[i];
      if (dependency == null) continue;
      if (isDependencyOutOfDate(dependency, data.myTimeStamps[i])) return false;
    }

    return true;
  }

  private boolean isDependencyOutOfDate(Object dependency, long oldTimeStamp) {
    if (dependency instanceof PsiElement &&
        myLastPsiTimeStamp == myManager.getModificationTracker().getModificationCount()) {
      return false;
    }
    final long timeStamp = getTimeStamp(dependency);
    return timeStamp < 0 || timeStamp != oldTimeStamp;
  }

  private Data<T> computeData(T value, Object[] dependencies) {
    if (dependencies == null) {
      return new Data<T>(value, null, null);
    }

    TLongArrayList timeStamps = new TLongArrayList();
    List<Object> deps = new ArrayList<Object>();
    collectDependencies(timeStamps, deps, dependencies);
    if (myTrackValue) {
      collectDependencies(timeStamps, deps, new Object[]{value});
    }

    myLastPsiTimeStamp = myManager.getModificationTracker().getModificationCount();

    return  new Data<T>(value, ArrayUtil.toObjectArray(deps), timeStamps.toNativeArray());
  }

  private void collectDependencies(TLongArrayList timeStamps, List<Object> resultingDeps, Object[] dependencies) {
    for (Object dependency : dependencies) {
      if (dependency == null || dependency == NULL) continue;
      if (dependency instanceof Object[]) {
        collectDependencies(timeStamps, resultingDeps, (Object[])dependency);
      }
      else {
        resultingDeps.add(dependency);
        timeStamps.add(getTimeStamp(dependency));
      }
    }
  }

  private long getTimeStamp(Object dependency) {
    if (dependency instanceof Reference){
      final Object original = ((Reference)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    
    if (dependency instanceof Ref) {
      final Object original = ((Ref)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }

    if (dependency instanceof ModificationTracker) {
      return ((ModificationTracker)dependency).getModificationCount();
    }

    if (dependency instanceof PsiDirectory) {
      return myManager.getModificationTracker().getOutOfCodeBlockModificationCount();
    }
    
    if (dependency instanceof PsiElement) {
      PsiElement element = (PsiElement)dependency;
      if (!element.isValid()) return -1;
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return -1;
      return containingFile.getModificationStamp();
    }

    if (dependency == PsiModificationTracker.MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getModificationCount();
    }
    else if (dependency == PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getOutOfCodeBlockModificationCount();
    } 
    else if (dependency == PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT) {
      return myManager.getModificationTracker().getJavaStructureModificationCount();
    }
    else {
      LOG.error("Wrong dependency type: " + dependency.getClass());
      return -1;
    }
  }

  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }

  public T setValue(final CachedValueProvider.Result<T> result) {
    w.lock();

    try {
      T value = result.getValue();
      setValue(value, result);
      return value;
    }
    finally {
      w.unlock();
    }
  }

  private static class MyTimedReference<T> extends TimedReference<SoftReference<Data<T>>> {
    private boolean myIsLocked;


    public MyTimedReference() {
      super(null);
    }

    public void setIsLocked(final boolean isLocked) {
      myIsLocked = isLocked;
    }

    protected boolean isLocked() {
      return super.isLocked() || myIsLocked;
    }

    public void setData(final Data<T> data) {
      set(new SoftReference<Data<T>>(data));
    }

    @Nullable
    public Data<T> getData() {
      final SoftReference<Data<T>> ref = get();
      return ref != null ? ref.get() : null;
    }
  }
}
