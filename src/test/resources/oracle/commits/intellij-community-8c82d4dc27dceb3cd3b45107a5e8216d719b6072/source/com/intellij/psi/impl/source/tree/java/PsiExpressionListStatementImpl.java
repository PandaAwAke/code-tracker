package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionListStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionListStatementImpl extends CompositePsiElement implements PsiExpressionListStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiExpressionListStatementImpl");

  public PsiExpressionListStatementImpl() {
    super(EXPRESSION_LIST_STATEMENT);
  }

  public PsiExpressionList getExpressionList() {
    return (PsiExpressionList)findChildByRoleAsPsiElement(ChildRole.EXPRESSION_LIST);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXPRESSION_LIST:
        return TreeUtil.findChild(this, EXPRESSION_LIST);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == EXPRESSION_LIST) {
      return ChildRole.EXPRESSION_LIST;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitExpressionListStatement(this);
  }

  public String toString() {
    return "PsiExpressionListStatement";
  }
}
