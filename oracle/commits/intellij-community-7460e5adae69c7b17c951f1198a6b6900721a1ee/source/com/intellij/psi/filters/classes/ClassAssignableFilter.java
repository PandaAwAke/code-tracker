package com.intellij.psi.filters.classes;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.filters.ElementFilter;
import org.jdom.Element;

import java.lang.ref.SoftReference;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 26.03.2003
 * Time: 21:01:47
 * To change this template use Options | File Templates.
 */
public abstract class ClassAssignableFilter implements ElementFilter{
  protected String myClassName = null;
  protected PsiClass myClass = null;
  private SoftReference myCachedClass = new SoftReference(null);

  public abstract boolean isAcceptable(Object aClass, PsiElement context);
  public abstract String toString();

  public boolean isClassAcceptable(Class hintClass){
    return PsiClass.class.isAssignableFrom(hintClass);
  }


  protected PsiClass getPsiClass(PsiManager manager, GlobalSearchScope scope){
    if(myClass != null){
      return myClass;
    }

    if(myCachedClass.get() == null && manager != null){
      myCachedClass = new SoftReference(manager.findClass(myClassName, scope));
    }
    return (PsiClass) myCachedClass.get();
  }

  public void readExternal(Element element)
    throws InvalidDataException{
    myClassName = element.getTextTrim();
  }

  public void writeExternal(Element element)
    throws WriteExternalException{
    throw new WriteExternalException("Filter data could _not_ be written");
  }
}
