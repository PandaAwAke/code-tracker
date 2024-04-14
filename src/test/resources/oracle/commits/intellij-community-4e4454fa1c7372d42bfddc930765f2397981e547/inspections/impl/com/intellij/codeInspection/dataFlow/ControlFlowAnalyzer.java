package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;

class ControlFlowAnalyzer extends PsiElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer");
  private static final int NOT_FOUND = -10;
  private ControlFlow myPass1Flow;
  private ControlFlow myCurrentFlow;
  private int myPassNumber;
  private HashSet<DfaVariableValue> myFields;
  private Stack<CatchDescriptor> myCatchStack;
  private PsiType myRuntimeException;
  private DfaValueFactory myFactory;
  private final InstructionFactory myInstructionFactory;
  private boolean myHonorRuntimeExceptions = true;

  public ControlFlowAnalyzer(final DfaValueFactory valueFactory, final InstructionFactory instructionFactory) {
    myFactory = valueFactory;
    myInstructionFactory = instructionFactory;
  }

  private static class CantAnalyzeException extends RuntimeException {
  }

  public void setHonorRuntimeExceptions(final boolean honorRuntimeExceptions) {
    myHonorRuntimeExceptions = honorRuntimeExceptions;
  }

  public ControlFlow buildControlFlow(PsiElement codeFragment) {
    if (codeFragment == null) return null;

    myRuntimeException = PsiType.getJavaLangRuntimeException(codeFragment.getManager(), codeFragment.getResolveScope());
    myFields = new HashSet<DfaVariableValue>();
    myCatchStack = new Stack<CatchDescriptor>();
    myPassNumber = 1;
    myPass1Flow = new ControlFlow(myFactory, myInstructionFactory);
    myCurrentFlow = myPass1Flow;

    try {
      codeFragment.accept(this);
    }
    catch (CantAnalyzeException e) {
      return null;
    }

    myPassNumber = 2;
    final ControlFlow pass2Flow = new ControlFlow(myFactory, myInstructionFactory);
    myCurrentFlow = pass2Flow;

    codeFragment.accept(this);

    pass2Flow.setFields(myFields.toArray(new DfaVariableValue[myFields.size()]));

    LOG.assertTrue(myPass1Flow.getInstructionCount() == pass2Flow.getInstructionCount());

    return pass2Flow;
  }

  private boolean myRecursionStopper = false;

  private void addInstruction(Instruction instruction) {
    ProgressManager.getInstance().checkCanceled();

    if (!myRecursionStopper) {
      myRecursionStopper = true;
      try {
         //Add extra conditional goto in order to handle possible runtime exceptions that could be caught by finally block.
        if (instruction instanceof BranchingInstruction || instruction instanceof AssignInstruction ||
            instruction instanceof MethodCallInstruction) {
          addConditionalRuntimeThrow();
        }
      }
      finally {
        myRecursionStopper = false;
      }
    }

    myCurrentFlow.addInstruction(instruction);
  }

  private int getEndOffset(PsiElement element) {
    return myPassNumber == 2 ? myPass1Flow.getEndOffset(element) : 0;
  }

  private int getStartOffset(PsiElement element) {
    return myPassNumber == 2 ? myPass1Flow.getStartOffset(element) : 0;
  }

  private void startElement(PsiElement element) {
    myCurrentFlow.startElement(element);
  }

  private void finishElement(PsiElement element) {
    myCurrentFlow.finishElement(element);
  }

  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    startElement(expression);

    try {
      PsiExpression lExpr = expression.getLExpression();
      PsiExpression rExpr = expression.getRExpression();

      if (rExpr == null) {
        pushUnknown();
        return;
      }

      lExpr.accept(this);

      IElementType op = expression.getOperationSign().getTokenType();
      PsiType type = expression.getType();
      boolean isBoolean = type == PsiType.BOOLEAN;
      if (op == JavaTokenType.EQ) {
        rExpr.accept(this);
        generateBoxingUnboxingInstructionFor(rExpr, type);
      }
      else if (op == JavaTokenType.ANDEQ) {
        if (isBoolean) {
          generateNonMaccartyExpression(true, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinop(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.OREQ) {
        if (isBoolean) {
          generateNonMaccartyExpression(false, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinop(lExpr, rExpr, type);
        }
      }
      else if (op == JavaTokenType.XOREQ) {
        if (isBoolean) {
          generateXorExpression(expression, lExpr, rExpr, type);
        }
        else {
          generateDefaultBinop(lExpr, rExpr, type);
        }
      }
      else {
        generateDefaultBinop(lExpr, rExpr, type);
      }

      addInstruction(myInstructionFactory.createAssignInstruction(rExpr));
    }
    finally {
      finishElement(expression);
    }
  }

  private void generateDefaultBinop(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
    addInstruction(myInstructionFactory.createBinopInstruction(null, null));
  }

  public void visitAssertStatement(PsiAssertStatement statement) {
    startElement(statement);
    final PsiExpression condition = statement.getAssertCondition();
    final PsiExpression description = statement.getAssertDescription();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(myInstructionFactory.createConditionalGotoInstruction(getEndOffset(statement), false, condition));
      if (description != null) {
        description.accept(this);
      }
      addInstruction(myInstructionFactory.createReturnInstruction());
    }
    finishElement(statement);
  }

  public void visitDeclarationStatement(PsiDeclarationStatement statement) {
    startElement(statement);

    PsiElement[] elements = statement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiClass) {
        element.accept(this);
      }
      else if (element instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)element;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          initializeVariable(variable, initializer);
        }
      }
    }

    finishElement(statement);
  }

  private void initializeVariable(PsiVariable variable, PsiExpression initializer) {
    DfaVariableValue dfaVariable = myFactory.getVarFactory().create(variable, false);
    addInstruction(myInstructionFactory.createPushInstruction(dfaVariable));
    initializer.accept(this);
    generateBoxingUnboxingInstructionFor(initializer, variable.getType());
    addInstruction(myInstructionFactory.createAssignInstruction(initializer));
    addInstruction(myInstructionFactory.createPopInstruction());
  }

  public void visitCodeBlock(PsiCodeBlock block) {
    startElement(block);

    PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      statement.accept(this);
    }

    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
        PsiElement[] declarations = declarationStatement.getDeclaredElements();
        for (PsiElement declaration : declarations) {
          if (declaration instanceof PsiVariable) {
            myCurrentFlow.removeVariable((PsiVariable)declaration);
          }
        }
      }
    }

    finishElement(block);
  }

  public void visitBlockStatement(PsiBlockStatement statement) {
    startElement(statement);
    statement.getCodeBlock().accept(this);
    finishElement(statement);
  }

  public void visitBreakStatement(PsiBreakStatement statement) {
    startElement(statement);

    PsiStatement exitedStatement = statement.findExitedStatement();

    if (exitedStatement != null) {
      int offset = myPass1Flow.getEndOffset(exitedStatement);
      addInstruction(myInstructionFactory.createGotoInstruction(offset));
    }

    finishElement(statement);
  }

  public void visitContinueStatement(PsiContinueStatement statement) {
    startElement(statement);
    PsiStatement continuedStatement = statement.findContinuedStatement();
    if (continuedStatement != null) {
      int offset = -1;
      if (continuedStatement instanceof PsiForStatement) {
        PsiStatement body = ((PsiForStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      else if (continuedStatement instanceof PsiWhileStatement) {
        PsiStatement body = ((PsiWhileStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      else if (continuedStatement instanceof PsiDoWhileStatement) {
        PsiStatement body = ((PsiDoWhileStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      else if (continuedStatement instanceof PsiForeachStatement) {
        PsiStatement body = ((PsiForeachStatement)continuedStatement).getBody();
        offset = myPass1Flow.getEndOffset(body);
      }
      Instruction instruction = offset != -1
                                ? new GotoInstruction(offset)
                                : new EmptyInstruction();
      addInstruction(instruction);
    }
    finishElement(statement);
  }

  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    startElement(statement);

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
      PsiExpression condition = statement.getCondition();
      if (condition != null) {
        condition.accept(this);
        generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
        addInstruction(myInstructionFactory.createConditionalGotoInstruction(getStartOffset(statement), false, condition));
      }
    }

    finishElement(statement);
  }

  public void visitEmptyStatement(PsiEmptyStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  public void visitExpressionStatement(PsiExpressionStatement statement) {
    startElement(statement);
    final PsiExpression expr = statement.getExpression();
    expr.accept(this);
    addInstruction(myInstructionFactory.createPopInstruction());
    finishElement(statement);
  }

  public void visitExpressionListStatement(PsiExpressionListStatement statement) {
    startElement(statement);
    PsiExpression[] expressions = statement.getExpressionList().getExpressions();
    for (PsiExpression expr : expressions) {
      expr.accept(this);
      addInstruction(myInstructionFactory.createPopInstruction());
    }
    finishElement(statement);
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    startElement(statement);
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression iteratedValue = statement.getIteratedValue();

    if (iteratedValue != null) {
      iteratedValue.accept(this);
      addInstruction(myInstructionFactory.createFieldReferenceInstruction(iteratedValue, "Collection iterator or array.length"));
    }

    int offset = myCurrentFlow.getInstructionCount();
    DfaVariableValue dfaVariable = myFactory.getVarFactory().create(parameter, false);
    addInstruction(myInstructionFactory.createPushInstruction(dfaVariable));
    pushUnknown();
    addInstruction(myInstructionFactory.createAssignInstruction(null));

    addInstruction(myInstructionFactory.createPopInstruction());

    pushUnknown();
    addInstruction(myInstructionFactory.createConditionalGotoInstruction(getEndOffset(statement), true, null));

    final PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    addInstruction(myInstructionFactory.createGotoInstruction(offset));

    finishElement(statement);
    myCurrentFlow.removeVariable(parameter);
  }

  public void visitForStatement(PsiForStatement statement) {
    startElement(statement);
    final ArrayList<PsiElement> declaredVariables = new ArrayList<PsiElement>();

    PsiStatement initialization = statement.getInitialization();
    if (initialization != null) {
      initialization.accept(this);
      initialization.accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
          visitElement(expression);
        }

        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
          PsiElement[] declaredElements = statement.getDeclaredElements();
          for (PsiElement element : declaredElements) {
            if (element instanceof PsiVariable) {
              declaredVariables.add(element);
            }
          }
        }
      });
    }

    PsiExpression condition = statement.getCondition();
    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
    }
    else {
      addInstruction(myInstructionFactory.createPushInstruction(myFactory.getConstFactory().getTrue()));
    }
    addInstruction(myInstructionFactory.createConditionalGotoInstruction(getEndOffset(statement), true, condition));

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    PsiStatement update = statement.getUpdate();
    if (update != null) {
      update.accept(this);
    }

    int offset = initialization != null
                 ? getEndOffset(initialization)
                 : getStartOffset(statement);

    addInstruction(myInstructionFactory.createGotoInstruction(offset));
    finishElement(statement);

    for (PsiElement declaredVariable : declaredVariables) {
      PsiVariable psiVariable = (PsiVariable)declaredVariable;
      myCurrentFlow.removeVariable(psiVariable);
    }
  }

  public void visitIfStatement(PsiIfStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    PsiStatement thenStatement = statement.getThenBranch();
    PsiStatement elseStatement = statement.getElseBranch();

    int offset = elseStatement != null
                 ? getStartOffset(elseStatement)
                 : getEndOffset(statement);

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(myInstructionFactory.createConditionalGotoInstruction(offset, true, condition));
    }

    if (thenStatement != null) {
      thenStatement.accept(this);
    }

    if (elseStatement != null) {
      offset = getEndOffset(statement);
      Instruction instruction = new GotoInstruction(offset);
      addInstruction(instruction);
      elseStatement.accept(this);
    }

    finishElement(statement);
  }

  // in case of JspTemplateStatement
  public void visitStatement(PsiStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  public void visitLabeledStatement(PsiLabeledStatement statement) {
    startElement(statement);
    PsiStatement childStatement = statement.getStatement();
    if (childStatement != null) {
      childStatement.accept(this);
    }
    finishElement(statement);
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    startElement(statement);

    PsiExpression returnValue = statement.getReturnValue();
    if (returnValue != null) {
      returnValue.accept(this);
      PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiMember.class);
      if (method != null) {
        generateBoxingUnboxingInstructionFor(returnValue, method.getReturnType());
      }
      addInstruction(myInstructionFactory.createCheckReturnValueInstruction(statement));
    }

    int finallyOffset = getFinallyOffset();
    if (finallyOffset != NOT_FOUND) {
      addInstruction(myInstructionFactory.createGosubInstruction(finallyOffset));
    }
    addInstruction(myInstructionFactory.createReturnInstruction());
    finishElement(statement);
  }

  public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
    startElement(statement);
    finishElement(statement);
  }

  public void visitSwitchStatement(PsiSwitchStatement switchStmt) {
    startElement(switchStmt);
    PsiElementFactory psiFactory = switchStmt.getManager().getElementFactory();
    PsiExpression caseExpression = switchStmt.getExpression();

    if (caseExpression != null /*&& !(caseExpression instanceof PsiReferenceExpression)*/) {
      caseExpression.accept(this);

      generateBoxingUnboxingInstructionFor(caseExpression, PsiType.INT);
      if (TypeConversionUtil.isEnumType(caseExpression.getType())) {
        addInstruction(myInstructionFactory.createFieldReferenceInstruction(caseExpression, "switch statement expression"));
      }

      addInstruction(myInstructionFactory.createPopInstruction());
    }

    PsiCodeBlock body = switchStmt.getBody();

    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      PsiSwitchLabelStatement defaultLabel = null;
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiSwitchLabelStatement) {
          PsiSwitchLabelStatement psiLabelStatement = (PsiSwitchLabelStatement)statement;
          if (psiLabelStatement.isDefaultCase()) {
            defaultLabel = psiLabelStatement;
          }
          else {
            try {
              int offset = getStartOffset(statement);
              PsiExpression caseValue = psiLabelStatement.getCaseValue();

              if (caseValue != null &&
                  caseExpression instanceof PsiReferenceExpression &&
                  ((PsiReferenceExpression)caseExpression).getQualifierExpression() == null &&
                  JavaPsiFacade.getInstance(body.getProject()).getConstantEvaluationHelper().computeConstantExpression(caseValue) != null) {
                PsiExpression psiComparison = psiFactory.createExpressionFromText(
                  caseExpression.getText() + "==" + caseValue.getText(), switchStmt);
                psiComparison.accept(this);
              }
              else {
                pushUnknown();
              }

              addInstruction(myInstructionFactory.createConditionalGotoInstruction(offset, false, statement));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      int offset = defaultLabel != null ? getStartOffset(defaultLabel) : getEndOffset(body);
      addInstruction(myInstructionFactory.createGotoInstruction(offset));

      body.accept(this);
    }

    finishElement(switchStmt);
  }

  public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    startElement(statement);

    PsiExpression lock = statement.getLockExpression();
    if (lock != null) {
      lock.accept(this);
      addInstruction(myInstructionFactory.createPopInstruction());
    }

    addInstruction(myInstructionFactory.createFlushVariableInstruction(null));

    PsiCodeBlock body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    finishElement(statement);
  }

  public void visitThrowStatement(PsiThrowStatement statement) {
    startElement(statement);

    PsiExpression exception = statement.getException();

    if (exception != null) {
      exception.accept(this);
      addThrowCode(exception.getType());
    }

    finishElement(statement);
  }

  private void addConditionalRuntimeThrow() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) {
        pushUnknown();
        final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch);
        addInstruction(myInstructionFactory.createGosubInstruction(cd.getJumpOffset()));
        addInstruction(myInstructionFactory.createReturnInstruction());
        branch.setOffset(myCurrentFlow.getInstructionCount());
      }
      else if (cd.getType() instanceof PsiClassType &&
               ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)cd.getType())) {
        pushUnknown();
        final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch);
        addInstruction(myInstructionFactory.createPushInstruction(myFactory.getNotNullFactory().create(myRuntimeException)));
        addGotoCatch(cd);
        branch.setOffset(myCurrentFlow.getInstructionCount());
        return;
      }
    }
  }

  private void addThrowCode(PsiType exceptionClass) {
    if (exceptionClass == null) return;
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) {
        addInstruction(myInstructionFactory.createGosubInstruction(cd.getJumpOffset()));
      }
      else if (cd.getType().isAssignableFrom(exceptionClass)) { // Definite catch.
        addGotoCatch(cd);
        return;
      }
      else if (cd.getType().isConvertibleFrom(exceptionClass)) { // Probable catch
        addInstruction(myInstructionFactory.createDupInstruction());
        pushUnknown();
        final ConditionalGotoInstruction branch = new ConditionalGotoInstruction(-1, false, null);
        addInstruction(branch);
        addGotoCatch(cd);
        branch.setOffset(myCurrentFlow.getInstructionCount());
      }
    }

    addInstruction(myInstructionFactory.createReturnInstruction());
  }

  /**
   * Exception is expected on the stack.
   * 
   * @param cd
   */
  private void addGotoCatch(CatchDescriptor cd) {
    addInstruction(myInstructionFactory.createPushInstruction(myFactory.getVarFactory().create(cd.getParameter(), false)));
    addInstruction(myInstructionFactory.createSwapInstruction());
    addInstruction(myInstructionFactory.createAssignInstruction(null));
    addInstruction(myInstructionFactory.createPopInstruction());
    addInstruction(myInstructionFactory.createGotoInstruction(cd.getJumpOffset()));
  }

  private int getFinallyOffset() {
    for (int i = myCatchStack.size() - 1; i >= 0; i--) {
      CatchDescriptor cd = myCatchStack.get(i);
      if (cd.isFinally()) return cd.getJumpOffset();
    }

    return NOT_FOUND;
  }

  class CatchDescriptor {
    private final PsiType myType;
    private PsiParameter myParameter;
    private final PsiCodeBlock myBlock;
    private final boolean myIsFinally;

    public CatchDescriptor(PsiCodeBlock finallyBlock) {
      myType = null;
      myBlock = finallyBlock;
      myIsFinally = true;
    }

    public CatchDescriptor(PsiParameter parameter, PsiCodeBlock catchBlock) {
      myType = parameter.getType();
      myParameter = parameter;
      myBlock = catchBlock;
      myIsFinally = false;
    }

    public PsiType getType() {
      return myType;
    }

    public boolean isFinally() {
      return myIsFinally;
    }

    public int getJumpOffset() {
      return getStartOffset(myBlock);
    }

    public PsiParameter getParameter() { return myParameter; }
  }

  public void visitErrorElement(PsiErrorElement element) {
    throw new CantAnalyzeException();
  }

  public void visitTryStatement(PsiTryStatement statement) {
    startElement(statement);
    PsiCodeBlock finallyBlock = statement.getFinallyBlock();

    if (finallyBlock != null) {
      myCatchStack.push(new CatchDescriptor(finallyBlock));
    }

    int catchesPushCount = 0;
    PsiCatchSection[] sections = statement.getCatchSections();
    for (int i = sections.length - 1; i >= 0; i--) {
      PsiCatchSection section = sections[i];
      PsiCodeBlock catchBlock = section.getCatchBlock();
      PsiParameter parameter = section.getParameter();
      if (parameter != null && catchBlock != null && parameter.getType() instanceof PsiClassType &&
          (!myHonorRuntimeExceptions || !ExceptionUtil.isUncheckedException((PsiClassType)parameter.getType()))) {
        myCatchStack.push(new CatchDescriptor(parameter, catchBlock));
        catchesPushCount++;
      }
      else {
        throw new CantAnalyzeException();
      }
    }

    int endOffset = finallyBlock == null ? getEndOffset(statement) : getStartOffset(finallyBlock) - 2;

    PsiCodeBlock tryBlock = statement.getTryBlock();

    if (tryBlock != null) {
      tryBlock.accept(this);
    }

    for (int i = 0; i < catchesPushCount; i++) myCatchStack.pop();

    addInstruction(myInstructionFactory.createGotoInstruction(endOffset));

    for (PsiCatchSection section : sections) {
      section.accept(this);
      addInstruction(myInstructionFactory.createGotoInstruction(endOffset));
    }

    if (finallyBlock != null) {
      myCatchStack.pop();
      addInstruction(myInstructionFactory.createGosubInstruction(getStartOffset(finallyBlock)));
      addInstruction(myInstructionFactory.createGotoInstruction(getEndOffset(statement)));
      finallyBlock.accept(this);
      addInstruction(myInstructionFactory.createReturnFromSubInstruction());
    }

    finishElement(statement);
  }

  public void visitCatchSection(PsiCatchSection section) {
    PsiCodeBlock catchBlock = section.getCatchBlock();
    if (catchBlock != null) catchBlock.accept(this);
  }

  public void visitWhileStatement(PsiWhileStatement statement) {
    startElement(statement);

    PsiExpression condition = statement.getCondition();

    if (condition != null) {
      condition.accept(this);
      generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
      addInstruction(myInstructionFactory.createConditionalGotoInstruction(getEndOffset(statement), true, condition));
    }

    PsiStatement body = statement.getBody();
    if (body != null) {
      body.accept(this);
    }

    if (condition != null) {
      addInstruction(myInstructionFactory.createGotoInstruction(getStartOffset(statement)));
    }

    finishElement(statement);
  }

  public void visitExpressionList(PsiExpressionList list) {
    startElement(list);

    PsiExpression[] expressions = list.getExpressions();
    for (PsiExpression expression : expressions) {
      expression.accept(this);
    }

    finishElement(list);
  }

  public void visitExpression(PsiExpression expression) {
    startElement(expression);
    DfaValue dfaValue = myFactory.create(expression);
    addInstruction(myInstructionFactory.createPushInstruction(dfaValue));
    finishElement(expression);
  }

  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    //TODO:::
    startElement(expression);
    PsiExpression arrayExpression = expression.getArrayExpression();
    arrayExpression.accept(this);
    addInstruction(myInstructionFactory.createFieldReferenceInstruction(expression, null));

    PsiExpression indexExpression = expression.getIndexExpression();
    if (indexExpression != null) {
      indexExpression.accept(this);
      generateBoxingUnboxingInstructionFor(indexExpression, PsiType.INT);
      addInstruction(myInstructionFactory.createPopInstruction());
    }

    pushTypeOrUnknown(arrayExpression);
    finishElement(expression);
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    //TODO:::
    startElement(expression);
    PsiType type = expression.getType();
    PsiExpression[] initializers = expression.getInitializers();
    for (PsiExpression initializer : initializers) {
      initializer.accept(this);
      if (type instanceof PsiArrayType) {
        generateBoxingUnboxingInstructionFor(initializer, ((PsiArrayType)type).getComponentType());
      }
      addInstruction(myInstructionFactory.createPopInstruction());
    }
    pushUnknown();
    finishElement(expression);
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    startElement(expression);

    try {
      DfaValue dfaValue = myFactory.create(expression);
      if (dfaValue != null) {
        addInstruction(myInstructionFactory.createPushInstruction(dfaValue));
      }
      else {
        IElementType op = expression.getOperationSign().getTokenType();
        PsiExpression lExpr = expression.getLOperand();
        PsiExpression rExpr = expression.getROperand();

        if (rExpr == null) {
          pushUnknown();
          return;
        }
        PsiType type = expression.getType();
        if (op == JavaTokenType.ANDAND) {
          generateAndExpression(lExpr, rExpr, type);
        }
        else if (op == JavaTokenType.OROR) {
          generateOrExpression(lExpr, rExpr, type);
        }
        else if (op == JavaTokenType.XOR && type == PsiType.BOOLEAN) {
          generateXorExpression(expression, lExpr, rExpr, type);
        }
        else {
          lExpr.accept(this);
          boolean comparing = (op == JavaTokenType.EQEQ || op == JavaTokenType.NE);
          PsiType lType = lExpr.getType();
          PsiType rType = rExpr.getType();

          boolean comparingRef = comparing
                                 && !TypeConversionUtil.isPrimitiveAndNotNull(lType)
                                 && !TypeConversionUtil.isPrimitiveAndNotNull(rType);

          boolean comparingPrimitiveNumerics = comparing &&
                                               TypeConversionUtil.isPrimitiveAndNotNull(lType) &&
                                               TypeConversionUtil.isPrimitiveAndNotNull(rType) &&
                                               TypeConversionUtil.isNumericType(lType) &&
                                               TypeConversionUtil.isNumericType(rType);

          PsiType castType = comparingPrimitiveNumerics ? PsiType.LONG : type;

          if (!comparingRef) {
            generateBoxingUnboxingInstructionFor(lExpr,castType);
          }
          rExpr.accept(this);
          if (!comparingRef) {
            generateBoxingUnboxingInstructionFor(rExpr,castType);
          }

          String opSign = expression.getOperationSign().getText();
          if ("+".equals(opSign)) {
            if (type == null || !type.equalsToText("java.lang.String")) {
              opSign = null;
            }
          }
          
          addInstruction(myInstructionFactory.createBinopInstruction(opSign, expression.isPhysical() ? expression : null));
        }
      }
    }
    finally {
      finishElement(expression);
    }
  }

  private void generateBoxingUnboxingInstructionFor(PsiExpression expression, PsiType expectedType) {
    PsiType exprType = expression.getType();

    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(exprType)) {
      addInstruction(myInstructionFactory.createMethodCallInstruction(expression, myFactory, MethodCallInstruction.MethodType.UNBOXING));
    }
    else if (TypeConversionUtil.isPrimitiveWrapper(expectedType) && TypeConversionUtil.isPrimitiveAndNotNull(exprType)) {
      addInstruction(myInstructionFactory.createMethodCallInstruction(expression, myFactory, MethodCallInstruction.MethodType.BOXING));
    }
    else if (exprType != expectedType &&
             TypeConversionUtil.isPrimitiveAndNotNull(exprType) &&
             TypeConversionUtil.isPrimitiveAndNotNull(expectedType) &&
             TypeConversionUtil.isNumericType(exprType) &&
             TypeConversionUtil.isNumericType(expectedType)) {
      addInstruction(myInstructionFactory.createCastInstruction(expression, myFactory, expectedType));
    }
  }

  private void generateXorExpression(PsiExpression expression, PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
    addInstruction(myInstructionFactory.createBinopInstruction("!=", expression.isPhysical() ? expression : null));
  }

  private void generateOrExpression(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr,exprType);
    addInstruction(myInstructionFactory.createConditionalGotoInstruction(getStartOffset(rExpr), true, lExpr));
    addInstruction(myInstructionFactory.createPushInstruction(myFactory.getConstFactory().getTrue()));
    addInstruction(myInstructionFactory.createGotoInstruction(getEndOffset(rExpr)));
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr,exprType);
  }

  private void generateNonMaccartyExpression(boolean and, PsiExpression lExpression, PsiExpression rExpression, final PsiType exprType) {
    rExpression.accept(this);
    generateBoxingUnboxingInstructionFor(rExpression, exprType);

    lExpression.accept(this);
    generateBoxingUnboxingInstructionFor(lExpression, exprType);

    ConditionalGotoInstruction toPopAndPushSuccess = new ConditionalGotoInstruction(-1, and, lExpression);
    addInstruction(toPopAndPushSuccess);
    final GotoInstruction overPushSuccess = new GotoInstruction(-1);
    addInstruction(overPushSuccess);

    final PopInstruction pop = new PopInstruction();
    addInstruction(pop);
    final PushInstruction pushSuccess = new PushInstruction(and
                                                            ? myFactory.getConstFactory().getFalse()
                                                            : myFactory.getConstFactory().getTrue());
    addInstruction(pushSuccess);

    toPopAndPushSuccess.setOffset(pop.getIndex());
    overPushSuccess.setOffset(pushSuccess.getIndex() + 1);
  }

  private void generateAndExpression(PsiExpression lExpr, PsiExpression rExpr, final PsiType exprType) {
    lExpr.accept(this);
    generateBoxingUnboxingInstructionFor(lExpr, exprType);
    ConditionalGotoInstruction firstTrueGoto = new ConditionalGotoInstruction(-1, true, lExpr);
    addInstruction(firstTrueGoto);
    rExpr.accept(this);
    generateBoxingUnboxingInstructionFor(rExpr, exprType);

    GotoInstruction overPushFalse = new GotoInstruction(-1);
    addInstruction(overPushFalse);
    PushInstruction pushFalse = new PushInstruction(myFactory.getConstFactory().getFalse());
    addInstruction(pushFalse);

    firstTrueGoto.setOffset(pushFalse.getIndex());
    overPushFalse.setOffset(pushFalse.getIndex() + 1);
  }

  public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    startElement(expression);
    PsiElement[] children = expression.getChildren();
    for (PsiElement child : children) {
      child.accept(this);
    }
    pushUnknown();
    finishElement(expression);
  }

  public void visitConditionalExpression(PsiConditionalExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    if (dfaValue != null) {
      addInstruction(myInstructionFactory.createPushInstruction(dfaValue));
    }
    else {
      PsiExpression condition = expression.getCondition();

      PsiExpression thenExpression = expression.getThenExpression();
      PsiExpression elseExpression = expression.getElseExpression();

      if (thenExpression != null && elseExpression != null) {
        condition.accept(this);
        generateBoxingUnboxingInstructionFor(condition, PsiType.BOOLEAN);
        PsiType type = expression.getType();
        addInstruction(myInstructionFactory.createConditionalGotoInstruction(getStartOffset(elseExpression), true, condition));
        thenExpression.accept(this);
        generateBoxingUnboxingInstructionFor(thenExpression,type);

        addInstruction(myInstructionFactory.createGotoInstruction(getEndOffset(expression)));
        elseExpression.accept(this);
        generateBoxingUnboxingInstructionFor(elseExpression,type);
      }
      else {
        pushUnknown();
      }
    }

    finishElement(expression);
  }

  private void pushUnknown() {
    addInstruction(myInstructionFactory.createPushInstruction(DfaUnknownValue.getInstance()));
  }

  public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
    startElement(expression);
    PsiExpression operand = expression.getOperand();
    PsiTypeElement checkType = expression.getCheckType();
    if (checkType != null) {
      operand.accept(this);
      PsiType type = checkType.getType();
      if (type instanceof PsiClassType) {
        type = ((PsiClassType)type).rawType();
      }
      addInstruction(myInstructionFactory.createPushInstruction(myFactory.getTypeFactory().create(type)));
      addInstruction(myInstructionFactory.createBinopInstruction("instanceof", expression));
    }
    else {
      pushUnknown();
    }

    finishElement(expression);
  }

  private void addMethodThrows(PsiMethod method) {
    if (method != null) {
      PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
      for (PsiClassType ref : refs) {
        pushUnknown();
        ConditionalGotoInstruction cond = new ConditionalGotoInstruction(NOT_FOUND, false, null);
        addInstruction(cond);
        addInstruction(myInstructionFactory.createEmptyStackInstruction());
        addInstruction(myInstructionFactory.createPushInstruction(myFactory.getTypeFactory().create(ref)));
        addThrowCode(ref);
        cond.setOffset(myCurrentFlow.getInstructionCount());
      }
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    try {
      startElement(expression);

      if (processSpecialMethods(expression)) {
        return;
      }

      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

      if (qualifierExpression != null) {
        qualifierExpression.accept(this);
      }
      else {
        pushUnknown();
      }

      PsiExpression[] paramExprs = expression.getArgumentList().getExpressions();
      PsiElement method = methodExpression.resolve();
      PsiParameter[] parameters = method instanceof PsiMethod ? ((PsiMethod)method).getParameterList().getParameters() : null;
      for (int i = 0; i < paramExprs.length; i++) {
        PsiExpression paramExpr = paramExprs[i];
        paramExpr.accept(this);
        if (parameters != null && i < parameters.length) {
          generateBoxingUnboxingInstructionFor(paramExpr, parameters[i].getType());
        }
      }

      addInstruction(myInstructionFactory.createMethodCallInstruction(expression, myFactory));

      if (!myCatchStack.isEmpty()) {
        addMethodThrows(expression.resolveMethod());
      }
    }
    finally {
      finishElement(expression);
    }
  }

  private boolean processSpecialMethods(PsiMethodCallExpression expression) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();

    PsiMethod resolved = expression.resolveMethod();
    if (resolved != null) {
      final PsiExpressionList argList = expression.getArgumentList();
      @NonNls String methodName = resolved.getName();

      PsiExpression[] params = argList.getExpressions();
      PsiClass owner = resolved.getContainingClass();
      final int exitPoint = getEndOffset(expression) - 1;
      if (owner != null) {
        final String className = owner.getQualifiedName();
        if ("java.lang.System".equals(className)) {
          if ("exit".equals(methodName)) {
            pushParameters(params, false);
            addInstruction(myInstructionFactory.createReturnInstruction());
            return true;
          }
        }
        else if ("junit.framework.Assert".equals(className)) {
          if ("fail".equals(methodName)) {
            pushParameters(params, false);
            addInstruction(myInstructionFactory.createReturnInstruction());
            return true;
          }
          else if ("assertTrue".equals(methodName)) {
            pushParameters(params, true);
            conditionalExit(exitPoint, false);
            return true;
          }
          else if ("assertFalse".equals(methodName)) {
            pushParameters(params, true);
            conditionalExit(exitPoint, true);
            return true;
          }
          else if ("assertNull".equals(methodName)) {
            pushParameters(params, true);

            addInstruction(myInstructionFactory.createPushInstruction(myFactory.getConstFactory().getNull()));
            addInstruction(myInstructionFactory.createBinopInstruction("==", null));
            conditionalExit(exitPoint, false);
            return true;
          }
          else if ("assertNotNull".equals(methodName)) {
            pushParameters(params, true);

            addInstruction(myInstructionFactory.createPushInstruction(myFactory.getConstFactory().getNull()));
            addInstruction(myInstructionFactory.createBinopInstruction("==", null));
            conditionalExit(exitPoint, true);
            return true;
          }
          return false;
        }
      }

      // Idea project only.
      if (qualifierExpression != null) {
        if (qualifierExpression.textMatches("LOG")) {
          final PsiType qualifierType = qualifierExpression.getType();
          if (qualifierType != null && qualifierType.equalsToText("com.intellij.openapi.diagnostic.Logger")) {
            if ("error".equals(methodName)) {
              for (PsiExpression param : params) {
                param.accept(this);
                addInstruction(myInstructionFactory.createPopInstruction());
              }
              addInstruction(myInstructionFactory.createReturnInstruction());
              return true;
            }
            else if ("assertTrue".equals(methodName)) {
              params[0].accept(this);
              for (int i = 1; i < params.length; i++) {
                params[i].accept(this);
                addInstruction(myInstructionFactory.createPopInstruction());
              }
              conditionalExit(exitPoint, false);
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  private void conditionalExit(final int exitPoint, final boolean negated) {
    addInstruction(myInstructionFactory.createConditionalGotoInstruction(exitPoint, negated, null));
    addInstruction(myInstructionFactory.createReturnInstruction());
    pushUnknown();
  }

  private void pushParameters(final PsiExpression[] params, final boolean leaveLastOnStack) {
    for (int i = 0; i < params.length; i++) {
      PsiExpression param = params[i];
      param.accept(this);
      if (!leaveLastOnStack || i < params.length - 1) {
        addInstruction(myInstructionFactory.createPopInstruction());
      }
    }
  }

  private void pushTypeOrUnknown(PsiExpression expr) {
    PsiType type = expr.getType();

    final DfaValue dfaValue;
    if (type instanceof PsiClassType) {
      dfaValue = myFactory.getTypeFactory().create(type);
    }
    else {
      dfaValue = null;
    }

    addInstruction(myInstructionFactory.createPushInstruction(dfaValue));
  }

  public void visitNewExpression(PsiNewExpression expression) {
    startElement(expression);

    pushUnknown();

    if (expression.getType() instanceof PsiArrayType) {
      final PsiExpression[] dimensions = expression.getArrayDimensions();
      for (final PsiExpression dimension : dimensions) {
        dimension.accept(this);
      }
      for (PsiExpression dimension : dimensions) {
        addInstruction(myInstructionFactory.createPopInstruction());
      }
      final PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
      if (arrayInitializer != null) {
        for (final PsiExpression initializer : arrayInitializer.getInitializers()) {
          initializer.accept(this);
          addInstruction(myInstructionFactory.createPopInstruction());
        }
      }
      addInstruction(myInstructionFactory.createMethodCallInstruction(expression, myFactory));
    }
    else {
      final PsiExpressionList args = expression.getArgumentList();
      PsiMethod ctr = expression.resolveConstructor();
      if (args != null) {
        PsiExpression[] params = args.getExpressions();
        PsiParameter[] parameters = ctr == null ? null : ctr.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
          PsiExpression param = params[i];
          param.accept(this);
          if (parameters != null && i < parameters.length) {
            generateBoxingUnboxingInstructionFor(param, parameters[i].getType());
          }
        }
      }

      addInstruction(myInstructionFactory.createMethodCallInstruction(expression, myFactory));

      if (!myCatchStack.isEmpty()) {
        addMethodThrows(ctr);
      }
    }

    finishElement(expression);
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
    startElement(expression);
    PsiExpression inner = expression.getExpression();
    if (inner != null) {
      inner.accept(this);
    }
    else {
      pushUnknown();
    }
    finishElement(expression);
  }

  public void visitPostfixExpression(PsiPostfixExpression expression) {
    startElement(expression);

    PsiExpression operand = expression.getOperand();
    operand.accept(this);
    generateBoxingUnboxingInstructionFor(operand, PsiType.INT);

    addInstruction(myInstructionFactory.createPopInstruction());
    pushUnknown();

    if (operand instanceof PsiReferenceExpression) {
      PsiVariable psiVariable = DfaValueFactory.resolveVariable((PsiReferenceExpression)expression.getOperand());
      if (psiVariable != null) {
        DfaVariableValue dfaVariable = myFactory.getVarFactory().create(psiVariable, false);
        addInstruction(myInstructionFactory.createFlushVariableInstruction(dfaVariable));
      }
    }

    finishElement(expression);
  }

  public void visitPrefixExpression(PsiPrefixExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    if (dfaValue == null) {
      PsiExpression operand = expression.getOperand();

      if (operand == null) {
        pushUnknown();
      }
      else {
        operand.accept(this);
        PsiType type = expression.getType();
        PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
        generateBoxingUnboxingInstructionFor(operand, unboxed == null ? type : unboxed);
        if (expression.getOperationSign().getTokenType() == JavaTokenType.EXCL) {
          addInstruction(myInstructionFactory.createNotInstruction());
        }
        else {
          addInstruction(myInstructionFactory.createPopInstruction());
          pushUnknown();

          if (operand instanceof PsiReferenceExpression) {
            PsiVariable psiVariable = DfaValueFactory.resolveVariable((PsiReferenceExpression)operand);
            if (psiVariable != null) {
              DfaVariableValue dfaVariable = myFactory.getVarFactory().create(psiVariable, false);
              addInstruction(myInstructionFactory.createFlushVariableInstruction(dfaVariable));
            }
          }
        }
      }
    }
    else {
      addInstruction(myInstructionFactory.createPushInstruction(dfaValue));
    }

    finishElement(expression);
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    if (dfaValue instanceof DfaVariableValue) {
      DfaVariableValue dfaVariable = (DfaVariableValue)dfaValue;
      PsiVariable psiVariable = dfaVariable.getPsiVariable();
      if (psiVariable instanceof PsiField &&
          !psiVariable.getModifierList().hasModifierProperty(PsiModifier.FINAL))
      {
        addField(dfaVariable);
      }
    }

    final PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      qualifierExpression.accept(this);
      if (expression.resolve() instanceof PsiField) {
        addInstruction(myInstructionFactory.createFieldReferenceInstruction(expression, null));
      }
      else {
        addInstruction(myInstructionFactory.createPopInstruction());
      }
    }

    addInstruction(myInstructionFactory.createPushInstruction(dfaValue));

    finishElement(expression);
  }

  private void addField(DfaVariableValue field) {
    myFields.add(field);
  }

  public void visitSuperExpression(PsiSuperExpression expression) {
    startElement(expression);
    addInstruction(myInstructionFactory.createPushInstruction(myFactory.getNotNullFactory().create(expression.getType())));
    finishElement(expression);
  }

  public void visitThisExpression(PsiThisExpression expression) {
    startElement(expression);
    addInstruction(myInstructionFactory.createPushInstruction(myFactory.getNotNullFactory().create(expression.getType())));
    finishElement(expression);
  }

  public void visitLiteralExpression(PsiLiteralExpression expression) {
    startElement(expression);

    DfaValue dfaValue = myFactory.create(expression);
    addInstruction(myInstructionFactory.createPushInstruction(dfaValue));

    finishElement(expression);
  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    startElement(expression);
    PsiExpression operand = expression.getOperand();

    if (operand != null) {
      operand.accept(this);
      generateBoxingUnboxingInstructionFor(operand, expression.getType());
    }
    else {
      pushTypeOrUnknown(expression);
    }

    TypeCastInstruction tcInstruction = createInstruction(expression);
    if (tcInstruction != null) {
      addInstruction(tcInstruction);
    }

    finishElement(expression);
  }

  public void visitClass(PsiClass aClass) {
  }

  @Nullable
  private TypeCastInstruction createInstruction(PsiTypeCastExpression castExpression) {
    PsiExpression expr = castExpression.getOperand();
    PsiType castType = castExpression.getCastType().getType();

    if (expr == null) return null;

    if (RedundantCastUtil.isTypeCastSemantical(castExpression)) {
      return new TypeCastInstruction();
    }

    DfaValue dfaExpr = myFactory.create(expr);
    DfaTypeValue dfaType = myFactory.getTypeFactory().create(castType);
    if (dfaExpr == null) return null;

    DfaRelationValue dfaInstanceof = myFactory.getRelationFactory().create(dfaExpr, dfaType, "instanceof", false);
    return dfaInstanceof != null ? myInstructionFactory.createTypeCastInstruction(castExpression, dfaInstanceof) : null;
  }


}