package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class PsiBlockStatementImpl extends CompositePsiElement implements PsiBlockStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBlockStatementImpl");

  public PsiBlockStatementImpl() {
    super(BLOCK_STATEMENT);
  }

  @NotNull
  public PsiCodeBlock getCodeBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.BLOCK);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.BLOCK:
        return TreeUtil.findChild(this, CODE_BLOCK);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == CODE_BLOCK) {
      return ChildRole.BLOCK;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitBlockStatement(this);
  }

  public String toString() {
    return "PsiBlockStatement";
  }
}
