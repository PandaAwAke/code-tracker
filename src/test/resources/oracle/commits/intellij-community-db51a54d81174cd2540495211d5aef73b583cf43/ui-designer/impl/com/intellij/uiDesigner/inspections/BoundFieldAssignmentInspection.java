/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.binding.FieldFormReference;
import com.intellij.uiDesigner.binding.FormReferenceProvider;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class BoundFieldAssignmentInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  public String getGroupDisplayName() {
    return UIDesignerBundle.message("form.inspections.group");
  }

  @NotNull
  public String getDisplayName() {
    return UIDesignerBundle.message("inspection.bound.field.title");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "BoundFieldAssignment";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (expression.getLExpression() instanceof PsiReferenceExpression) {
          PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
          if (method != null && AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) {
            return;
          }
          PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
          PsiElement lElement = lExpr.resolve();
          if (lElement instanceof PsiField) {
            PsiField field = (PsiField) lElement;
            PsiReference formReference = FormReferenceProvider.getFormReference(field);
            if (formReference instanceof FieldFormReference) {
              FieldFormReference ref = (FieldFormReference) formReference;
              if (!ref.isCustomCreate()) {
                holder.registerProblem(expression, UIDesignerBundle.message("inspection.bound.field.message"),
                                       new LocalQuickFix[0]);
              }
            }
          }
        }
      }
    };
  }
}
