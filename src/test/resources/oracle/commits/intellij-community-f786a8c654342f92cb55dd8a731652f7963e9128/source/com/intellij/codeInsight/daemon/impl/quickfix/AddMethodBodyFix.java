package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;

public class AddMethodBodyFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix");

  private final PsiMethod myMethod;

  public AddMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  public String getText() {
    return QuickFixBundle.message("add.method.body.text");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getBody() == null
        && myMethod.getContainingClass() != null
        && myMethod.getManager().isInProject(myMethod);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myMethod.getContainingFile())) return;

    try {
      myMethod.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);
      CreateFromUsageUtils.setupMethodBody(myMethod);
      CreateFromUsageUtils.setupEditor(myMethod, editor);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
