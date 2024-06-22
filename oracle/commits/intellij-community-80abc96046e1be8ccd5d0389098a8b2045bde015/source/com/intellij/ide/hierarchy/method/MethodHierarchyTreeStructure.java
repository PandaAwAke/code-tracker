package com.intellij.ide.hierarchy.method;

import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

public final class MethodHierarchyTreeStructure extends HierarchyTreeStructure {
  public static final String TYPE = "Method hierarchy for ";
  private final SmartPsiElementPointer myMethod;

  /**
   * Should be called in read action
   */
  public MethodHierarchyTreeStructure(final Project project, final PsiMethod method) {
    super(project, null);
    myBaseDescriptor = buildHierarchyElement(project, method);
    ((MethodHierarchyNodeDescriptor)myBaseDescriptor).setTreeStructure(this);
    myMethod = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(method);
    setBaseElement(myBaseDescriptor); //to set myRoot
  }

  private HierarchyNodeDescriptor buildHierarchyElement(final Project project, final PsiMethod method) {
    final PsiClass suitableBaseClass = findSuitableBaseClass(method);

    HierarchyNodeDescriptor descriptor = null;
    final ArrayList superClasses = createSuperClasses(suitableBaseClass);

    if (!suitableBaseClass.equals(method.getContainingClass())) {
      superClasses.add(0, suitableBaseClass);
    }

    // remove from the top of the branch the classes that contain no 'method'
    for(int i = superClasses.size() - 1; i >= 0; i--){
      final PsiClass psiClass = (PsiClass)superClasses.get(i);

      if (MethodHierarchyUtil.findBaseMethodInClass(method, psiClass, false) == null) {
        superClasses.remove(i);
      }
      else {
        break;
      }
    }

    for(int i = superClasses.size() - 1; i >= 0; i--){
      final PsiClass superClass = (PsiClass)superClasses.get(i);
      final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, superClass, false, MethodHierarchyTreeStructure.this);
      if (descriptor != null){
        descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
      }
      descriptor = newDescriptor;
    }
    final HierarchyNodeDescriptor newDescriptor = new MethodHierarchyNodeDescriptor(project, descriptor, method.getContainingClass(), true, MethodHierarchyTreeStructure.this);
    if (descriptor != null) {
      descriptor.setCachedChildren(new HierarchyNodeDescriptor[] {newDescriptor});
    }
    return newDescriptor;
  }

  private static ArrayList createSuperClasses(PsiClass aClass) {
    if (!aClass.isValid()) {
      return new ArrayList();
    }

    final ArrayList superClasses = new ArrayList();
    while (!isJavaLangObject(aClass)) {
      final PsiClass aClass1 = aClass;
      final PsiClass[] superTypes = aClass1.getSupers();
      PsiClass superType = null;
      // find class first
      for (int i = 0; i < superTypes.length; i++) {
        final PsiClass type = superTypes[i];
        if (!type.isInterface() && !isJavaLangObject(type)) {
          superType = type;
          break;
        }
      }
      // if we haven't found a class, try to find an interface
      if (superType == null) {
        for (int i = 0; i < superTypes.length; i++) {
          final PsiClass type = superTypes[i];
          if (!isJavaLangObject(type)) {
            superType = type;
            break;
          }
        }
      }
      if (superType == null) break;
      if (superClasses.contains(superType)) break;
      superClasses.add(superType);
      aClass = superType;
    }

    return superClasses;
  }

  private static boolean isJavaLangObject(final PsiClass aClass) {
    return "java.lang.Object".equals(aClass.getQualifiedName());
  }

  private static PsiClass findSuitableBaseClass(final PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();

    if (containingClass instanceof PsiAnonymousClass) {
      return containingClass;
    }

    final PsiClass superClass = containingClass.getSuperClass();
    if (superClass == null) {
      return containingClass;
    }

    final boolean isContainingClassesMethod = MethodHierarchyUtil.findBaseMethodInClass(method, superClass, true) != null;

    if (!isContainingClassesMethod) {
      final PsiClass[] interfaces = containingClass.getInterfaces();
      for (int i = 0; i < interfaces.length; i++) {
        final PsiClass anInterface = interfaces[i];
        if (MethodHierarchyUtil.findBaseMethodInClass(method, anInterface, true) != null) {
          return anInterface;
        }
      }
    }

    return containingClass;
  }

  @Nullable
  public final PsiMethod getBaseMethod() {
    final PsiElement element = myMethod.getElement();
    return element instanceof PsiMethod ? (PsiMethod)element : null;
  }


  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiClass psiClass = ((MethodHierarchyNodeDescriptor)descriptor).getPsiClass();

    final PsiClass[] subclasses = getSubclasses(psiClass);

    final ArrayList<HierarchyNodeDescriptor> descriptors = new ArrayList<HierarchyNodeDescriptor>(subclasses.length);
    for (int i = 0; i < subclasses.length; i++) {
      final PsiClass aClass = subclasses[i];
      if (HierarchyBrowserManager.getInstance(myProject).HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED) {
        if (shouldHideClass(aClass)) {
          continue;
        }
      }

      final MethodHierarchyNodeDescriptor d = new MethodHierarchyNodeDescriptor(myProject, descriptor, aClass, false, this);
      descriptors.add(d);
    }
    return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
  }

  private PsiClass[] getSubclasses(final PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) {
      return PsiClass.EMPTY_ARRAY;
    }
    if (psiClass.hasModifierProperty(PsiModifier.FINAL)) {
      return PsiClass.EMPTY_ARRAY;
    }

    final PsiSearchHelper helper = PsiManager.getInstance(myProject).getSearchHelper();

    final ArrayList classes = new ArrayList(Arrays.asList(helper.findInheritors(psiClass, GlobalSearchScope.allScope(myProject), false)));

    final EjbClassRole role = J2EERolesUtil.getEjbRole(psiClass);
    if (role != null && role.isDeclarationRole()) {
      final PsiClass[] implementations = role.findImplementations();
      classes.addAll(Arrays.asList(implementations));
    }

    return (PsiClass[])classes.toArray(new PsiClass[classes.size()]);
  }

  private boolean shouldHideClass(final PsiClass psiClass) {
    final PsiMethod method = getMethod(psiClass, false);
    if (method != null) {
      return false;
    }

    if (isSuperClassForBaseClass(psiClass)) {
      return false;
    }

    final boolean isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT);

    // was it implemented is in superclasses?
    final PsiMethod baseClassMethod = getMethod(psiClass, true);

    final boolean hasBaseImplementation;
    if (baseClassMethod == null) {
      hasBaseImplementation = false;
    }
    else {
      hasBaseImplementation = !baseClassMethod.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    if (hasBaseImplementation || isAbstract) {
      // check inherited classes

      final PsiClass[] subclasses = getSubclasses(psiClass);
      for (int i = 0; i < subclasses.length; i++) {
        final PsiClass subclass = subclasses[i];
        if (!shouldHideClass(subclass)) {
          return false;
        }
      }
      return true;
    }
    else {
      // should define method
      return false;
    }
  }

  private PsiMethod getMethod(final PsiClass aClass, final boolean checkBases) {
    return MethodHierarchyUtil.findBaseMethodInClass(getBaseMethod(), aClass, checkBases);
  }

  boolean isSuperClassForBaseClass(final PsiClass aClass) {
    final PsiMethod baseMethod = getBaseMethod();
    if (baseMethod == null) {
      return false;
    }
    final PsiClass baseClass = baseMethod.getContainingClass();
    if (baseClass == null) {
      return false;
    }
    // NB: parameters here are at CORRECT places!!!
    final boolean isInheritor = baseClass.isInheritor(aClass, true);
    return isInheritor;
  }
}
