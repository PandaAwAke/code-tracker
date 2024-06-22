package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

public class VariableTypeFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix");

  private final PsiVariable myVariable;
  private final PsiType myReturnType;

  public VariableTypeFix(PsiVariable variable, PsiType toReturn) {
    myVariable = variable;
    myReturnType = toReturn != null ? GenericsUtil.getVariableTypeByExpressionType(toReturn) : null;
  }

  public String getText() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  myVariable.getName(),
                                  myReturnType.getCanonicalText());
  }

  public String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myVariable != null
        && myVariable.isValid()
        && myVariable.getManager().isInProject(myVariable)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && !TypeConversionUtil.isVoidType(myReturnType);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myVariable.getContainingFile())) return;
    try {
      myVariable.normalizeDeclaration();
      myVariable.getTypeElement().replace(file.getManager().getElementFactory().createTypeElement(myReturnType));
      CodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
      QuickFixAction.markDocumentForUndo(file);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
