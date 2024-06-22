/*
 * Class ClassObjectEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.sun.jdi.ReferenceType;

public class ClassObjectEvaluator implements Evaluator {
  private TypeEvaluator myTypeEvaluator;

  public ClassObjectEvaluator(TypeEvaluator typeEvaluator) {
    myTypeEvaluator = typeEvaluator;
  }

  public Modifier getModifier() {
    return null;
  }

  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    Object object = myTypeEvaluator.evaluate(context);
    if (!(object instanceof ReferenceType)) {
      throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate: the type specified is not a reference type");
    }
    return ((ReferenceType)object).classObject();
  }
}
