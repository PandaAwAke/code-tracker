package com.intellij.psi.impl.light;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class LightPackageReferenceExpression extends LightPackageReference implements PsiReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.light.LightPackageReferenceExpression");

  public LightPackageReferenceExpression(PsiManager manager, PsiPackage refPackage) {
    super(manager, refPackage);
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

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }
}
