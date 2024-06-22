/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 10:05:49 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;

public class ReturnInstruction extends Instruction {
  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    return new DfaInstructionState[0];
  }

  public String toString() {
    return "RETURN";
  }
}
