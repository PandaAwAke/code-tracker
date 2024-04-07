/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 20, 2002
 */
package com.intellij.psi.controlFlow;

public abstract class BranchingInstruction extends InstructionBase {
  public int offset;

  public BranchingInstruction(int offset) {
    this.offset = offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitBranchingInstruction(this, offset, nextOffset);
  }
}
