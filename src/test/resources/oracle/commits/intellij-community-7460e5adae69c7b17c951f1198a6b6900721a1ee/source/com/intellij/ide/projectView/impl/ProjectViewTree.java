package com.intellij.ide.projectView.impl;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.util.ui.Tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 17, 2003
 * Time: 7:44:22 PM
 */
public abstract class ProjectViewTree extends Tree {
  protected ProjectViewTree(TreeModel newModel) {
    super(newModel);
    setCellRenderer(new NodeRenderer());
  }

  public final int getToggleClickCount() {
    DefaultMutableTreeNode node = getSelectedNode();
    if (node != null) {
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        if (!descriptor.expandOnDoubleClick()) return -1;
      }
    }
    return super.getToggleClickCount();
  }

  public abstract DefaultMutableTreeNode getSelectedNode();
}
