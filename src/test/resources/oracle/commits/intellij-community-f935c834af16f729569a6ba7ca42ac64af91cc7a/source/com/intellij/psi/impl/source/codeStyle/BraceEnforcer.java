package com.intellij.psi.impl.source.codeStyle;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.jsp.JspFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class BraceEnforcer extends AbstractPostFormatProcessor {

  public BraceEnforcer(CodeStyleSettings settings) {
    super(settings);
  }

  @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitElement(expression);
  }

  @Override public void visitIfStatement(PsiIfStatement statement) {
    if (checkElementContainsRange(statement)) {
      final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(statement.getProject()).createSmartPsiElementPointer(statement);
      super.visitIfStatement(statement);
      statement = (PsiIfStatement)pointer.getElement();
      processStatement(statement, statement.getThenBranch(), mySettings.IF_BRACE_FORCE);
      final PsiStatement elseBranch = statement.getElseBranch();
      if (!(elseBranch instanceof PsiIfStatement) || !mySettings.SPECIAL_ELSE_IF_TREATMENT) {
        processStatement(statement, elseBranch, mySettings.IF_BRACE_FORCE);
      }
    }
  }

  @Override public void visitForStatement(PsiForStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitForStatement(statement);
      processStatement(statement, statement.getBody(), mySettings.FOR_BRACE_FORCE);
    }
  }

  @Override public void visitForeachStatement(PsiForeachStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitForeachStatement(statement);
      processStatement(statement, statement.getBody(), mySettings.FOR_BRACE_FORCE);
    }
  }

  @Override public void visitWhileStatement(PsiWhileStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitWhileStatement(statement);
      processStatement(statement, statement.getBody(), mySettings.WHILE_BRACE_FORCE);
    }
  }

  @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    if (checkElementContainsRange(statement)) {
      super.visitDoWhileStatement(statement);
      processStatement(statement, statement.getBody(), mySettings.DOWHILE_BRACE_FORCE);
    }
  }

  @Override public void visitJspFile(JspFile file) {
    final PsiClass javaRoot = file.getJavaClass();
    if (javaRoot != null) {
      javaRoot.accept(this);
    }
  }
  
  private void processStatement(PsiStatement statement, PsiStatement blockCandidate, int options) {
    if (blockCandidate instanceof PsiBlockStatement || blockCandidate == null) return;
    if (options == CodeStyleSettings.FORCE_BRACES_ALWAYS ||
        options == CodeStyleSettings.FORCE_BRACES_IF_MULTILINE && isMultiline(statement)) {
      replaceWithBlock(statement, blockCandidate);
    }
  }

  private void replaceWithBlock(PsiStatement statement, PsiStatement blockCandidate) {
    LOG.assertTrue(statement != null);
    if (!statement.isValid()) {
      LOG.assertTrue(false);
    }

    if (!checkRangeContainsElement(blockCandidate)) return;

    final PsiManager manager = statement.getManager();
    LOG.assertTrue(manager != null);
    final PsiElementFactory factory = manager.getElementFactory();
    
    String oldText = blockCandidate.getText();
    StringBuffer buf = new StringBuffer(oldText.length() + 3);
    buf.append('{');
    buf.append(oldText);
    buf.append("\n}");
    final int oldTextLength = statement.getTextLength();
    try {
      CodeEditUtil.replaceChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(statement),
                                SourceTreeToPsiMap.psiElementToTree(blockCandidate),
                                SourceTreeToPsiMap.psiElementToTree(factory.createStatementFromText(buf.toString(), null)));
      CodeStyleManager.getInstance(statement.getProject()).reformat(statement, true);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      updateResultRange(oldTextLength , statement.getTextLength());
    }
  }

}
