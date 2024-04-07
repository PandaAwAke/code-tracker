/*
 * Class BinaryExpressionEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.sun.jdi.*;

class BinaryExpressionEvaluator implements Evaluator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.BinaryExpressionEvaluator");
  private Evaluator myLeftOperand;
  private Evaluator myRightOperand;
  private IElementType myOpType;
  private String myExpectedType; // a result of PsiType.getCanonicalText()

  public BinaryExpressionEvaluator(Evaluator leftOperand, Evaluator rightOperand, IElementType opType, String expectedType) {
    myLeftOperand = leftOperand;
    myRightOperand = rightOperand;
    myOpType = opType;
    myExpectedType = expectedType;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Value leftResult = (Value)myLeftOperand.evaluate(context);
    return evaluateOperation(leftResult, myOpType, myRightOperand, myExpectedType, context);

  }

  static Object evaluateOperation(Value leftResult,
                                          IElementType opType,
                                          Evaluator rightOperand,
                                          String expectedType,
                                          EvaluationContextImpl context) throws EvaluateException {
    VirtualMachineProxyImpl vm = context.getDebugProcess().getVirtualMachineProxy();
    if (leftResult instanceof BooleanValue) {
      boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
      if (opType == JavaTokenType.OROR && v1) {
        return DebuggerUtilsEx.createValue(vm, expectedType, true);
      }
      if (opType == JavaTokenType.ANDAND && !v1) {
        return DebuggerUtilsEx.createValue(vm, expectedType, false);
      }
    }
    Value rightResult = (Value)rightOperand.evaluate(context);
    if (opType == JavaTokenType.PLUS) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 + v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 + v2);
      }
      if (leftResult instanceof StringReference || rightResult instanceof StringReference) {
        String v1 = DebuggerUtilsEx.getValueAsString(context, leftResult);
        String v2 = DebuggerUtilsEx.getValueAsString(context, rightResult);
        return vm.mirrorOf(v1 + v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "+"));
    }
    else if (opType == JavaTokenType.MINUS) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 - v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 - v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "-"));
    }
    else if (opType == JavaTokenType.ASTERISK) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 * v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 * v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "*"));
    }
    else if (opType == JavaTokenType.DIV) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 / v2);
      }
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 / v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 / v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "/"));
    }
    else if (opType == JavaTokenType.PERC) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 % v2);
      }
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 % v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 % v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "%"));
    }
    else if (opType == JavaTokenType.LTLT) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 << v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 << v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "<<"));
    }
    else if (opType == JavaTokenType.GTGT) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >> v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >> v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">>"));
    }
    else if (opType == JavaTokenType.GTGTGT) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >>> v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >>> v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">>>"));
    }
    else if (opType == JavaTokenType.AND) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 & v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 & v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 & v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "&"));
    }
    else if (opType == JavaTokenType.OR) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 | v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 | v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 | v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "|"));
    }
    else if (opType == JavaTokenType.XOR) {
      if (DebuggerUtilsEx.isInteger(leftResult) && DebuggerUtilsEx.isInteger(rightResult)) {
        long v1 = ((PrimitiveValue)leftResult).longValue();
        long v2 = ((PrimitiveValue)rightResult).longValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 ^ v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 ^ v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 ^ v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "^"));
    }
    else if (opType == JavaTokenType.EQEQ) {
      if (leftResult == null && rightResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, true);
      if (leftResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, rightResult.equals(leftResult));
      if (rightResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, leftResult.equals(rightResult));
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 == v2);
      }
      if (leftResult instanceof ObjectReference && rightResult instanceof ObjectReference) {
        ObjectReference v1 = (ObjectReference)leftResult;
        ObjectReference v2 = (ObjectReference)rightResult;
        return DebuggerUtilsEx.createValue(vm, expectedType, v1.uniqueID() == v2.uniqueID());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "=="));
    }
    else if (opType == JavaTokenType.OROR) {
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 || v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "||"));
    }
    else if (opType == JavaTokenType.ANDAND) {
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 && v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "&&"));
    }
    else if (opType == JavaTokenType.NE) {
      if (leftResult == null && rightResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, false);
      if (leftResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, !rightResult.equals(leftResult));
      if (rightResult == null) return DebuggerUtilsEx.createValue(vm, expectedType, !leftResult.equals(rightResult));
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (leftResult instanceof BooleanValue && rightResult instanceof BooleanValue) {
        boolean v1 = ((PrimitiveValue)leftResult).booleanValue();
        boolean v2 = ((PrimitiveValue)rightResult).booleanValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 != v2);
      }
      if (leftResult instanceof ObjectReference && rightResult instanceof ObjectReference) {
        ObjectReference v1 = (ObjectReference)leftResult;
        ObjectReference v2 = (ObjectReference)rightResult;
        return DebuggerUtilsEx.createValue(vm, expectedType, v1.uniqueID() != v2.uniqueID());
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "!="));
    }
    else if (opType == JavaTokenType.LT) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 < v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 < v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "<"));
    }
    else if (opType == JavaTokenType.GT) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 > v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 > v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">"));
    }
    else if (opType == JavaTokenType.LE) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 <= v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 <= v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", "<="));
    }
    else if (opType == JavaTokenType.GE) {
      if (DebuggerUtilsEx.isNumeric(leftResult) && DebuggerUtilsEx.isNumeric(rightResult)) {
        double v1 = ((PrimitiveValue)leftResult).doubleValue();
        double v2 = ((PrimitiveValue)rightResult).doubleValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >= v2);
      }
      if (leftResult instanceof CharValue && rightResult instanceof CharValue) {
        char v1 = ((CharValue)leftResult).charValue();
        char v2 = ((CharValue)rightResult).charValue();
        return DebuggerUtilsEx.createValue(vm, expectedType, v1 >= v2);
      }
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.incompatible.types", ">="));
    }

    LOG.assertTrue(false);

    return null;
  }

  
}
