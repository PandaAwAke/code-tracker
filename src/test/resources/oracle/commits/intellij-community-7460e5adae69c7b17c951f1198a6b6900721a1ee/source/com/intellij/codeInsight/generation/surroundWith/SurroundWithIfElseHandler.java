
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class SurroundWithIfElseHandler implements SurroundStatementsHandler{
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0){
      return null;
    }

    String text = "if(a){\n}else{\n}";
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(text, null);
    ifStatement = (PsiIfStatement)codeStyleManager.reformat(ifStatement);

    ifStatement = (PsiIfStatement)container.addAfter(ifStatement, statements[statements.length - 1]);

    PsiCodeBlock thenBlock = ((PsiBlockStatement)ifStatement.getThenBranch()).getCodeBlock();
    thenBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    TextRange range = ifStatement.getCondition().getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }
}