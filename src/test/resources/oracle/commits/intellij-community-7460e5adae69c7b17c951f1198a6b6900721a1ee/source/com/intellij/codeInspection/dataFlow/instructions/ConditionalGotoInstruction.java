/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:29 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;


import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;

public class ConditionalGotoInstruction extends BranchingInstruction {
  private int myOffset;
  private final boolean myIsNegated;

  public ConditionalGotoInstruction(int myOffset, boolean isNegated, PsiElement psiAnchor) {
    this.myOffset = myOffset;
    myIsNegated = isNegated;
    setPsiAnchor(psiAnchor);
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue cond = memState.pop();

    DfaValue condTrue;
    DfaValue condFalse;

    if (myIsNegated) {
      condFalse = cond;
      condTrue = cond.createNegated();
    } else {
      condTrue = cond;
      condFalse = cond.createNegated();
    }

    if (condTrue == DfaConstValue.Factory.getInstance().getTrue()) {
      markBranchReachable(true);
      return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getOffset()), memState)};
    }

    if (condFalse == DfaConstValue.Factory.getInstance().getTrue()) {
      markBranchReachable(false);
      return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
    }

    ArrayList result = new ArrayList();

    DfaMemoryState thenState = memState.createCopy();
    DfaMemoryState elseState = memState.createCopy();

    if (thenState.applyCondition(condTrue)) {
      result.add(new DfaInstructionState(runner.getInstruction(getOffset()), thenState));
      markBranchReachable(true);
    }

    if (elseState.applyCondition(condFalse)) {
      result.add(new DfaInstructionState(runner.getInstruction(getIndex() + 1), elseState));
      markBranchReachable(false);
    }

    return (DfaInstructionState[]) result.toArray(new DfaInstructionState[result.size()]);
  }

  private void markBranchReachable(boolean isTrueBranch) {
    if (isTrueBranch ^ myIsNegated) {
      setTrueReachable();
    }
    else {
      setFalseReachable();
    }
  }

  public String toString() {
    return "cond_goto " + myOffset;
  }

  public int getOffset() {
    return myOffset;
  }

  public boolean isTrueReachable() {
    return myIsTrueReachable;
  }

  public boolean isFalseReachable() {
    return myIsFalseReachable;
  }

  public void setOffset(int offset) {
    myOffset = offset;
  }
}
