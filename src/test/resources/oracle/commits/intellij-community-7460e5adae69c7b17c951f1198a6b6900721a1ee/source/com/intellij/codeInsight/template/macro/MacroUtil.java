package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.completion.proc.VariablesProcessor;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

class MacroUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.macro.MacroUtil");

  public static PsiType resultToPsiType(Result result, ExpressionContext context){
    if (result instanceof PsiTypeResult) {
      return ((PsiTypeResult) result).getType();
    }
    Project project = context.getProject();
    String text = result.toString();
    if (text == null) return null;
    PsiManager manager = PsiManager.getInstance(project);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    //-1: Hack to deal with stupid resolve
    PsiElement place = file != null ? file.findElementAt(context.getStartOffset()) : null;
    PsiDeclarationStatement decl = file != null ? (PsiDeclarationStatement) PsiTreeUtil.getParentOfType(place, PsiDeclarationStatement.class) : null;
    if (decl != null) {
      place = file.findElementAt(decl.getTextOffset() -1);
    }
    PsiElementFactory factory = manager.getElementFactory();
    try{
      return factory.createTypeFromText(text, place);
    }
    catch(IncorrectOperationException e){
      return null;
    }
  }

  public static PsiExpression resultToPsiExpression(Result result, ExpressionContext context){
    if (result instanceof PsiElementResult){
      PsiElement element = ((PsiElementResult)result).getElement();
      if (element instanceof PsiExpression){
        return (PsiExpression)element;
      }
    }
    Project project = context.getProject();
    String text = result.toString();
    if (text == null) return null;
    PsiManager manager = PsiManager.getInstance(project);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    //-1: Hack to deal with resolve algorithm
    PsiElement place = file != null ? file.findElementAt(context.getStartOffset()) : null;
    if (place != null) {
      PsiElement parent = place.getParent();
      if (parent != null) {
        PsiElement parentOfParent = parent.getParent();
        if (parentOfParent instanceof PsiDeclarationStatement) {
          place = file.findElementAt(parentOfParent.getTextOffset() -1);
        }
      }
    }
    PsiElementFactory factory = manager.getElementFactory();
    try{
      return factory.createExpressionFromText(text, place);
    }
    catch(IncorrectOperationException e){
      return null;
    }
  }

  public static PsiExpression[] getStandardExpressions(PsiElement place) {
    ArrayList array = new ArrayList();
    PsiElementFactory factory = place.getManager().getElementFactory();
    try {
      array.add(factory.createExpressionFromText("true", null));
      array.add(factory.createExpressionFromText("false", null));

      PsiElement scope = place;
      boolean firstClass = true;
      boolean static_flag = false;
      while (scope != null) {
        if (scope instanceof PsiModifierListOwner && ((PsiModifierListOwner)scope).getModifierList() != null){
          if(((PsiModifierListOwner)scope).hasModifierProperty(PsiModifier.STATIC)){
            static_flag = true;
          }
        }
        if (scope instanceof PsiClass) {
          PsiClass aClass = (PsiClass)scope;

          String name = aClass.getName();
          PsiExpression expr = null;
          if(!static_flag){
            if (firstClass) {
              expr = factory.createExpressionFromText("this", place);
            }
            else {
              if (name != null) {
                expr = factory.createExpressionFromText(name + ".this", place);
              }
            }
            if (expr != null) {
              array.add(expr);
            }
          }
          firstClass = false;
          if (aClass.hasModifierProperty(PsiModifier.STATIC)) break;
        }
        else if (scope instanceof PsiMember) {
          if (((PsiMember)scope).hasModifierProperty(PsiModifier.STATIC)) break;
        }
        scope = scope.getParent();
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return (PsiExpression[])array.toArray(new PsiExpression[array.size()]);
  }

  public static PsiVariable[] getVariablesVisibleAt(final PsiElement place, String prefix) {
    final List<PsiVariable> list = new ArrayList<PsiVariable>();
    VariablesProcessor varproc = new VariablesProcessor(prefix, true, list){
      public boolean execute(PsiElement pe, PsiSubstitutor substitutor) {
        if(!(pe instanceof PsiField) || PsiUtil.isAccessible(((PsiField)pe), place, null)) return super.execute(pe, substitutor);
        return true;
      }
    };
    PsiScopesUtil.treeWalkUp(varproc, place, null);
    return varproc.getResultsAsArray();
  }
}