/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.ui.tree.TreeInplaceEditor;

import javax.swing.*;
import javax.swing.tree.TreePath;

public abstract class DebuggerTreeInplaceEditor extends TreeInplaceEditor {
  private final DebuggerTreeNodeImpl myNode;

  protected Project getProject() {
    return myNode.getTree().getProject();
  }

  public DebuggerTreeInplaceEditor(DebuggerTreeNodeImpl node) {
    myNode = node;
  }

  protected TreePath getNodePath() {
    return new TreePath(myNode.getPath());
  }

  protected JTree getTree() {
    return myNode.getTree();
  }

  @Override
  public void show() {
    myNode.getTree().onEditorShown(myNode);
    super.show();
  }

  @Override
  protected void remove() {
    myNode.getTree().onEditorHidden(myNode);
    super.remove();
  }
}
