package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;

class ClsIdentifierImpl extends ClsElementImpl implements PsiIdentifier, PsiJavaToken {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsIdentifierImpl");

  private final PsiElement myParent;
  private final String myText;

  public ClsIdentifierImpl(PsiElement parent, String text) {
    myParent = parent;
    myText = text;
  }

  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  public String getText() {
    return myText;
  }

  public PsiElement[] getChildren(){
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent(){
    return myParent;
  }

  public String getMirrorText(){
    return getText();
  }

  public void setMirror(TreeElement element){
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.IDENTIFIER);
    myMirror = element;
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitIdentifier(this);
  }

  public String toString() {
    return "PsiIdentifier:" + getText();
  }
}
