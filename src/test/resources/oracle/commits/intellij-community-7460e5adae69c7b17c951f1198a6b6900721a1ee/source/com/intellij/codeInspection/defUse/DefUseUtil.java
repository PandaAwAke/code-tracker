/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Mar 22, 2002
 * Time: 7:25:02 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.defUse;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.IntArrayList;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DefUseUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defUse.DefUseUtil");

  public static class Info {
    private final PsiVariable myVariable;
    private final PsiElement myContext;
    private final boolean myIsRead;

    public Info(PsiVariable variable, PsiElement context, boolean read) {
      myVariable = variable;
      myContext = context;
      myIsRead = read;
    }

    public PsiVariable getVariable() {
      return myVariable;
    }

    public PsiElement getContext() {
      return myContext;
    }

    public boolean isRead() {
      return myIsRead;
    }
  }

  private static class InstructionState {
    private Set<PsiVariable> myVariablesUseArmed;
    private final int myInstructionIdx;
    private final IntArrayList myBackwardTraces;

    public InstructionState(int instructionIdx) {
      myInstructionIdx = instructionIdx;
      myBackwardTraces = new IntArrayList();
      myVariablesUseArmed = null;
    }

    public void addBackwardTrace(int i) {
      myBackwardTraces.add(i);
    }

    public IntArrayList getBackwardTraces() {
      return myBackwardTraces;
    }

    public int getInstructionIdx() {
      return myInstructionIdx;
    }

    void mergeUseArmed(PsiVariable psiVariable) {
      touch();
      myVariablesUseArmed.add(psiVariable);
    }

    boolean mergeUseDisarmed(PsiVariable psiVariable) {
      touch();

      boolean result = myVariablesUseArmed.contains(psiVariable);
      myVariablesUseArmed.remove(psiVariable);

      return result;
    }

    void touch() {
      if (myVariablesUseArmed == null) myVariablesUseArmed = new THashSet<PsiVariable>();
    }

    public boolean equals(Object obj) {
      InstructionState state = (InstructionState) obj;
      if (myVariablesUseArmed == null && state.myVariablesUseArmed == null) return true;
      if (myVariablesUseArmed == null || state.myVariablesUseArmed == null) return false;

      return myVariablesUseArmed.equals(state.myVariablesUseArmed);
    }

    public void merge(InstructionState state) {
      touch();
      myVariablesUseArmed.addAll(state.myVariablesUseArmed);
    }
  }

  public static List<Info> getUnusedDefs(PsiCodeBlock body, Set<PsiVariable> outUsedVariables) {
    if (body != null) {
      List<Info> unusedDefs = new ArrayList<Info>();
      IntArrayList exitPoints = new IntArrayList();

      ControlFlow flow;
      try {
        flow = new ControlFlowAnalyzer(body, ourPolicy).buildControlFlow();
      }
      catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
        return null;
      }
      Instruction[] instructions = flow.getInstructions();
      if (LOG.isDebugEnabled()) {
        System.out.println(flow);
      }

      Set<PsiVariable> assignedVariables = new THashSet<PsiVariable>();
      Set<PsiVariable> readVariables = new THashSet<PsiVariable>();
      for (int i = 0; i < instructions.length; i++) {
        Instruction instruction = instructions[i];
        ProgressManager.getInstance().checkCanceled();
        if (instruction instanceof WriteVariableInstruction) {
          WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
          PsiElement context = flow.getElement(i);
          context = PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
          PsiVariable psiVariable = writeInstruction.variable;
          if (context != null && !(context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null)) {
            assignedVariables.add(psiVariable);
          }
        } else if (instruction instanceof ReadVariableInstruction) {
          ReadVariableInstruction readInstruction = (ReadVariableInstruction) instruction;
          readVariables.add(readInstruction.variable);
        }
      }

      InstructionState[] states = getStates(instructions);

      boolean[] defsArmed = new boolean[instructions.length];
      for (int i = 0; i < defsArmed.length; i++) defsArmed[i] = false;

      List<InstructionState> queue = new ArrayList<InstructionState>();

      InstructionState startupState = states[instructions.length];
      startupState.touch();

      for (Iterator<PsiVariable> iterator = assignedVariables.iterator(); iterator.hasNext();) {
        PsiVariable psiVariable = iterator.next();
        if (psiVariable instanceof PsiField) {
          startupState.mergeUseArmed(psiVariable);
        }
      }

      ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize() - 1, exitPoints, new ArrayList<PsiStatement>(),
                                                  ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);

      if (exitPoints.isEmpty()) return null;
      for (int i = 0; i < exitPoints.size(); i++) {
        startupState.addBackwardTrace(exitPoints.get(i));
      }

      queue.add(startupState);

      while (!queue.isEmpty()) {
        ProgressManager.getInstance().checkCanceled();
        InstructionState state = queue.remove(0);

        int idx = state.getInstructionIdx();
        if (idx < instructions.length) {
          Instruction instruction = instructions[idx];

          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction writeInstruction = (WriteVariableInstruction) instruction;
            PsiVariable psiVariable = writeInstruction.variable;
            outUsedVariables.add(psiVariable);
            if (state.mergeUseDisarmed(psiVariable)) {
              defsArmed[idx] = true;
            }
          } else if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction readInstruction = (ReadVariableInstruction)instruction;
            state.mergeUseArmed(readInstruction.variable);
            outUsedVariables.add(readInstruction.variable);
          } else {
            state.touch();
          }
        }

        for (int i = 0; i < state.getBackwardTraces().size(); i++) {
          int prevIdx = state.getBackwardTraces().get(i);
          if (!state.equals(states[prevIdx])) {
            states[prevIdx].merge(state);
            queue.add(states[prevIdx]);
          }
        }
      }

      for (int i = 0; i < instructions.length; i++) {
        Instruction instruction = instructions[i];
        if (instruction instanceof WriteVariableInstruction) {
          WriteVariableInstruction writeInstruction = (WriteVariableInstruction)instruction;
          if (!defsArmed[i]) {
            PsiElement context = flow.getElement(i);
            context = PsiTreeUtil.getParentOfType(context, new Class[] {PsiStatement.class, PsiAssignmentExpression.class, PsiPostfixExpression.class, PsiPrefixExpression.class}, false);
            PsiVariable psiVariable = writeInstruction.variable;
            if (context != null && !(context instanceof PsiTryStatement)) {
              if (context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null) {
                if (!assignedVariables.contains(psiVariable)) {
                  unusedDefs.add(new Info(psiVariable, context, false));
                }
              } else {
                unusedDefs.add(new Info(psiVariable, context, readVariables.contains(psiVariable)));
              }
            }
          }
        }
      }

      return unusedDefs;
    }

    return null;
  }

  public static PsiElement[] getDefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref) {
    try {
      return new RefsDefs(body) {

        final InstructionState[] states = getStates(instructions);

        protected int nNext(int index) {
          return states[index].getBackwardTraces().size();
        }

        protected int getNext(int index, int no) {
          return states[index].getBackwardTraces().get(no);
        }

        protected boolean defs() { return true; }

        protected void processInstruction(final Set<PsiElement> res, final Instruction instruction, int index) {
          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
            if (instructionW.variable == def) {

              final PsiElement element = flow.getElement(index);
              element.accept(new PsiRecursiveElementVisitor() {
                public void visitReferenceExpression(PsiReferenceExpression ref) {
                  if (PsiUtil.isAccessedForWriting(ref)) {
                    if (ref.resolve() == def)
                      res.add(ref);
                  }
                }
                public void visitVariable(PsiVariable var) {
                  if (var == def && (var instanceof PsiParameter || var.hasInitializer()))
                    res.add(var.getNameIdentifier());
                }
              });
            }
          }
        }
      }.get(def, ref);
    }
    catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
      return null;
    }
  }

  public static PsiElement[] getRefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref) {
    try {
      return new RefsDefs(body) {

        protected int nNext(int index) {
          return instructions[index].nNext();
        }

        protected int getNext(int index, int no) {
          return instructions[index].getNext(index, no);
        }

        protected boolean defs() { return false; }

        protected void processInstruction(final Set<PsiElement> res, final Instruction instruction, int index) {
          if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction instructionR = (ReadVariableInstruction)instruction;
            if (instructionR.variable == def) {

              final PsiElement element = flow.getElement(index);
              element.accept(new PsiRecursiveElementVisitor() {
                public void visitReferenceExpression(PsiReferenceExpression ref) {
                  if (ref.resolve() == def)
                    res.add(ref);
                }
              });
            }
          }
        }
      }.get(def, ref);
    }
    catch (ControlFlowAnalyzer.AnalysisCanceledException e) {
      return null;
    }
  }

  protected static abstract class RefsDefs {

    protected abstract int   nNext(int index);
    protected abstract int getNext(int index, int no);

    final Instruction[] instructions;
    final ControlFlow flow;
    final PsiCodeBlock body;


    protected RefsDefs(PsiCodeBlock body) throws ControlFlowAnalyzer.AnalysisCanceledException {
      this.body = body;
      flow = new ControlFlowAnalyzer(body, ourPolicy, false, false, true).buildControlFlow();
      instructions = flow.getInstructions();
    }

    protected abstract void processInstruction(Set<PsiElement> res, final Instruction instruction, int index);
    protected abstract boolean defs ();

    public PsiElement [] get (final PsiVariable def, PsiElement ref) {
      if (body != null) {

        if (LOG.isDebugEnabled()) {
          for (int i = 0; i < instructions.length; i++) {
            Instruction instruction = instructions[i];
            System.out.println("" + i + ": " + instruction);
          }
        }

        {
          final boolean [] visited = new boolean[instructions.length + 1];
          final boolean [] parmsVisited = new boolean [1];
          visited [visited.length-1] = true; // stop on the code end
          int elem = flow.getStartOffset(ref);

          // hack: ControlFlow doesn't contains parameters initialization
          if (elem == -1 && def instanceof PsiParameter)
            elem = 0;

          if (elem != -1) {
            if (!defs () && instructions [elem] instanceof ReadVariableInstruction) {
              LOG.assertTrue(nNext(elem) == 1);
              LOG.assertTrue(getNext(elem,0) == elem+1);
              elem += 1;
            }

            final Set<PsiElement> res = new THashSet<PsiElement>();
            class Inner {

              void traverse (int index) {
                visited [index] = true;

                if (defs ()) {
                  final Instruction instruction = instructions [index];
                  processInstruction(res, instruction, index);
                  if (instruction instanceof WriteVariableInstruction) {
                    WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
                    if (instructionW.variable == def) {
                      return;
                    }
                  }

                  // hack: ControlFlow doesnn't contains parameters initialization
                  if (index == 0 && !parmsVisited [0]) {
                    parmsVisited [0] = true;
                    if (def instanceof PsiParameter)
                      res.add (def.getNameIdentifier());
                  }
                }

                final int nNext = nNext (index);
                for (int i = 0; i < nNext; i++) {
                  final int prev = getNext(index, i);
                  if (!visited [prev]) {
                    if (!defs ()) {
                      final Instruction instruction = instructions [prev];
                      if (instruction instanceof WriteVariableInstruction) {
                        WriteVariableInstruction instructionW = (WriteVariableInstruction)instruction;
                        if (instructionW.variable == def) {
                          continue;
                        }
                      } else {
                        processInstruction(res, instruction, prev);
                      }
                    }
                    traverse (prev);

                  }
                }
              }
            }
            new Inner ().traverse (elem);
            return res.toArray(new PsiElement[res.size ()]);
          }
        }
      }
      return null;
    }
  }


    private static InstructionState[] getStates(final Instruction[] instructions) {
    final InstructionState[] states = new InstructionState[instructions.length + 1];
    for (int i = 0; i < states.length; i++) {
      states[i] = new InstructionState(i);
    }

    for (int i = 0; i < instructions.length; i++) {
      final Instruction instruction = instructions[i];
      for (int j = 0; j != instruction.nNext(); ++ j) {
        states [instruction.getNext(i, j)].addBackwardTrace(i);
      }
    }
    return states;
  }

  private static final ControlFlowPolicy ourPolicy = new ControlFlowPolicy() {
    public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
      if (refExpr.isQualified()) return null;

      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        return (PsiVariable) refElement;
      }

      return null;
    }

    public boolean isParameterAccepted(PsiParameter psiParameter) {
      return true;
    }

    public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
      return true;
    }
  };

  public static PsiElement[] getDefsRefs (final boolean defs, PsiFile file, final PsiElement target) {
    if (!(target instanceof PsiIdentifier)) {
      return null;
    }

    if (file instanceof PsiCompiledElement)
      file = (PsiFile)((PsiCompiledElement)file).getMirror();

    if (file instanceof PsiJavaFile) {
      final PsiElement def;
      final PsiElement refElem = target.getParent ();
      {
        if (refElem instanceof PsiReference) {
          def = ((PsiReference)refElem).resolve();
        } else {
          def = refElem;
        }
      }

      if (def instanceof PsiLocalVariable || def instanceof PsiParameter) {
        final PsiVariable var = (PsiVariable) def;
        final PsiMethod method;
        {
          PsiElement p = var;
          while (!(p instanceof PsiMethod)) {
            final PsiElement parent = p.getParent();
            LOG.assertTrue (parent != null);
            p = parent;
          }
          method = (PsiMethod)p;
        }
        final PsiCodeBlock body = method.getBody();
        final PsiElement[] elems =
          (defs
           ? DefUseUtil.getDefs(body, var, refElem)
           : DefUseUtil.getRefs(body, var, refElem)
          );
        return elems;
      }
    }
    return null;
  }

}
