package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.util.IncorrectOperationException;

public class InsertSuperFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.InsertSuperFix");

  private final PsiMethod myConstructor;

  public InsertSuperFix(PsiMethod constructor) {
    myConstructor = constructor;
  }

  public String getText() {
    return QuickFixBundle.message("insert.super.constructor.call.text");
  }

  public String getFamilyName() {
    return QuickFixBundle.message("insert.super.constructor.call.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myConstructor.isValid()
        && myConstructor.getBody() != null
        && myConstructor.getBody().getLBrace() != null
        && myConstructor.getManager().isInProject(myConstructor)
    ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myConstructor.getContainingFile())) return;
    try {
      PsiStatement superCall = myConstructor.getManager().getElementFactory().createStatementFromText("super();",null);

      PsiCodeBlock body = myConstructor.getBody();
      PsiJavaToken lBrace = body.getLBrace();
      body.addAfter(superCall, lBrace);
      lBrace = (PsiJavaToken) new PsiMatcherImpl(body)
                .firstChild(PsiMatcherImpl.hasClass(PsiExpressionStatement.class))
                .firstChild(PsiMatcherImpl.hasClass(PsiMethodCallExpression.class))
                .firstChild(PsiMatcherImpl.hasClass(PsiExpressionList.class))
                .firstChild(PsiMatcherImpl.hasClass(PsiJavaToken.class))
                .dot(PsiMatcherImpl.hasText("("))
                .getElement();
      editor.getCaretModel().moveToOffset(lBrace.getTextOffset()+1);
      QuickFixAction.markDocumentForUndo(file);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
