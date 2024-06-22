package com.intellij.xml.impl.dtd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class XmlAttributeDescriptorImpl extends BasicXmlAttributeDescriptor {
  private XmlAttributeDecl myDecl;
  private boolean required;
  private String myName;

  public XmlAttributeDescriptorImpl() {

  }
  public XmlAttributeDescriptorImpl(XmlAttributeDecl decl) {
    init(decl);
  }

  public boolean isRequired() {
    return required;
  }

  public PsiElement getDeclaration(){
    return myDecl;
  }

  public String getName() {
    if (myName!=null) {
      return myName;
    }
    myName = myDecl.getNameElement().getText();
    return myName;
  }

  public void init(PsiElement element){
    myDecl = (XmlAttributeDecl) element;
    required = myDecl.isAttributeRequired();
  }

  public Object[] getDependences(){
    return new Object[]{myDecl};
  }

  public boolean isFixed() {
    return myDecl.isAttributeFixed();
  }

  public boolean hasIdType() {
    return myDecl.isIdAttribute();
  }

  public boolean hasIdRefType() {
    return myDecl.isIdRefAttribute();
  }

  public String getDefaultValue() {
    XmlAttributeValue value = myDecl.getDefaultValue();
    if (value != null) {
      String text = value.getText();

      return text.substring(1, text.length() - 1);
    }

    return null;
  }

  public boolean isEnumerated() {
    return myDecl.isEnumerated();
  }

  public String[] getEnumeratedValues() {
    List result = new ArrayList();

    XmlElement[] values = myDecl.getEnumeratedValues();

    for (int i = 0; i < values.length; i++) {
      XmlElement value = values[i];
      result.add(value.getText());
    }

    return (String[])result.toArray(new String[result.size()]);
  }

  public String getQualifiedName() {
    return getName();
  }

  public String getDefaultName() {
    return getName();
  }
}
