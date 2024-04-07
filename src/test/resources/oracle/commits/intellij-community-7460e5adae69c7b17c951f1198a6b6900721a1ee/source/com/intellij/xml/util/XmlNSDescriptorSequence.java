package com.intellij.xml.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 08.09.2003
 * Time: 17:27:43
 * To change this template use Options | File Templates.
 */
public class XmlNSDescriptorSequence implements XmlNSDescriptor{
  final List<XmlNSDescriptor> sequence = new ArrayList<XmlNSDescriptor>();

  public XmlNSDescriptorSequence(){
  }

  public XmlNSDescriptorSequence(XmlNSDescriptor[] descriptors){
    for(int i = 0; i < descriptors.length; i++){
      final XmlNSDescriptor descriptor = descriptors[i];
      add(descriptor);
    }
  }

  public void add(XmlNSDescriptor descriptor){
    sequence.add(descriptor);
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final XmlElementDescriptor elementDescriptor = descriptor.getElementDescriptor(tag);
      if(elementDescriptor != null) return elementDescriptor;
    }
    return null;
  }

  public XmlElementDescriptor[] getRootElementsDescriptors() {
    final List<XmlElementDescriptor> descriptors = new ArrayList<XmlElementDescriptor>();
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      descriptors.addAll(Arrays.asList(descriptor.getRootElementsDescriptors()));
    }

    return descriptors.toArray(new XmlElementDescriptor[descriptors.size()]);
  }

  public XmlFile getDescriptorFile(){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final XmlFile file = descriptor.getDescriptorFile();
      if(file != null) return file;
    }
    return null;
  }

  public boolean isHierarhyEnabled() {
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      if(descriptor.isHierarhyEnabled()) return true;
    }
    return false;
  }

  public PsiElement getDeclaration(){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final PsiElement declaration = descriptor.getDeclaration();
      if(declaration != null) return declaration;
    }
    return null;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      if(!descriptor.processDeclarations(context, processor, substitutor, lastElement, place)) return false;
    }

    return true;
  }

  public String getName(PsiElement context){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final String name = descriptor.getName(context);
      if(name != null) return name;
    }
    return null;
  }

  public String getName(){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      final String name = descriptor.getName();
      if(name != null) return name;
    }
    return null;
  }

  public void init(PsiElement element){
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      descriptor.init(element);
    }
  }

  public Object[] getDependences(){
    final List<Object> ret = new ArrayList<Object>();
    final Iterator iterator = sequence.iterator();
    while(iterator.hasNext()){
      final XmlNSDescriptor descriptor = (XmlNSDescriptor) iterator.next();
      ret.addAll(Arrays.asList(descriptor.getDependences()));
    }
    return ret.toArray();
  }
}
