package com.intellij.psi.filters.classes;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.filters.*;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:37:26
 * To change this template use Options | File Templates.
 */
public class AnyInnerFilter extends ClassFilter{
  ElementFilter myFilter;
  public AnyInnerFilter(){
    super(PsiClass.class);
  }

  public AnyInnerFilter(ElementFilter filter){
    this();
    myFilter = filter;
  }

  public ElementFilter getFilter(){
    return myFilter;
  }

  public void setFilter(ElementFilter myFilter){
    this.myFilter = myFilter;
  }

  public boolean isAcceptable(Object classElement, PsiElement place){
    if(classElement instanceof PsiClass){
      final PsiClass[] inners = ((PsiClass)classElement).getInnerClasses();
      for(int j = 0; j < inners.length; j++){
        final PsiClass inner = inners[j];
        if(inner.hasModifierProperty(PsiModifier.STATIC)
          && PsiUtil.isAccessible(inner, place, null)
          && myFilter.isAcceptable(inner, place)){
          return true;
        }
      }
    }
    return false;
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    myFilter = (ElementFilter)FilterUtil.readFilterGroup(element).get(0);
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }

  public String toString(){
    return "any-inner(" + getFilter().toString() + ")";
  }
}
