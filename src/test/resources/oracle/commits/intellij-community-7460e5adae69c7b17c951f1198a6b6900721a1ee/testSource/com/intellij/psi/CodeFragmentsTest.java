package com.intellij.psi;

import com.intellij.testFramework.PsiTestCase;

public class CodeFragmentsTest extends PsiTestCase{
  public CodeFragmentsTest() {
    myRunCommandForTest = true;
  }

  public void testAddImport() throws Exception {
    PsiCodeFragment fragment = myPsiManager.getElementFactory().createExpressionCodeFragment("AAA.foo()", null, false);
    PsiClass arrayListClass = myPsiManager.findClass("java.util.ArrayList");
    PsiReference ref = fragment.findReferenceAt(0);
    ref.bindToElement(arrayListClass);
    assertEquals("ArrayList.foo()", fragment.getText());
  }
}
