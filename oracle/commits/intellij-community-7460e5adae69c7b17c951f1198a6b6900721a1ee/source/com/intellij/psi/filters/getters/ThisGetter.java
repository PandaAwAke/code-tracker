package com.intellij.psi.filters.getters;

import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.completion.CompletionContext;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.12.2003
 * Time: 14:02:59
 * To change this template use Options | File Templates.
 */
public class ThisGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    boolean first = true;
    final List<PsiExpression> expressions = new ArrayList<PsiExpression>();
    final PsiElementFactory factory = context.getManager().getElementFactory();

    while(context != null){
      if(context instanceof PsiClass){
        final String expressionText;
        if(first){
          first = false;
          expressionText = "this";
        }
        else expressionText = ((PsiClass)context).getName() + ".this";
        try{
          expressions.add(factory.createExpressionFromText(expressionText, context));
        }
        catch(IncorrectOperationException ioe){}
      }
      if(context instanceof PsiModifierListOwner){
        if(((PsiModifierListOwner)context).hasModifierProperty(PsiModifier.STATIC)) break;
      }
      context = context.getContext();
    }
    return expressions.toArray();
  }
}
