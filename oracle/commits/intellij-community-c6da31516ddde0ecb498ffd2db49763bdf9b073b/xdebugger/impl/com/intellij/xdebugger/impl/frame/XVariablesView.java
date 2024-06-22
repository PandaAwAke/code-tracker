package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.Disposable;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class XVariablesView extends XDebugViewBase {
  private XDebuggerTreePanel myDebuggerTreePanel;
  private XDebuggerTreeState myTreeState;
  private Object myFrameEqualityObject;
  private XDebuggerTreeRestorer myTreeRestorer;

  public XVariablesView(@NotNull XDebugSession session, final Disposable parentDisposable) {
    super(session, parentDisposable);
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    myDebuggerTreePanel = new XDebuggerTreePanel(session, editorsProvider, null, XDebuggerActions.VARIABLES_TREE_POPUP_GROUP);
    DnDManager.getInstance().registerSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
  }

  protected void rebuildView(final SessionEvent event) {
    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    XDebuggerTree tree = myDebuggerTreePanel.getTree();

    if (event == SessionEvent.BEFORE_RESUME || event == SessionEvent.SETTINGS_CHANGED) {
      if (myTreeRestorer != null) {
        myTreeRestorer.dispose();
      }
      myFrameEqualityObject = stackFrame != null ? stackFrame.getEqualityObject() : null;
      myTreeState = XDebuggerTreeState.saveState(tree);
      if (event == SessionEvent.BEFORE_RESUME) {
        return;
      }
    }

    tree.markNodesObsolete();
    if (stackFrame != null) {
      tree.setSourcePosition(stackFrame.getSourcePosition());
      tree.setRoot(new XStackFrameNode(tree, stackFrame), false);
      Object newEqualityObject = stackFrame.getEqualityObject();
      if (myFrameEqualityObject != null && newEqualityObject != null && myFrameEqualityObject.equals(newEqualityObject) && myTreeState != null) {
        myTreeRestorer = myTreeState.restoreState(tree);
      }
    }
    else {
      tree.setSourcePosition(null);
      tree.setRoot(MessageTreeNode.createInfoMessage(tree, null, mySession.getDebugProcess().getCurrentStateMessage()), true);
    }
  }

  public JComponent getPanel() {
    return myDebuggerTreePanel.getMainPanel();
  }

  @Override
  public void dispose() {
    DnDManager.getInstance().unregisterSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
    super.dispose();
  }
}
