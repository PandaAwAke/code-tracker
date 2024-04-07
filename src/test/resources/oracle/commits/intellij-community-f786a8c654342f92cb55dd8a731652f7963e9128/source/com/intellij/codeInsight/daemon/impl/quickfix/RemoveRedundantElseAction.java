package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class RemoveRedundantElseAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.RemoveRedundantElseAction");

  public String getText() {
    return QuickFixBundle.message("remove.redundant.else.fix");
  }

  public String getFamilyName() {
    return QuickFixBundle.message("remove.redundant.else.fix");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    if (elementAt instanceof PsiKeyword &&
        elementAt.getParent() instanceof PsiIfStatement &&
        PsiKeyword.ELSE.equals(elementAt.getText())) {
      PsiIfStatement ifStatement = (PsiIfStatement)elementAt.getParent();
      if (ifStatement.getElseBranch() == null) return false;
      if (ifStatement.getThenBranch() == null) return false;
      PsiElement block = PsiTreeUtil.getParentOfType(ifStatement, PsiCodeBlock.class);
      if (block != null) {
        try {
          ControlFlow controlFlow = new ControlFlowAnalyzer(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), true, false).buildControlFlow();
          int startOffset = controlFlow.getStartOffset(ifStatement.getThenBranch());
          int endOffset = controlFlow.getEndOffset(ifStatement.getThenBranch());
          return !ControlFlowUtil.canCompleteNormally(controlFlow, startOffset,endOffset);
        }
        catch (AnalysisCanceledException e) {
          return false;
        }
      }
    }
    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    PsiIfStatement ifStatement = (PsiIfStatement)elementAt.getParent();
    LOG.assertTrue(ifStatement != null && ifStatement.getElseBranch() != null);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch instanceof PsiBlockStatement) {
      PsiElement[] statements = ((PsiBlockStatement)elseBranch).getCodeBlock().getStatements();
      if (statements.length > 0) {
        ifStatement.getParent().addRangeAfter(statements[0], statements[statements.length-1], ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(elseBranch, ifStatement);
    }
    ifStatement.getElseBranch().delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
