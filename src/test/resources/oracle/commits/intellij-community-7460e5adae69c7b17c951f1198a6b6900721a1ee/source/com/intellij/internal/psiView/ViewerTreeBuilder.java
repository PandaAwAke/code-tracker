/**
 * class ViewerTreeBuilder
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class ViewerTreeBuilder extends AbstractTreeBuilder {
  public ViewerTreeBuilder(Project project, JTree tree) {
    super(tree, (DefaultTreeModel)tree.getModel(), new ViewerTreeStructure(project), IndexComparator.INSTANCE);
    initRootNode();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    Object element = nodeDescriptor.getElement();
    Object rootElement = myTreeStructure.getRootElement();
    if (rootElement.equals(element)) return true;
    NodeDescriptor parent = nodeDescriptor.getParentDescriptor();
    if (parent != null && rootElement.equals(parent.getElement())) return true;
    return false;
  }
}
