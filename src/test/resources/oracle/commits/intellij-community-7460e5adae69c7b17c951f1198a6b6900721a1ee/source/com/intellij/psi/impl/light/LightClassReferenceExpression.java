package com.intellij.psi.impl.light;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

public class LightClassReferenceExpression extends LightClassReference implements PsiReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.light.LightClassReferenceExpression");

  public LightClassReferenceExpression(PsiManager manager, String text, PsiClass refClass) {
    super(manager, text, refClass);
  }

  public PsiExpression getQualifierExpression(){
    return null;
  }

  public PsiElement bindToElementViaStaticImport(PsiClass aClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  // very special method
  public void setCachedResolveResult(PsiElement result, Boolean problemWithAccess, Boolean problemWithStatic) {
    LOG.assertTrue(false);
  }

  public PsiType getType(){
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  public void processVariants(PsiScopeProcessor processor){
    throw new RuntimeException("Variants are not available for light references");
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }
}
