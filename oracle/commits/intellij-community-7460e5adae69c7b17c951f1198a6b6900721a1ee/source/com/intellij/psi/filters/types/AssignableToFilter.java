package com.intellij.psi.filters.types;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.filters.InitializableFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.infos.CandidateInfo;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.01.2003
 * Time: 20:53:38
 * To change this template use Options | File Templates.
 */
public class AssignableToFilter implements InitializableFilter{
  private PsiType myType = null;
  private ElementFilter myFilter = null;

  public AssignableToFilter(PsiType type){
    myType = type;
  }

  public AssignableToFilter(){}

  public void init(Object[] type){
    myFilter = new OrFilter();
    final List<ElementFilter> filters = new ArrayList<ElementFilter>();
    for (int i = 0; i < type.length; i++) {
      final Object o = type[i];
      PsiType currentType = null;
      if(o instanceof PsiType)
        currentType = (PsiType) o;
      else if(o instanceof PsiClass){
        final PsiClass psiClass = (PsiClass)o;
        currentType = psiClass.getManager().getElementFactory().createType(psiClass);
      }
      if(currentType != null){
        filters.add(new AssignableToFilter(currentType));
      }
    }
    myFilter = new OrFilter(filters.toArray(new ElementFilter[filters.size()]));
  }

  public boolean isClassAcceptable(Class hintClass){
    return true;
  }

  public boolean isAcceptable(Object element, PsiElement context){
    if(myType != null){
      if(element == null) return false;
      if (element instanceof PsiType) return myType.isAssignableFrom((PsiType) element);
      PsiSubstitutor substitutor = null;
      if(element instanceof CandidateInfo){
        final CandidateInfo info = (CandidateInfo)element;
        substitutor = info.getSubstitutor();
        element = info.getElement();
      }

      PsiType typeByElement = FilterUtil.getTypeByElement((PsiElement)element, context);
      if(substitutor != null) typeByElement = substitutor.substitute(typeByElement);
      return typeByElement != null && typeByElement.isAssignableFrom(myType) && !typeByElement.equals(myType);
    }
    else if(myFilter != null){
      if(element == null) return false;
      return myFilter.isAcceptable(element, context);
    }
    else return false;
  }

  public void readExternal(Element element) throws InvalidDataException{
  }

  public void writeExternal(Element element) throws WriteExternalException{
  }

  public String toString(){
    if(myType != null)
      return "assignable-to(" + myType + ")";
    else if(myFilter != null) return myFilter.toString();
    return "uninitialized-equals-filter";
  }
}
