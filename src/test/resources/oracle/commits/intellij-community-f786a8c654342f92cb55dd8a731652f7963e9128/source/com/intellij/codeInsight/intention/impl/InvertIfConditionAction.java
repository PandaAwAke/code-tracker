/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 22, 2002
 * Time: 2:55:23 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class InvertIfConditionAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.InvertIfConditionAction");

  private static IElementType[] ourTokenMap = new IElementType[]{
    JavaTokenType.EQEQ, JavaTokenType.NE,
    JavaTokenType.LT, JavaTokenType.GE,
    JavaTokenType.LE, JavaTokenType.GT,
    JavaTokenType.OROR, JavaTokenType.ANDAND
  };

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) return false;
    final PsiExpression condition = ifStatement.getCondition();
    if (condition == null) return false;
    if (ifStatement.getThenBranch() == null) return false;
    if (element instanceof PsiKeyword) {
      PsiKeyword keyword = (PsiKeyword) element;
      if ((keyword.getTokenType() == JavaTokenType.IF_KEYWORD || keyword.getTokenType() == JavaTokenType.ELSE_KEYWORD)
          && keyword.getParent() == ifStatement) {
        return true;
      }
    }
    final TextRange condTextRange = condition.getTextRange();
    if (condTextRange == null) return false;
    if (!condTextRange.contains(offset)) return false;
    PsiElement block = findCodeBlock(ifStatement);
    if (block == null) return false;

    return true;
  }

  public String getText() {
    return getFamilyName();
  }

  public String getFamilyName() {
    return CodeInsightBundle.message("intention.invert.if.condition");
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(file.findElementAt(
            editor.getCaretModel().getOffset()), PsiIfStatement.class);

    LOG.assertTrue(ifStatement != null);
    PsiElement block = findCodeBlock(ifStatement);

    ControlFlow controlFlow = buildControlFlow(block);

    PsiExpression condition = (PsiExpression) ifStatement.getCondition().copy();

    setupBranches(ifStatement, controlFlow);
    if (condition != null) {
      ifStatement.getCondition().replace(invertCondition(condition));
    }

    formatIf(ifStatement);
  }

  private void formatIf(PsiIfStatement ifStatement) throws IncorrectOperationException {
    PsiManager manager = ifStatement.getManager();
    Project project = manager.getProject();
    PsiElementFactory factory = manager.getElementFactory();

    PsiElement thenBranch = ifStatement.getThenBranch().copy();
    PsiElement elseBranch = ifStatement.getElseBranch() != null ? ifStatement.getElseBranch().copy() : null;
    PsiElement condition = ifStatement.getCondition().copy();

    PsiBlockStatement codeBlock = (PsiBlockStatement) factory.createStatementFromText("{}", null);
    codeBlock = (PsiBlockStatement) CodeStyleManager.getInstance(project).reformat(codeBlock);

    ifStatement.getThenBranch().replace(codeBlock);
    if (elseBranch != null) {
      ifStatement.getElseBranch().replace(codeBlock);
    }
    ifStatement.getCondition().replace(factory.createExpressionFromText("true", null));
    ifStatement = (PsiIfStatement) CodeStyleManager.getInstance(project).reformat(ifStatement);

    if (!(thenBranch instanceof PsiBlockStatement)) {
      PsiBlockStatement codeBlock1 = (PsiBlockStatement) ifStatement.getThenBranch().replace(codeBlock);
      codeBlock1 = (PsiBlockStatement) CodeStyleManager.getInstance(project).reformat(codeBlock1);
      codeBlock1.getCodeBlock().add(thenBranch);
    } else {
      ifStatement.getThenBranch().replace(thenBranch);
    }

    if (elseBranch != null) {
      if (!(elseBranch instanceof PsiBlockStatement)) {
        PsiBlockStatement codeBlock1 = (PsiBlockStatement) ifStatement.getElseBranch().replace(codeBlock);
        codeBlock1 = (PsiBlockStatement) CodeStyleManager.getInstance(project).reformat(codeBlock1);
        codeBlock1.getCodeBlock().add(elseBranch);
      } else {
        elseBranch = ifStatement.getElseBranch().replace(elseBranch);

        if (emptyBlock(((PsiBlockStatement) elseBranch).getCodeBlock())) {
          ifStatement.getElseBranch().delete();
        }
      }
    }

    ifStatement.getCondition().replace(condition);
  }

  private boolean emptyBlock (PsiCodeBlock block) {
    PsiElement[] children = block.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiComment) return false;
      if (!(child instanceof PsiWhiteSpace) && !(child instanceof PsiJavaToken)) return false;
    }
    return true;
  }

  private PsiElement findCodeBlock(PsiIfStatement ifStatement) {
    PsiElement e = PsiTreeUtil.getParentOfType(ifStatement, PsiMethod.class, PsiClassInitializer.class);
    if (e instanceof PsiMethod) return ((PsiMethod) e).getBody();
    if (e instanceof PsiClassInitializer) return ((PsiClassInitializer) e).getBody();
    return null;
  }

  private PsiElement findNearestCodeBlock(PsiIfStatement ifStatement) {
    return PsiTreeUtil.getParentOfType(ifStatement, PsiCodeBlock.class);
  }

  private ControlFlow buildControlFlow(PsiElement element) {
    try {
      return new ControlFlowAnalyzer(element, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false, false).buildControlFlow();
    }
    catch (AnalysisCanceledException e) {
      return ControlFlow.EMPTY;
    }
  }

  private void setupBranches(PsiIfStatement ifStatement, ControlFlow flow) throws IncorrectOperationException {
    PsiElementFactory factory = ifStatement.getManager().getElementFactory();
    Project project = ifStatement.getProject();

    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();

    if (elseBranch != null) {
      elseBranch = (PsiStatement) elseBranch.copy();
      setElseBranch(ifStatement, thenBranch, flow);
      ifStatement.getThenBranch().replace(elseBranch);
      return;
    }

    if (flow.getInstructions().length == 0) {
      ifStatement.setElseBranch(thenBranch);
      PsiStatement statement = factory.createStatementFromText("{}", null);
      statement = (PsiStatement) CodeStyleManager.getInstance(project).reformat(statement);
      statement = (PsiStatement) ifStatement.getThenBranch().replace(statement);
      CodeStyleManager.getInstance(project).reformat(statement);
      return;
    }

    int endOffset = calcEndOffset(flow, ifStatement);

    LOG.assertTrue(endOffset >= 0);

    if (endOffset >= flow.getSize()) {
      PsiStatement statement = factory.createStatementFromText("return;", null);
      statement = (PsiStatement) CodeStyleManager.getInstance(project).reformat(statement);
      if (thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement) thenBranch).getCodeBlock().getStatements();
        int len = statements.length;
        if (len > 0) {
          if (statements[len - 1] instanceof PsiReturnStatement) len--;
          if (len > 0) {
            ifStatement.getParent().addRangeAfter(statements[0], statements[len - 1], ifStatement);
          }
        }
      } else {
        if (!(thenBranch instanceof PsiReturnStatement)) {
          addAfter(ifStatement, thenBranch);
        }
      }
      ifStatement.getThenBranch().replace(statement);
      return;
    }
    PsiElement element = flow.getElement(endOffset);
    while (element != null && !(element instanceof PsiStatement)) element = element.getParent();

    if (element != null && element.getParent() instanceof PsiForStatement) {
      PsiForStatement forStatement = (PsiForStatement) element.getParent();
      if (forStatement.getUpdate() == element) {
        PsiStatement statement = factory.createStatementFromText("continue;", null);
        statement = (PsiStatement) CodeStyleManager.getInstance(project).reformat(statement);
        addAfter(ifStatement, thenBranch);
        ifStatement.getThenBranch().replace(statement);
        return;
      }
    }
    if (element instanceof PsiWhileStatement && flow.getStartOffset(element) == endOffset) {
      PsiStatement statement = factory.createStatementFromText("continue;", null);
      statement = (PsiStatement) CodeStyleManager.getInstance(project).reformat(statement);
      addAfter(ifStatement, thenBranch);
      ifStatement.getThenBranch().replace(statement);
      return;
    }

    if (element instanceof PsiReturnStatement) {
      PsiReturnStatement returnStatement = (PsiReturnStatement) element;
      addAfter(ifStatement, thenBranch);
      ifStatement.getThenBranch().replace(returnStatement.copy());

      ControlFlow flow2 = buildControlFlow(findCodeBlock(ifStatement));
      if (!ControlFlowUtil.isInstructionReachable(flow2, flow2.getStartOffset(returnStatement), 0)) returnStatement.delete();
      return;
    }

    boolean nextUnreachable = flow.getEndOffset(ifStatement) + 1 == flow.getSize();
    if (!nextUnreachable) {
      PsiElement nearestCodeBlock = findNearestCodeBlock(ifStatement);
      if (nearestCodeBlock != null) {
        ControlFlow flow2 = buildControlFlow(nearestCodeBlock);
        nextUnreachable = !ControlFlowUtil.isInstructionReachable(flow2, flow2.getEndOffset(ifStatement), getThenOffset(flow2, ifStatement));
      }
    }
    if (nextUnreachable) {
      setElseBranch(ifStatement, thenBranch, flow);

      PsiElement first = ifStatement.getNextSibling();
//      while (first instanceof PsiWhiteSpace) first = first.getNextSibling();
      if (first != null) {
        PsiElement last = first;
        while (last.getNextSibling() != null) last = last.getNextSibling();
        while (last instanceof PsiWhiteSpace || (last instanceof PsiJavaToken && ((PsiJavaToken) last).getTokenType() == JavaTokenType.RBRACE))
          last = last.getPrevSibling();


        PsiBlockStatement codeBlock = (PsiBlockStatement) factory.createStatementFromText("{}", null);
        codeBlock = (PsiBlockStatement) ifStatement.getThenBranch().replace(codeBlock);
        codeBlock.getCodeBlock().addRange(first, last);

        first.getParent().deleteChildRange(first, last);
      }
      CodeStyleManager.getInstance(project).reformat(ifStatement);
      return;
    }

    setElseBranch(ifStatement, thenBranch, flow);
    PsiStatement statement = factory.createStatementFromText("{}", null);
    statement = (PsiStatement) CodeStyleManager.getInstance(project).reformat(statement);
    statement = (PsiStatement) ifStatement.getThenBranch().replace(statement);
    CodeStyleManager.getInstance(project).reformat(statement);
  }

  private void setElseBranch(PsiIfStatement ifStatement, PsiStatement thenBranch, ControlFlow flow) throws IncorrectOperationException {
    if (flow.getEndOffset(ifStatement) == flow.getEndOffset(thenBranch)) {
      if (thenBranch instanceof PsiContinueStatement) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.delete();
        }
        return;
      }
      else if (thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement) thenBranch).getCodeBlock().getStatements();
        if (statements.length > 0 && statements[statements.length - 1] instanceof PsiContinueStatement) {
          statements[statements.length - 1].delete();
        }
      }
    }
    ifStatement.setElseBranch(thenBranch);
  }

  private void addAfter(PsiIfStatement ifStatement, PsiStatement thenBranch) throws IncorrectOperationException {
    if (thenBranch instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement = (PsiBlockStatement) thenBranch;
      PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
      if (statements.length > 0) {
        ifStatement.getParent().addRangeAfter(statements[0], statements[statements.length - 1], ifStatement);
      }
    } else {
      ifStatement.getParent().addAfter(thenBranch, ifStatement);
    }
  }

  private int getThenOffset(ControlFlow controlFlow, PsiIfStatement ifStatement) {
    Instruction[] instructions = controlFlow.getInstructions();
    PsiStatement thenBranch = ifStatement.getThenBranch();
    for (int i = 0; i < instructions.length; i++) {
      if (PsiTreeUtil.isAncestor(thenBranch, controlFlow.getElement(i), false)) return i;
    }
    return -1;
  }

  private int calcEndOffset(ControlFlow controlFlow, PsiIfStatement ifStatement) {
    int endOffset = -1;

    Instruction[] instructions = controlFlow.getInstructions();
    for (int i = 0; i < instructions.length; i++) {
      Instruction instruction = instructions[i];
      if (controlFlow.getElement(i) != ifStatement) continue;

      if (instruction instanceof GoToInstruction) {
        GoToInstruction goToInstruction = (GoToInstruction) instruction;
        if (goToInstruction.role != ControlFlow.JUMP_ROLE_GOTO_END) continue;

        endOffset = goToInstruction.offset;
        break;
      } else if (instruction instanceof ConditionalGoToInstruction) {
        ConditionalGoToInstruction goToInstruction = (ConditionalGoToInstruction) instruction;
        if (goToInstruction.role != ControlFlow.JUMP_ROLE_GOTO_END) continue;

        endOffset = goToInstruction.offset;
        break;
      }
    }

    int length = instructions.length;
    while (endOffset < length && instructions[endOffset] instanceof GoToInstruction && !((GoToInstruction) instructions[endOffset]).isReturn) {
      endOffset = ((GoToInstruction) instructions[endOffset]).offset;
    }

    return endOffset;
  }

  private PsiExpression invertCondition(PsiExpression condition) throws IncorrectOperationException {
    PsiElementFactory factory = condition.getManager().getElementFactory();

    if (condition instanceof PsiBinaryExpression) {
      PsiBinaryExpression expression = (PsiBinaryExpression) condition;
      PsiJavaToken operationSign = expression.getOperationSign();
      for (int i = 0; i < ourTokenMap.length; i++) {
        IElementType tokenType = ourTokenMap[i];
        if (operationSign.getTokenType() == tokenType) {
          expression = (PsiBinaryExpression)expression.copy();
          expression.getOperationSign().replace(createOperationToken(factory, ourTokenMap[i + (i % 2 == 0 ? 1 : -1)]));
          if (tokenType == JavaTokenType.OROR || tokenType == JavaTokenType.ANDAND) {
            expression.getLOperand().replace(invertCondition(expression.getLOperand()));
            expression.getROperand().replace(invertCondition(expression.getROperand()));
          }
          return expression;
        }
      }
    } else if (condition instanceof PsiPrefixExpression) {
      PsiPrefixExpression expression = (PsiPrefixExpression) condition;
      PsiJavaToken operationSign = expression.getOperationSign();
      if (operationSign.getTokenType() == JavaTokenType.EXCL) {
        PsiExpression operand = expression.getOperand();
        if (operand instanceof PsiParenthesizedExpression) {
          operand = ((PsiParenthesizedExpression) operand).getExpression();
        }
        return operand;
      }
    }

    if (condition instanceof PsiParenthesizedExpression) {
      PsiExpression operand = ((PsiParenthesizedExpression) condition).getExpression();
      operand.replace(invertCondition(operand));
      return condition;
    }

    PsiPrefixExpression result = (PsiPrefixExpression)factory.createExpressionFromText("!(a)", null);
    if (!(condition instanceof PsiBinaryExpression)) {
      result.getOperand().replace(condition);
    } else {
      PsiParenthesizedExpression e = (PsiParenthesizedExpression) result.getOperand();
      e.getExpression().replace(condition);
    }

    return result;
  }

  private PsiElement createOperationToken(PsiElementFactory factory, IElementType tokenType) throws IncorrectOperationException {
    final String s;
    if (tokenType == JavaTokenType.EQEQ) {
      s = "==";
    }
    else if (tokenType == JavaTokenType.NE) {
      s = "!=";
    }
    else if (tokenType == JavaTokenType.LT) {
      s = "<";
    }
    else if (tokenType == JavaTokenType.LE) {
      s = "<=";
    }
    else if (tokenType == JavaTokenType.GT) {
      s = ">";
    }
    else if (tokenType == JavaTokenType.GE) {
      s = ">=";
    }
    else if (tokenType == JavaTokenType.ANDAND) {
      s = "&&";
    }
    else if (tokenType == JavaTokenType.OROR) {
      s = "||";
    }
    else {
      LOG.error("Unknown token type");
      s = "==";
    }

    PsiBinaryExpression expression = (PsiBinaryExpression) factory.createExpressionFromText("a" + s + "b", null);
    return expression.getOperationSign();
  }

}
