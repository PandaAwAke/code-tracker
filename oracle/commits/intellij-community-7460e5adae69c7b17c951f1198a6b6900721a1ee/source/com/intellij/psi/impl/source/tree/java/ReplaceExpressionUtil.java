package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;

public class ReplaceExpressionUtil implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil");

  public static boolean isNeedParenthesis(TreeElement oldExpr, TreeElement newExpr) {
    if (!ElementType.EXPRESSION_BIT_SET.isInSet(oldExpr.getTreeParent().getElementType())) return false;
    int priority = getExpressionPriority(newExpr);
    int parentPriority = getExpressionPriority(oldExpr.getTreeParent());
    if (priority > parentPriority) return false;
    IElementType i = oldExpr.getTreeParent().getElementType();
    if (i == ASSIGNMENT_EXPRESSION) {
      if (priority < parentPriority) return true;
      return oldExpr.getTreeParent().getChildRole(oldExpr) == ChildRole.LOPERAND ? true : false;
    }
    else if (i == CONDITIONAL_EXPRESSION) {
      int role = oldExpr.getTreeParent().getChildRole(oldExpr);
      if (role == ChildRole.THEN_EXPRESSION) return false;
      if (priority < parentPriority) return true;
      return role == ChildRole.ELSE_EXPRESSION ? false : true;
    }
    else if (i == BINARY_EXPRESSION) {
      if (priority < parentPriority) return true;
      return oldExpr.getTreeParent().getChildRole(oldExpr) == ChildRole.LOPERAND ? false : true;
    }
    else if (i == INSTANCE_OF_EXPRESSION) {
      return priority < parentPriority;
    }
    else if (i == PREFIX_EXPRESSION || i == TYPE_CAST_EXPRESSION) {
      return priority < parentPriority;
    }
    else if (i == POSTFIX_EXPRESSION) {
      return priority <= parentPriority;
    }
    else if (i == REFERENCE_EXPRESSION) {
      return priority < parentPriority;
    }
    else if (i == METHOD_CALL_EXPRESSION) {
      return false;
    }
    else if (i == NEW_EXPRESSION) {
      return false;
    }
    else if (i == ARRAY_ACCESS_EXPRESSION) {
      int role = oldExpr.getTreeParent().getChildRole(oldExpr);
      if (role == ChildRole.ARRAY_DIMENSION) return false;
      return priority < parentPriority;
    }
    else if (i == ARRAY_INITIALIZER_EXPRESSION) {
      return false;
    }
    else if (i == PARENTH_EXPRESSION) {
      return false;
    }
    else if (i == LITERAL_EXPRESSION || i == THIS_EXPRESSION || i == SUPER_EXPRESSION || i == CLASS_OBJECT_ACCESS_EXPRESSION) {
      return false;
    }

    LOG.assertTrue(false);
    return false;
  }

  private static int getExpressionPriority(TreeElement expr) {
    IElementType i = expr.getElementType();
    if (i == ASSIGNMENT_EXPRESSION) {
      return 0;
    }
    else if (i == CONDITIONAL_EXPRESSION) {
      return 1;
    }
    else if (i == BINARY_EXPRESSION) {
      {
        IElementType opType = ((PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(expr)).getOperationSign().getTokenType();
        if (opType == OROR) {
          return 2;
        }
        else if (opType == ANDAND) {
          return 3;
        }
        else if (opType == OR) {
          return 4;
        }
        else if (opType == XOR) {
          return 5;
        }
        else if (opType == AND) {
          return 6;
        }
        else if (opType == EQEQ || opType == NE) {
          return 7;
        }
        else if (opType == LT || opType == GT || opType == LE || opType == GE) {
          return 8;
        }
        else if (opType == LTLT || opType == GTGT || opType == GTGTGT) {
          return 9;
        }
        else if (opType == PLUS || opType == MINUS) {
          return 10;
        }
        else if (opType == ASTERISK || opType == DIV || opType == PERC) {
          return 11;
        }
      }


      return 8;
    }
    else if (i == INSTANCE_OF_EXPRESSION) {
      return 8;
    }
    else if (i == PREFIX_EXPRESSION || i == TYPE_CAST_EXPRESSION) {
      return 12;
    }
    else if (i == POSTFIX_EXPRESSION) {
      return 13;
    }
    else if (i == LITERAL_EXPRESSION || i == REFERENCE_EXPRESSION || i == THIS_EXPRESSION || i == SUPER_EXPRESSION || i ==
                                                                                                                      PARENTH_EXPRESSION ||
             i == METHOD_CALL_EXPRESSION ||
             i == CLASS_OBJECT_ACCESS_EXPRESSION ||
             i == NEW_EXPRESSION ||
             i == ARRAY_ACCESS_EXPRESSION ||
             i == ARRAY_INITIALIZER_EXPRESSION ||
             i == JAVA_CODE_REFERENCE ||
             i == EMPTY_EXPRESSION) {
      return 14;
    }
    else {
      LOG.assertTrue(false, "Unknown element type:"+i);
      return -1;
    }
  }
}