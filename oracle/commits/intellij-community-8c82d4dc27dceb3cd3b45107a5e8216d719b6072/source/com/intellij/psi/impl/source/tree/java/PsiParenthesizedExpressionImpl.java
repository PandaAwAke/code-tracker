package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class PsiParenthesizedExpressionImpl extends CompositePsiElement implements PsiParenthesizedExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiParenthesizedExpressionImpl");

  public PsiParenthesizedExpressionImpl() {
    super(PARENTH_EXPRESSION);
  }

  @Nullable
  public PsiExpression getExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.EXPRESSION);
  }

  public PsiType getType() {
    PsiExpression expr = getExpression();
    if (expr == null) return null;
    return expr.getType();
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.EXPRESSION:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);

    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitParenthesizedExpression(this);
  }

  public String toString() {
    return "PsiParenthesizedExpression:" + getText();
  }
}

