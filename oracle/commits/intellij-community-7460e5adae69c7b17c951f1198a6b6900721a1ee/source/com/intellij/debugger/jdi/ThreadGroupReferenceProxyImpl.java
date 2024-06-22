/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.debugger.engine.jdi.ThreadGroupReferenceProxy;
import com.sun.jdi.ThreadGroupReference;
import com.sun.jdi.ThreadReference;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ThreadGroupReferenceProxyImpl extends ObjectReferenceProxyImpl implements ThreadGroupReferenceProxy{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl");
  //caches
  private ThreadGroupReferenceProxyImpl myParentThreadGroupProxy;
  private boolean myIsParentGroupCached = false;
  private String myName;

  public ThreadGroupReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ThreadGroupReference threadGroupReference) {
    super(virtualMachineProxy, threadGroupReference);
    LOG.assertTrue(threadGroupReference != null);
  }

  public ThreadGroupReference getThreadGroupReference() {
    return (ThreadGroupReference)getObjectReference();
  }

  public String name() {
    checkValid();
    if (myName == null) {
      myName = getThreadGroupReference().name();
    }
    return myName;
  }

  public ThreadGroupReferenceProxyImpl parent() {
    checkValid();
    if (!myIsParentGroupCached) {
      myParentThreadGroupProxy = getVirtualMachineProxy().getThreadGroupReferenceProxy(getThreadGroupReference().parent());
      myIsParentGroupCached = true;
    }
    return myParentThreadGroupProxy;
  }

  public String toString() {
    return "ThreadGroupReferenceProxy: " + getThreadGroupReference().toString();
  }

  public void suspend() {
    getThreadGroupReference().suspend();
  }

  public void resume() {
    getThreadGroupReference().resume();
  }

  public List<ThreadReferenceProxyImpl> threads() {
    List<ThreadReference> list = getThreadGroupReference().threads();
    List<ThreadReferenceProxyImpl> proxies = new ArrayList<ThreadReferenceProxyImpl>(list.size());

    for (Iterator<ThreadReference> iterator = list.iterator(); iterator.hasNext();) {
      ThreadReference threadReference = iterator.next();
      proxies.add(getVirtualMachineProxy().getThreadReferenceProxy(threadReference));
    }
    return proxies;
  }

  public List<ThreadGroupReferenceProxyImpl> threadGroups() {
    List<ThreadGroupReference> list = getThreadGroupReference().threadGroups();
    List<ThreadGroupReferenceProxyImpl> proxies = new ArrayList<ThreadGroupReferenceProxyImpl>(list.size());

    for (Iterator<ThreadGroupReference> iterator = list.iterator(); iterator.hasNext();) {
      ThreadGroupReference threadGroupReference = iterator.next();
      proxies.add(getVirtualMachineProxy().getThreadGroupReferenceProxy(threadGroupReference));
    }
    return proxies;
  }

  public void clearCaches() {
//    myIsParentGroupCached = false;
//    myName = null;
//    myParentThreadGroupProxy = null;
    super.clearCaches();
  }
}
