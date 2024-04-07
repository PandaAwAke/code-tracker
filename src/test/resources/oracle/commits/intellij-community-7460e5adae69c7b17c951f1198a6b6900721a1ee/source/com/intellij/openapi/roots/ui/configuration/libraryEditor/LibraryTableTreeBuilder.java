package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

class LibraryTableTreeBuilder extends AbstractTreeBuilder {
  public LibraryTableTreeBuilder(JTree tree, DefaultTreeModel treeModel, AbstractTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure, IndexComparator.INSTANCE);
    initRootNode();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    final Object element = nodeDescriptor.getElement();
    final Object rootElement = getTreeStructure().getRootElement();
    return rootElement.equals(element) || (element instanceof ClassesElement) || (element instanceof SourcesElement) ||
           (element instanceof JavadocElement);
  }
}
