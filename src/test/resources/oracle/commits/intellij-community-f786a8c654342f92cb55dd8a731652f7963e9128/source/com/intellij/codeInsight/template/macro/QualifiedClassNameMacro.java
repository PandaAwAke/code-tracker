package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class QualifiedClassNameMacro implements Macro {

  public String getName() {
    return "qualifiedClassName";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.qualified.class.name");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(Expression[] params, final ExpressionContext context) {
    Project project = context.getProject();
    int templateStartOffset = context.getTemplateStartOffset();
    final int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    while(place != null){
      if (place instanceof PsiClass && !(place instanceof PsiAnonymousClass) && !(place instanceof PsiTypeParameter)){
        final PsiClass psiClass = ((PsiClass)place);
        return new TextResult(psiClass.getQualifiedName());
      }
      place = place.getParent();
    }

    return null;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    return null;
  }
}
