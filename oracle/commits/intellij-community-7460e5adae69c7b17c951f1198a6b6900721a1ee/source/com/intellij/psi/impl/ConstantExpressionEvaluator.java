package com.intellij.psi.impl;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import com.intellij.psi.util.ConstantExpressionUtil;
import gnu.trove.THashSet;

import java.util.Set;

public class ConstantExpressionEvaluator extends PsiElementVisitor {
  protected Set myVisitedVars;
  protected boolean myThrowExceptionOnOverflow;
  protected Object myValue;

  protected ConstantExpressionEvaluator(Set visitedVars, boolean throwExceptionOnOverflow) {
    myVisitedVars = visitedVars;
    myThrowExceptionOnOverflow = throwExceptionOnOverflow;
  }

  protected Object getValue() {
    return myValue;
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    myValue = expression.getValue();
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    Object lOperandValue = accept(expression.getLOperand());
    Object rOperandValue = accept(expression.getROperand());

    final PsiJavaToken operationSign = expression.getOperationSign();
    if (operationSign == null) {
      myValue = null;
      return;
    }

    final IElementType tokenType = operationSign.getTokenType();

    Object value = null;
    if (tokenType == JavaTokenType.LT) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() < ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.LE) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() <= ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.GT) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() > ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.GE) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() >= ((Number)rOperandValue).doubleValue());
      }
    }
    else if (tokenType == JavaTokenType.EQEQ) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() == ((Number)rOperandValue).doubleValue());
      }
      else if (lOperandValue instanceof String && rOperandValue instanceof String) {
        value = Boolean.valueOf(lOperandValue == rOperandValue);
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() != ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.NE) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        value = Boolean.valueOf(((Number)lOperandValue).doubleValue() != ((Number)rOperandValue).doubleValue());
      }
      else if (lOperandValue instanceof String && rOperandValue instanceof String) {
        value = Boolean.valueOf(lOperandValue != rOperandValue);
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() != ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.PLUS) {
      if (lOperandValue == null || rOperandValue == null) {
        myValue = null;
        return;
      }
      if (lOperandValue instanceof String || rOperandValue instanceof String) {
        value = (lOperandValue.toString() + rOperandValue.toString()).intern();
      }
      else {
        if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
        if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());

        if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
          if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
            value = new Double(((Number)lOperandValue).doubleValue() + ((Number)rOperandValue).doubleValue());
            checkRealNumberOverflow(value, lOperandValue, rOperandValue,expression);
          }
          else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
            value = new Float(((Number)lOperandValue).floatValue() + ((Number)rOperandValue).floatValue());
            checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
          }
          else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
            final long l = ((Number)lOperandValue).longValue();
            final long r = ((Number)rOperandValue).longValue();
            value = new Long(l + r);
            checkAdditionOverflow(((Long)value).longValue() >= 0, l >= 0, r >= 0, expression);
          }
          else {
            final int l = ((Number)lOperandValue).intValue();
            final int r = ((Number)rOperandValue).intValue();
            value = new Integer(l + r);
            checkAdditionOverflow(((Integer)value).intValue() >= 0, l >= 0, r >= 0, expression);
          }
        }
      }
    }
    else if (tokenType == JavaTokenType.MINUS) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = new Double(((Number)lOperandValue).doubleValue() - ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() - ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long l = ((Number)lOperandValue).longValue();
          final long r = ((Number)rOperandValue).longValue();
          value = new Long(l - r);
          checkAdditionOverflow(((Long)value).longValue() >= 0, l >= 0, r < 0, expression);
        }
        else {
          final int l = ((Number)lOperandValue).intValue();
          final int r = ((Number)rOperandValue).intValue();
          value = new Integer(l - r);
          checkAdditionOverflow(((Integer)value).intValue() >= 0, l >= 0, r < 0, expression);
        }
      }
    }
    else if (tokenType == JavaTokenType.ASTERISK) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = new Double(((Number)lOperandValue).doubleValue() * ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() * ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long l = ((Number)lOperandValue).longValue();
          final long r = ((Number)rOperandValue).longValue();
          value = new Long(l * r);
          checkMultiplicationOverflow(((Long)value).longValue(), l, r, expression);
        }
        else {
          final int l = ((Number)lOperandValue).intValue();
          final int r = ((Number)rOperandValue).intValue();
          value = new Integer(l * r);
          checkMultiplicationOverflow(((Integer)value).intValue(), l, r, expression);
        }
      }
    }
    else if (tokenType == JavaTokenType.DIV) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = new Double(((Number)lOperandValue).doubleValue() / ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() / ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          final long r = ((Number)rOperandValue).longValue();
          final long l = ((Number)lOperandValue).longValue();
          checkDivisionOverflow(l, r, Long.MIN_VALUE, expression);
          value = r == 0 ? null : new Long(l / r);
        }
        else {
          final int r = ((Number)rOperandValue).intValue();
          final int l = ((Number)lOperandValue).intValue();
          checkDivisionOverflow(l, r, Integer.MIN_VALUE, expression);
          value = r == 0 ? null : new Integer(l / r);
        }
      }
    }
    else if (tokenType == JavaTokenType.PERC) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (lOperandValue instanceof Number && rOperandValue instanceof Number) {
        double rVal = ((Number)rOperandValue).doubleValue();
        if (myThrowExceptionOnOverflow && rVal == 0) throw new ConstantEvaluationOverflowException(expression);
        if (lOperandValue instanceof Double || rOperandValue instanceof Double) {
          value = new Double(((Number)lOperandValue).doubleValue() % ((Number)rOperandValue).doubleValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Float || rOperandValue instanceof Float) {
          value = new Float(((Number)lOperandValue).floatValue() % ((Number)rOperandValue).floatValue());
          checkRealNumberOverflow(value, lOperandValue, rOperandValue, expression);
        }
        else if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() % ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() % ((Number)rOperandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.LTLT) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() << ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() << ((Number)rOperandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.GTGT) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() >> ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() >> ((Number)rOperandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.GTGTGT) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() >>> ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() >>> ((Number)rOperandValue).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.AND) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() & ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() & ((Number)rOperandValue).intValue());
        }
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() & ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.OR) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() | ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() | ((Number)rOperandValue).intValue());
        }
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() | ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.XOR) {
      if (lOperandValue instanceof Character) lOperandValue = new Integer(((Character)lOperandValue).charValue());
      if (rOperandValue instanceof Character) rOperandValue = new Integer(((Character)rOperandValue).charValue());
      if (isIntegral(lOperandValue) && isIntegral(rOperandValue)) {
        if (lOperandValue instanceof Long || rOperandValue instanceof Long) {
          value = new Long(((Number)lOperandValue).longValue() ^ ((Number)rOperandValue).longValue());
        }
        else {
          value = new Integer(((Number)lOperandValue).intValue() ^ ((Number)rOperandValue).intValue());
        }
      }
      else if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() ^ ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.ANDAND) {
      if (lOperandValue == null || rOperandValue == null) {
        myValue = null;
        return;
      }
      if (lOperandValue instanceof Boolean && !((Boolean)lOperandValue).booleanValue()) {
        myValue = Boolean.FALSE;
        return;
      }
      if (rOperandValue instanceof Boolean && !((Boolean)rOperandValue).booleanValue()) {
        myValue = Boolean.FALSE;
        return;
      }
      if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() && ((Boolean)rOperandValue).booleanValue());
      }
    }
    else if (tokenType == JavaTokenType.OROR) {
      if (lOperandValue == null || rOperandValue == null) {
        myValue = null;
        return;
      }
      if (lOperandValue instanceof Boolean && ((Boolean)lOperandValue).booleanValue()) {
        myValue = Boolean.TRUE;
        return;
      }
      if (rOperandValue instanceof Boolean && ((Boolean)rOperandValue).booleanValue()) {
        myValue = Boolean.TRUE;
        return;
      }
      if (lOperandValue instanceof Boolean && rOperandValue instanceof Boolean) {
        value = Boolean.valueOf(((Boolean)lOperandValue).booleanValue() || ((Boolean)rOperandValue).booleanValue());
      }
    }

    myValue = value;
  }

  protected Object accept(PsiElement element) {
    myValue = null;
    if (element != null) {
      element.accept(this);
    }

    return myValue;
  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    Object operand;
    PsiType castType = expression.getCastType().getType();
    if(expression.getOperand() == null) {
      myValue = null;
      return;
    }

    operand = accept(expression.getOperand());

    myValue = ConstantExpressionUtil.computeCastTo(operand, castType);
  }

  public void visitConditionalExpression(PsiConditionalExpression expression) {
    if (expression.getCondition() == null || expression.getThenExpression() == null || expression.getElseExpression() == null) {
      myValue = null;
      return;
    }

    Object condition = accept(expression.getCondition());
    Object thenExpr = accept(expression.getThenExpression());
    Object elseExpr = accept(expression.getElseExpression());

    Object value = null;
    if (condition instanceof Boolean && thenExpr != null && elseExpr != null) {
      value = ((Boolean) condition).booleanValue() ? thenExpr : elseExpr;
    }

    myValue = value;
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    Object operand;
    if (expression.getOperand() == null) {
      myValue = null;
      return;
    }
    operand = accept(expression.getOperand());
    if (operand == null || expression.getOperationSign() == null) {
      myValue = null;
      return;
    }
    IElementType tokenType = expression.getOperationSign().getTokenType();
    Object value = null;
    if (tokenType == JavaTokenType.MINUS) {
      if (operand instanceof Character) operand = new Integer(((Character)operand).charValue());
      if (operand instanceof Number) {
        if (operand instanceof Double) {
          value = new Double(-((Number)operand).doubleValue());
          checkRealNumberOverflow(value, null, null, expression);
        }
        else if (operand instanceof Float) {
          value = new Float(-((Number)operand).floatValue());
          checkRealNumberOverflow(value, null, null, expression);
        }
        else if (operand instanceof Long) {
          value = new Long(-((Number)operand).longValue());
          if (myThrowExceptionOnOverflow
              && !(expression.getOperand() instanceof PsiLiteralExpression)
              && ((Number)operand).longValue() == Long.MIN_VALUE) {
            throw new ConstantEvaluationOverflowException(expression);
          }
        }
        else {
          value = new Integer(-((Number)operand).intValue());
          if (myThrowExceptionOnOverflow
              && !(expression.getOperand() instanceof PsiLiteralExpression)
              && ((Number)operand).intValue() == Integer.MIN_VALUE) {
            throw new ConstantEvaluationOverflowException(expression);
          }
        }
      }
    }
    else if (tokenType == JavaTokenType.PLUS) {
      if (operand instanceof Character) operand = new Integer(((Character)operand).charValue());
      if (operand instanceof Number) {
        value = operand;
      }
    }
    else if (tokenType == JavaTokenType.TILDE) {
      if (operand instanceof Character) operand = new Integer(((Character)operand).charValue());
      if (isIntegral(operand)) {
        if (operand instanceof Long) {
          value = new Long(~((Number)operand).longValue());
        }
        else {
          value = new Integer(~((Number)operand).intValue());
        }
      }
    }
    else if (tokenType == JavaTokenType.EXCL) {
      if (operand instanceof Boolean) {
        value = Boolean.valueOf(!((Boolean)operand).booleanValue());
      }
    }

    myValue = value;
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    myValue = accept(expression.getExpression());
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    while (qualifierExpression != null) {
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        myValue = null;
        return;
      }

      PsiReferenceExpression qualifier = (PsiReferenceExpression) qualifierExpression;
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiPackage) break;
      if (!(resolved instanceof PsiClass)) {
        myValue = null;
        return;
      }
      qualifierExpression = ((PsiReferenceExpression) qualifierExpression).getQualifierExpression();
    }
    PsiElement resolvedExpression = expression.resolve();
    if (resolvedExpression instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable) resolvedExpression;
      // avoid cycles
      if (myVisitedVars != null && myVisitedVars.contains(variable)) {
        myValue = null;
        return;
      }

      Set oldVisitedVars = myVisitedVars;
      if (myVisitedVars == null) { myVisitedVars = new THashSet(); }

      myVisitedVars.add(variable);
      try {
        if (!(variable instanceof PsiVariableEx)) {
          myValue = null; //?
          return;
        }

        myValue = ((PsiVariableEx) variable).computeConstantValue(myVisitedVars);
        return;
      }
      finally {
        myVisitedVars.remove(variable);
        myVisitedVars = oldVisitedVars;
      }
    }

    myValue = null;
  }

  protected static boolean isIntegral(Object o) {
    return o instanceof Long || o instanceof Integer || o instanceof Short || o instanceof Byte || o instanceof Character;
  }

  protected void checkDivisionOverflow(long l, final long r, long minValue, PsiBinaryExpression expression) {
    if (!myThrowExceptionOnOverflow) return;
    if (r == 0) throw new ConstantEvaluationOverflowException(expression);
    if (r == -1 && l == minValue) throw new ConstantEvaluationOverflowException(expression);
  }

  protected void checkMultiplicationOverflow(long result, long l, long r, PsiExpression expression) {
    if (!myThrowExceptionOnOverflow) return;
    if (r == 0 || l == 0) return;
    if (result / r != l || ((l < 0) ^ (r < 0) != (result < 0))) throw new ConstantEvaluationOverflowException(expression);
  }

  protected void checkAdditionOverflow(boolean resultPositive,
                                       boolean lPositive,
                                       boolean rPositive, PsiBinaryExpression expression) {
    if (!myThrowExceptionOnOverflow) return;
    boolean overflow = lPositive == rPositive && lPositive != resultPositive;
    if (overflow) throw new ConstantEvaluationOverflowException(expression);
  }

  protected void checkRealNumberOverflow(Object result,
                                         Object lOperandValue,
                                         Object rOperandValue, PsiExpression expression) {
    if (!myThrowExceptionOnOverflow) return;
    if (lOperandValue instanceof Float && ((Float) lOperandValue).isInfinite()) return;
    if (lOperandValue instanceof Double && ((Double) lOperandValue).isInfinite()) return;
    if (rOperandValue instanceof Float && ((Float) rOperandValue).isInfinite()) return;
    if (rOperandValue instanceof Double && ((Double) rOperandValue).isInfinite()) return;

    if (result instanceof Float && ((Float) result).isInfinite()) throw new ConstantEvaluationOverflowException(expression);
    if (result instanceof Double && ((Double) result).isInfinite()) throw new ConstantEvaluationOverflowException(expression);
  }

  public static Object computeConstantExpression(PsiExpression expression, Set visitedVars, boolean throwExceptionOnOverflow) {
    ConstantExpressionEvaluator evaluator = new ConstantExpressionEvaluator(visitedVars, throwExceptionOnOverflow);
    return _compute(evaluator, expression);
  }

  protected static Object _compute(ConstantExpressionEvaluator evaluator, PsiExpression expression) {
    evaluator.accept(expression);
    return evaluator.getValue();
  }

}
