package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;

/**
 *  @author dsl
 */
public class LightReferenceParameterList extends LightElement implements PsiReferenceParameterList {
  private final PsiTypeElement[] myTypeElements;
  private final String myText;

  public LightReferenceParameterList(PsiManager manager,
                            PsiTypeElement[] referenceElements) {
    super(manager);
    myTypeElements = referenceElements;
    myText = calculateText();
  }

  private String calculateText() {
    if (myTypeElements.length == 0) return "";
    final StringBuffer buffer = new StringBuffer();
    buffer.append("<");
    for (int i = 0; i < myTypeElements.length; i++) {
      PsiTypeElement type = myTypeElements[i];
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append(type.getText());
    }
    buffer.append(">");
    return buffer.toString();
  }

  public String toString() {
    return "PsiReferenceParameterList";
  }

  public String getText() {
    return myText;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitReferenceParameterList(this);
  }

  public PsiElement copy() {
    final PsiTypeElement[] elements = new PsiTypeElement[myTypeElements.length];
    for (int i = 0; i < myTypeElements.length; i++) {
      PsiTypeElement typeElement = myTypeElements[i];
      elements[i] = (PsiTypeElement) typeElement.copy();
    }
    return new LightReferenceParameterList(myManager, elements);
  }

  public PsiTypeElement[] getTypeParameterElements() {
    return myTypeElements;
  }

  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByTypeElements(myTypeElements);
  }
}
