package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;

public class FieldElement extends RepositoryTreeElement{
   private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.FieldElement");

  public FieldElement() {
    super(FIELD);
  }

  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getTextOffset();
  }

  public void deleteChildInternal(TreeElement child) {
    if (getChildRole(child) == ChildRole.INITIALIZER){
      TreeElement eq = findChildByRole(ChildRole.INITIALIZER_EQ);
      if (eq != null){
        deleteChildInternal(eq);
      }
    }
    super.deleteChildInternal(child);
  }

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        if (firstChild.getElementType() == JavaTokenType.DOC_COMMENT){
          return firstChild;
        }
        else{
          return null;
        }

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, JavaElementType.MODIFIER_LIST);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, JavaElementType.TYPE);

      case ChildRole.NAME:
        return TreeUtil.findChild(this, JavaTokenType.IDENTIFIER);

      case ChildRole.INITIALIZER_EQ:
        return TreeUtil.findChild(this, JavaTokenType.EQ);

      case ChildRole.INITIALIZER:
        return TreeUtil.findChild(this, ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == JavaTokenType.C_STYLE_COMMENT || i == JavaTokenType.END_OF_LINE_COMMENT) {
      {
        if (TreeUtil.skipElementsBack(child, ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET) == null) {
          return ChildRole.PRECEDING_COMMENT;
        }
        else {
          return ChildRole.NONE;
        }
      }
    }
    else if (i == JavaElementType.MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == JavaElementType.TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaTokenType.EQ) {
      return getChildRole(child, ChildRole.INITIALIZER_EQ);
    }
    else if (i == JavaTokenType.SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.isInSet(child.getElementType())) {
        return ChildRole.INITIALIZER;
      }
      return ChildRole.NONE;
    }
  }
}
