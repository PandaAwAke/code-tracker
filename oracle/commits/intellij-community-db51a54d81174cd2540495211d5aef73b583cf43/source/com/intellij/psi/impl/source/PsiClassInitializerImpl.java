package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiClassInitializerImpl extends NonSlaveRepositoryPsiElement implements PsiClassInitializer {
  private PsiModifierListImpl myRepositoryModifierList = null;

  public PsiClassInitializerImpl(PsiManagerEx manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiClassInitializerImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  protected Object clone() {
    PsiClassInitializerImpl clone = (PsiClassInitializerImpl)super.clone();
    clone.myRepositoryModifierList = null;
    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);

    if (repositoryId < 0){
      if (myRepositoryModifierList != null){
        myRepositoryModifierList.setOwner(this);
        myRepositoryModifierList = null;
      }
    }
    else{
      myRepositoryModifierList = (PsiModifierListImpl)bindSlave(ChildRole.MODIFIER_LIST);
    }
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, JspClass.class);
  }

  public PsiModifierList getModifierList(){
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
      synchronized (PsiLock.LOCK) {
        if (myRepositoryModifierList == null){
          myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
        }
        return myRepositoryModifierList;
      }
    }
    else{
      return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
    }
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiCodeBlock getBody(){
    return (PsiCodeBlock)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClassInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiClassInitializer";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return lastParent == null || PsiScopesUtil.walkChildrenScopes(this, processor, substitutor, lastParent, place);
  }

  public Icon getElementIcon(int flags) {
    return createLayeredIcon(Icons.CLASS_INITIALIZER, ElementPresentationUtil.getFlags(this, false));
  }
}

