package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class PsiTypeCastExpressionImpl extends CompositePsiElement implements PsiTypeCastExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTypeCastExpressionImpl");

  public PsiTypeCastExpressionImpl() {
    super(TYPE_CAST_EXPRESSION);
  }

  public PsiTypeElement getCastType() {
    return (PsiTypeElement)findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  public PsiType getType() {
    return getCastType().getType();
  }

  public TreeElement findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LPARENTH:
        return TreeUtil.findChild(this, LPARENTH);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, TYPE);

      case ChildRole.RPARENTH:
        return TreeUtil.findChild(this, RPARENTH);

      case ChildRole.OPERAND:
        return TreeUtil.findChild(this, EXPRESSION_BIT_SET);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (i == TYPE) {
      return ChildRole.TYPE;
    }
    else {
      if (EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.OPERAND;
      }
      return ChildRole.NONE;
    }
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitTypeCastExpression(this);
  }

  public String toString() {
    return "PsiTypeCastExpression:" + getText();
  }
}

