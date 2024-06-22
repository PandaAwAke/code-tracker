package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;

class ClsPackageStatementImpl extends ClsElementImpl implements PsiPackageStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsPackageStatementImpl");

  private ClsFileImpl myFile;
  private final String myPackageName;

  public ClsPackageStatementImpl(ClsFileImpl file) {
    myFile = file;
    String className = myFile.getClasses()[0].getQualifiedName();
    int index = className.lastIndexOf('.');
    if (index < 0){
      myPackageName = null;
    }
    else{
      myPackageName = className.substring(0, index);
    }
  }

  public PsiElement getParent() {
    return myFile;
  }

  void invalidate() {
    myFile = null;
  }

  /**
   * @not_implemented
   */
  public PsiJavaCodeReferenceElement getPackageReference() {
    LOG.error("method not implemented");
    return null;
  }

  /**
   * @not_implemented
   */
  public PsiModifierList getAnnotationList() {
    LOG.error("method not implemented");
    return null;
  }

  /**
   * @not_implemented
   */
  public PsiElement[] getChildren() {
    LOG.error("method not implemented");
    return null;
  }

  public String getPackageName() {
    return myPackageName;
  }

  public String getMirrorText() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("package ");
    buffer.append(getPackageName());
    buffer.append(";");
    return buffer.toString();
  }

  public void setMirror(TreeElement element) {
    LOG.assertTrue(myMirror == null);
    LOG.assertTrue(element.getElementType() == ElementType.PACKAGE_STATEMENT);
    myMirror = element;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPackageStatement(this);
  }

  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}
