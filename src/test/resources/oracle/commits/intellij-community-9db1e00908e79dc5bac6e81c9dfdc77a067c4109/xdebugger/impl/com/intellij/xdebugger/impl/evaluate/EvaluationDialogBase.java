package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.nodes.EvaluatingExpressionRootNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class EvaluationDialogBase extends DialogWrapper {
  private JPanel myMainPanel;
  private JPanel myResultPanel;
  private JPanel myInputPanel;
  private final XDebuggerTreePanel myTreePanel;
  private final XStackFrame myStackFrame;

  protected EvaluationDialogBase(@NotNull XDebugSession session, String title, final XDebuggerEditorsProvider editorsProvider, final XStackFrame stackFrame) {
    super(session.getProject(), true);
    myStackFrame = stackFrame;
    setModal(false);
    setTitle(title);
    setOKButtonText(XDebuggerBundle.message("xdebugger.button.evaluate"));
    setCancelButtonText(XDebuggerBundle.message("xdebugger.evaluate.dialog.close"));
    myTreePanel = new XDebuggerTreePanel(session, editorsProvider, stackFrame.getSourcePosition(), XDebuggerActions.EVALUATE_DIALOG_TREE_POPUP_GROUP);
    myResultPanel.add(myTreePanel.getMainPanel(), BorderLayout.CENTER);
    init();
  }

  protected JPanel getInputPanel() {
    return myInputPanel;
  }

  protected JPanel getResultPanel() {
    return myResultPanel;
  }

  protected void doOKAction() {
    evaluate();
  }

  protected void evaluate() {
    final XDebuggerTree tree = myTreePanel.getTree();
    final EvaluatingExpressionRootNode root = new EvaluatingExpressionRootNode(this, tree);
    tree.setRoot(root, false);
    myResultPanel.invalidate();
    getInputEditor().selectAll();
  }

  protected void dispose() {
    myTreePanel.dispose();
    super.dispose();
  }

  protected String getDimensionServiceKey() {
    return "#xdebugger.evaluate";
  }

  protected abstract XDebuggerEditorBase getInputEditor();

  public abstract void startEvaluation(XDebuggerEvaluator.XEvaluationCallback callback);

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected XDebuggerEvaluator getEvaluator() {
    return myStackFrame.getEvaluator();
  }

  public JComponent getPreferredFocusedComponent() {
    return getInputEditor().getPreferredFocusedComponent();
  }
}
