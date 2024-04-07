package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SmartPointerManagerImpl extends SmartPointerManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");

  private static final Key<ArrayList<WeakReference<SmartPointerEx>>> SMART_POINTERS_IN_PSI_FILE_KEY = Key.create(
    "SMART_POINTERS_IN_DOCUMENT_KEY");
  private static final Key<Boolean> BELTS_ARE_FASTEN_KEY = Key.create("BELTS_ARE_FASTEN_KEY");

  private final Project myProject;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
  }


  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "SmartPointerManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void fastenBelts(PsiFile file) {
    synchronized (file) {
      file.putUserData(BELTS_ARE_FASTEN_KEY, Boolean.TRUE);

      ArrayList<WeakReference<SmartPointerEx>> pointers = file.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
      if (pointers == null) return;

      int index = 0;
      for (int i = 0; i < pointers.size(); i++) {
        WeakReference<SmartPointerEx> reference = pointers.get(i);
        SmartPointerEx pointer = reference.get();
        if (pointer != null) {
          pointer.fastenBelt();
          pointers.set(index++, reference);
        }
      }

      int size = pointers.size();
      for (int i = size - 1; i >= index; i--) {
        pointers.remove(i);
      }
    }
  }

  public void unfastenBelts(PsiFile file) {
    synchronized (file) {
      file.putUserData(BELTS_ARE_FASTEN_KEY, null);
    }
  }

  public void synchronizePointers(PsiFile file) {
    synchronized (file) {
      ArrayList<WeakReference<SmartPointerEx>> pointers = file.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
      if (pointers == null) return;

      int index = 0;
      for (int i = 0; i < pointers.size(); i++) {
        WeakReference<SmartPointerEx> reference = pointers.get(i);
        SmartPointerEx pointer = reference.get();
        if (pointer != null) {
          pointer.documentAndPsiInSync();
          pointers.set(index++, reference);
        }
      }

      int size = pointers.size();
      for (int i = size - 1; i >= index; i--) {
        pointers.remove(i);
      }
    }
  }

  public SmartPsiElementPointer createSmartPsiElementPointer(PsiElement element) {
    if (!element.isValid()) {
      LOG.assertTrue(false, "Invalid element:" + element);
    }

    SmartPointerEx pointer = new SmartPsiElementPointerImpl(myProject, element);
    initPointer(element, pointer);

    return pointer;
  }

  private void initPointer(PsiElement element, SmartPointerEx pointer) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      synchronized (file) {
        Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
        if (document != null) {
          //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
          if (!(element instanceof PsiFile)) {
            LOG.assertTrue(!PsiDocumentManager.getInstance(myProject).isUncommited(document));
          }
        }

        ArrayList<WeakReference<SmartPointerEx>> pointers = file.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
        if (pointers == null) {
          pointers = new ArrayList<WeakReference<SmartPointerEx>>();
          file.putUserData(SMART_POINTERS_IN_PSI_FILE_KEY, pointers);
        }
        pointers.add(new WeakReference<SmartPointerEx>(pointer));

        Boolean isFasten = file.getUserData(BELTS_ARE_FASTEN_KEY);
        if (isFasten == Boolean.TRUE) {
          pointer.fastenBelt();
        }
      }
    }
  }

  public SmartTypePointer createSmartTypePointer(PsiType type) {
    return type.accept(new SmartTypeCreatingVisitor());
  }

  public SmartPsiElementPointer createLazyPointer(PsiElement element) {
    LazyPointerImpl pointer = new LazyPointerImpl(element);
    initPointer(element, pointer);
    return pointer;
  }

  private static class SimpleTypePointer implements SmartTypePointer {
    private final PsiType myType;

    private SimpleTypePointer(PsiType type) {
      myType = type;
    }

    public PsiType getType() {
      return myType;
    }
  }

  private static class ArrayTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartTypePointer myComponentTypePointer;

    public ArrayTypePointer(PsiType type, SmartTypePointer componentTypePointer) {
      myType = type;
      myComponentTypePointer = componentTypePointer;
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiType type = myComponentTypePointer.getType();
      if (type == null) {
        myType = null;
      }
      else {
        myType = new PsiArrayType(type);
      }
      return myType;
    }
  }

  private static class WildcardTypePointer implements SmartTypePointer {
    private PsiWildcardType myType;
    private PsiManager myManager;
    private final SmartTypePointer myBoundPointer;
    private boolean myIsExtending;

    public WildcardTypePointer(PsiWildcardType type, SmartTypePointer boundPointer) {
      myType = type;
      myManager = myType.getManager();
      myBoundPointer = boundPointer;
      myIsExtending = myType.isExtends();
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      if (myBoundPointer == null) {
        return PsiWildcardType.createUnbounded(myManager);
      }
      else {
        if (myIsExtending) {
          return PsiWildcardType.createExtends(myManager, myBoundPointer.getType());
        }
        else {
          return PsiWildcardType.createSuper(myManager, myBoundPointer.getType());
        }
      }
    }
  }


  private static class ClassTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartPsiElementPointer myClass;
    private final Map<SmartPsiElementPointer, SmartTypePointer> myMap;


    public ClassTypePointer(PsiType type,
                            SmartPsiElementPointer aClass,
                            Map<SmartPsiElementPointer, SmartTypePointer> map) {
      myType = type;
      myClass = aClass;
      myMap = map;
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiElement classElement = myClass.getElement();
      if (!(classElement instanceof PsiClass)) return null;
      Map<PsiTypeParameter, PsiType> resurrected = new HashMap<PsiTypeParameter, PsiType>();
      final Set<Map.Entry<SmartPsiElementPointer, SmartTypePointer>> set = myMap.entrySet();
      for (Iterator<Map.Entry<SmartPsiElementPointer, SmartTypePointer>> iterator = set.iterator();
           iterator.hasNext();) {
        Map.Entry<SmartPsiElementPointer, SmartTypePointer> entry = iterator.next();
        PsiElement element = entry.getKey().getElement();
        if (element instanceof PsiTypeParameter) {
          resurrected.put(((PsiTypeParameter)element), entry.getValue().getType());
        }
      }
      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator((PsiClass) classElement);
      while (iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        if (!resurrected.containsKey(typeParameter)) {
          resurrected.put(typeParameter, null);
        }
      }
      final PsiSubstitutor resurrectedSubstitutor = PsiSubstitutorImpl.createSubstitutor(resurrected);
      myType = new PsiImmediateClassType(((PsiClass)classElement), resurrectedSubstitutor);
      return myType;
    }
  }

  private class ClassReferenceTypePointer implements SmartTypePointer {
    private PsiType myType;
    private final SmartPsiElementPointer mySmartPsiElementPointer;
    private final String myReferenceText;

    ClassReferenceTypePointer(PsiClassReferenceType type) {
      myType = type;
      final PsiJavaCodeReferenceElement reference = type.getReference();
      mySmartPsiElementPointer = createSmartPsiElementPointer(reference);
      myReferenceText = reference.getText();
    }

    public PsiType getType() {
      if (myType.isValid()) return myType;
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)mySmartPsiElementPointer.getElement();
      final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
      if (referenceElement != null) {
        final PsiClassType type = factory.createType(referenceElement);
        myType = type;
      }
      else {
        try {
          myType = factory.createTypeFromText(myReferenceText, null);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      return myType;
    }
  }

  private class SmartTypeCreatingVisitor extends PsiTypeVisitor<SmartTypePointer> {
    public SmartTypePointer visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return new SimpleTypePointer(primitiveType);
    }

    public SmartTypePointer visitArrayType(PsiArrayType arrayType) {
      return new ArrayTypePointer(arrayType, arrayType.getComponentType().accept(this));
    }

    public SmartTypePointer visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      final SmartTypePointer boundPointer;
      if (bound == null) {
        boundPointer = null;
      }
      else {
        boundPointer = bound.accept(this);
      }
      return new WildcardTypePointer(wildcardType, boundPointer);
    }

    public SmartTypePointer visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) {
        LOG.assertTrue(classType instanceof PsiClassReferenceType);
        return new ClassReferenceTypePointer((PsiClassReferenceType)classType);
      }
      if (classType instanceof PsiClassReferenceType) {
        classType = ((PsiClassReferenceType)classType).createImmediateCopy();
      }
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final com.intellij.util.containers.HashMap<SmartPsiElementPointer, SmartTypePointer> map = new com.intellij.util.containers.HashMap<SmartPsiElementPointer, SmartTypePointer>();
      final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
      while (iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        final PsiType substitutionResult = substitutor.substitute(typeParameter);
        if (substitutionResult != null) {
          final SmartPsiElementPointer pointer = createSmartPsiElementPointer(typeParameter);
          map.put(pointer, substitutionResult.accept(this));
        }
      }
      return new ClassTypePointer(classType, createSmartPsiElementPointer(aClass), map);
    }
  }
}
