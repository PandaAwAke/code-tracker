package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author ik
 */
public class XmlElementDescriptorByType extends XmlElementDescriptorImpl {
  private ComplexTypeDescriptor myType;
  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";

  public XmlElementDescriptorByType(XmlTag instanceTag, ComplexTypeDescriptor descriptor) {
    myDescriptorTag = instanceTag;
    myType = descriptor;
  }

  public XmlElementDescriptorByType() {}

  public PsiElement getDeclaration(){
    return myDescriptorTag;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    return true;
  }

  public String getName(PsiElement context){
    return myDescriptorTag.getName();
  }

  public XmlNSDescriptor getNSDescriptor() {
    if (NSDescriptor==null) {
      final XmlFile file = (XmlFile) XmlUtil.getContainingFile(getType().getDeclaration());
      if(file == null) return null;
      final XmlDocument document = file.getDocument();
      if(document == null) return null;
      NSDescriptor = (XmlNSDescriptor)document.getMetaData();
    }

    return NSDescriptor;
  }

  public ComplexTypeDescriptor getType() {
    return myType;
  }

  public String getDefaultName() {
    XmlTag rootTag = ((XmlFile)getType().getDeclaration().getContainingFile()).getDocument().getRootTag();

    if (QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue("elementFormDefault"))) {
      return getQualifiedName();
    }

    return getName();
  }

  public boolean isAbstract() {
    return Boolean.valueOf(myDescriptorTag.getAttributeValue("abstract"));
  }

}
