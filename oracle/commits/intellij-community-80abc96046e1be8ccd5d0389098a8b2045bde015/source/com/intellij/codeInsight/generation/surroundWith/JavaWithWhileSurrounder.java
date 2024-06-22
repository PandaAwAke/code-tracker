
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class JavaWithWhileSurrounder extends JavaStatementsSurrounder{
  public String getTemplateDescription() {
    return "while";
  }

  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0){
      return null;
    }

    String text = "while(true){\n}";
    PsiWhileStatement whileStatement = (PsiWhileStatement)factory.createStatementFromText(text, null);
    whileStatement = (PsiWhileStatement)codeStyleManager.reformat(whileStatement);

    whileStatement = (PsiWhileStatement)container.addAfter(whileStatement, statements[statements.length - 1]);

    PsiCodeBlock bodyBlock = ((PsiBlockStatement)whileStatement.getBody()).getCodeBlock();
    bodyBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    return whileStatement.getCondition().getTextRange();
  }
}