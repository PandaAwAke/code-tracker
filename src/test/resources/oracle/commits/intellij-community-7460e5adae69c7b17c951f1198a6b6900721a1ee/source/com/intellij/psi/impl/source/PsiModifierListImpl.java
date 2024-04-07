package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.DeclarationView;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import gnu.trove.TObjectIntHashMap;

import java.util.Map;

public class PsiModifierListImpl extends SlaveRepositoryPsiElement implements PsiModifierList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiModifierListImpl");

  private static Map<String, IElementType> NAME_TO_KEYWORD_TYPE_MAP = new HashMap<String, IElementType>();

  static{
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PUBLIC, PUBLIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PROTECTED, PROTECTED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.PRIVATE, PRIVATE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.STATIC, STATIC_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.ABSTRACT, ABSTRACT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.FINAL, FINAL_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.NATIVE, NATIVE_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.SYNCHRONIZED, SYNCHRONIZED_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.STRICTFP, STRICTFP_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.TRANSIENT, TRANSIENT_KEYWORD);
    NAME_TO_KEYWORD_TYPE_MAP.put(PsiModifier.VOLATILE, VOLATILE_KEYWORD);
  }

  private static TObjectIntHashMap<String> NAME_TO_MODIFIER_FLAG_MAP = new TObjectIntHashMap<String>();

  static{
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PUBLIC, ModifierFlags.PUBLIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PROTECTED, ModifierFlags.PROTECTED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PRIVATE, ModifierFlags.PRIVATE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.PACKAGE_LOCAL, ModifierFlags.PACKAGE_LOCAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STATIC, ModifierFlags.STATIC_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.ABSTRACT, ModifierFlags.ABSTRACT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.FINAL, ModifierFlags.FINAL_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.NATIVE, ModifierFlags.NATIVE_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.SYNCHRONIZED, ModifierFlags.SYNCHRONIZED_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.STRICTFP, ModifierFlags.STRICTFP_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.TRANSIENT, ModifierFlags.TRANSIENT_MASK);
    NAME_TO_MODIFIER_FLAG_MAP.put(PsiModifier.VOLATILE, ModifierFlags.VOLATILE_MASK);
  }

  private int myCachedModifiers = -1;
  private PsiAnnotation[] myCachedAnnotations = null;

  public PsiModifierListImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  public PsiModifierListImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedModifiers = -1;
    myCachedAnnotations = null;
  }

  public boolean hasModifierProperty(String name){
    if (getTreeElement() != null){
      IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

      PsiElement parent = getParent();
      if (parent instanceof PsiClass){
        PsiElement pparent = parent.getParent();
        if (pparent instanceof PsiClass && ((PsiClass)pparent).isInterface()){
          if (type == PUBLIC_KEYWORD){
            return true;
          }
          if (type == null){ // package local
            return false;
          }
          if (type == STATIC_KEYWORD){
            return true;
          }
        }
        if (((PsiClass)parent).isInterface()){
          if (type == ABSTRACT_KEYWORD){
            return true;
          }

          // nested interface is implicitly static
          if (pparent instanceof PsiClass) {
            if (type == STATIC_KEYWORD){
              return true;
            }
          }
        }
        if (((PsiClass)parent).isEnum()){
          if (type == STATIC_KEYWORD) {
            return true;
          }
          else if (type == FINAL_KEYWORD) {
            final PsiField[] fields = ((PsiClass)parent).getFields();
            for (int i = 0; i < fields.length; i++) {
              PsiField field = fields[i];
              if (field instanceof PsiEnumConstant && ((PsiEnumConstant)field).getInitializingClass() != null) return false;
            }
            return true;
          }
          else if (type == ABSTRACT_KEYWORD) {
            final PsiMethod[] methods = ((PsiClass)parent).getMethods();
            for (int i = 0; i < methods.length; i++) {
              PsiMethod method = methods[i];
              if (method.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
            }
            return false;
          }
        }
      }
      else if (parent instanceof PsiMethod){
        PsiClass aClass = ((PsiMethod)parent).getContainingClass();
        if (aClass != null && aClass.isInterface()){
          if (type == PUBLIC_KEYWORD){
            return true;
          }
          if (type == null){ // package local
            return false;
          }
          if (type == ABSTRACT_KEYWORD){
            return true;
          }
        }
      }
      else if (parent instanceof PsiField){
        if (parent instanceof PsiEnumConstant) {
          return type == PUBLIC_KEYWORD || type == STATIC_KEYWORD || type == FINAL_KEYWORD;
        }
        else {
          PsiClass aClass = ((PsiField)parent).getContainingClass();
          if (aClass != null && aClass.isInterface()){
            if (type == PUBLIC_KEYWORD){
              return true;
            }
            if (type == null){ // package local
              return false;
            }
            if (type == STATIC_KEYWORD){
              return true;
            }
            if (type == FINAL_KEYWORD){
              return true;
            }
          }
        }
      }

      if (type == null){ // package local
        return !hasModifierProperty(PsiModifier.PUBLIC) && !hasModifierProperty(PsiModifier.PRIVATE) && !hasModifierProperty(PsiModifier.PROTECTED);
      }

      CompositeElement treeElement = calcTreeElement();
      return TreeUtil.findChild(treeElement, type) != null;
    }
    else{
      long repositoryId = getRepositoryId();
      if (myCachedModifiers < 0){
        myCachedModifiers = ((DeclarationView)getRepositoryManager().getItemView(repositoryId)).getModifiers(repositoryId);
      }
      int flag = NAME_TO_MODIFIER_FLAG_MAP.get(name);
      LOG.assertTrue(flag != 0);
      return (myCachedModifiers & flag) != 0;
    }
  }

  public void setModifierProperty(String name, boolean value) throws IncorrectOperationException{
    checkSetModifierProperty(name, value);

    IElementType type = NAME_TO_KEYWORD_TYPE_MAP.get(name);

    CompositeElement treeElement = calcTreeElement();
    CompositeElement parentTreeElement = treeElement.getTreeParent();
    if (value){
      if (parentTreeElement.getElementType() == ElementType.FIELD && parentTreeElement.getTreeParent().getElementType() == ElementType.CLASS && ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()){
        if (type == PUBLIC_KEYWORD || type == STATIC_KEYWORD || type == FINAL_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == ElementType.METHOD  && parentTreeElement.getTreeParent().getElementType() == ElementType.CLASS && ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()){
        if (type == PUBLIC_KEYWORD || type == ABSTRACT_KEYWORD) return;
      }
      else if (parentTreeElement.getElementType() == ElementType.CLASS && parentTreeElement.getTreeParent().getElementType() == ElementType.CLASS && ((PsiClass)SourceTreeToPsiMap.treeElementToPsi(parentTreeElement.getTreeParent())).isInterface()){
        if (type == PUBLIC_KEYWORD) return;
      }

      if (type == PUBLIC_KEYWORD
        || type == PRIVATE_KEYWORD
        || type == PROTECTED_KEYWORD
        || type == null /* package local */){

        if (type != PUBLIC_KEYWORD){
          setModifierProperty(PsiModifier.PUBLIC, false);
        }
        if (type != PRIVATE_KEYWORD){
          setModifierProperty(PsiModifier.PRIVATE, false);
        }
        if (type != PROTECTED_KEYWORD){
          setModifierProperty(PsiModifier.PROTECTED, false);
        }
        if (type == null) return;
      }

      if (TreeUtil.findChild(treeElement, type) == null){
        TreeElement keyword = Factory.createSingleLeafElement(type, name.toCharArray(), 0, name.length(), null, getManager());
        treeElement.addInternal(keyword, keyword, null, null);
      }
      if ((type == ABSTRACT_KEYWORD || type == NATIVE_KEYWORD) && parentTreeElement.getElementType() == METHOD){
        //Q: remove body?
      }
    }
    else{
      if (type == null){ // package local
        throw new IncorrectOperationException("Cannot reset package local modifier."); //?
      }
      TreeElement child = TreeUtil.findChild(treeElement, type);
      if (child != null){
        SourceTreeToPsiMap.treeElementToPsi(child).delete();
      }
    }
  }

  public void checkSetModifierProperty(String name, boolean value) throws IncorrectOperationException{
    CheckUtil.checkWritable(this);
  }

  public PsiAnnotation[] getAnnotations() {
    if (myCachedAnnotations == null) {
      if (getTreeElement() != null) {
        myCachedAnnotations = (PsiAnnotation[])calcTreeElement().getChildrenAsPsiElements(ANNOTATION_BIT_SET, PSI_ANNOTATION_ARRAY_CONSTRUCTOR);
      }
      else {
        long parentId = ((SrcRepositoryPsiElement)getParent()).getRepositoryId();
        DeclarationView view = (DeclarationView)getRepositoryManager().getItemView(parentId);
        String[] annotationStrings = view.getAnnotations(parentId);
        myCachedAnnotations = new PsiAnnotation[annotationStrings.length];
        for (int i = 0; i < annotationStrings.length; i++) {
          try {
            myCachedAnnotations[i] = getManager().getElementFactory().createAnnotationFromText(annotationStrings[i], this);
          }
          catch (IncorrectOperationException e) {
            LOG.error("Bad annotation text in repository: " + annotationStrings[i]);
          }
        }
      }
    }
    return myCachedAnnotations;
  }

  public PsiAnnotation findAnnotation(String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitModifierList(this);
  }

  public String toString(){
    return "PsiModifierList:" + getText();
  }
}
