package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaDataBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class ClsAnnotationImpl extends ClsElementImpl implements PsiAnnotation, Navigatable {
  public static final ClsAnnotationImpl[] EMPTY_ARRAY = new ClsAnnotationImpl[0];
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.compiled.ClsAnnotationImpl");
  private final ClsJavaCodeReferenceElementImpl myReferenceElement;
  private ClsAnnotationParameterListImpl myParameterList;
  private final ClsElementImpl myParent;
  public static final ClsAnnotationImpl[][] EMPTY_2D_ARRAY = new ClsAnnotationImpl[0][];

  public ClsAnnotationImpl(ClsJavaCodeReferenceElementImpl referenceElement, ClsElementImpl parent) {
    myReferenceElement = referenceElement;
    myParent = parent;
  }

  public void setParameterList(ClsAnnotationParameterListImpl parameterList) {
    myParameterList = parameterList;
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    buffer.append("@").append(myReferenceElement.getCanonicalText());
    myParameterList.appendMirrorText(indentLevel, buffer);
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(!CHECK_MIRROR_ENABLED || myMirror == null);
    myMirror = element;

    PsiAnnotation mirror = (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(element);
      ((ClsElementImpl)getParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getParameterList()));
      ((ClsElementImpl)getNameReferenceElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameReferenceElement()));
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myReferenceElement, myParameterList};
  }

  public PsiElement getParent() {
    return myParent;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return myParameterList;
  }

  @Nullable public String getQualifiedName() {
    if (myReferenceElement == null) return null;
    return myReferenceElement.getCanonicalText();
  }

  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return myReferenceElement;
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  public String getText() {
    final StringBuffer buffer = new StringBuffer();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  public PsiMetaDataBase getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

}
