package com.intellij.openapi.vcs.update;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.io.File;

/**
 * author: lesya
 */
public abstract class AbstractTreeNode extends DefaultMutableTreeNode{
  protected static final ArrayList<File> EMPTY_FILE_ARRAY = new ArrayList<File>();
  DefaultTreeModel myTreeModel;
  private JTree myTree;

  public void setTree(JTree tree) {
    myTree = tree;
    if (children == null) return;
    for (Iterator each = children.iterator(); each.hasNext();) {
      AbstractTreeNode node = (AbstractTreeNode) each.next();
      node.setTree(tree);
    }

  }

  public void setTreeModel(DefaultTreeModel treeModel) {
    myTreeModel = treeModel;
    if (children == null) return;
    for (Iterator each = children.iterator(); each.hasNext();) {
      AbstractTreeNode node = (AbstractTreeNode) each.next();
      node.setTreeModel(treeModel);
    }
  }

  protected DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  public JTree getTree() {
    return myTree;
  }

  public AbstractTreeNode() {
  }

  public String getText(){
    StringBuffer result = new StringBuffer();
    result.append(getName());
    if(showStatistics()){
      result.append(" (");
      result.append(getStatistics(getItemsCount()));
      result.append(")");
    }
    return result.toString();
  }

  private String getStatistics(int itemsCount){
    if(itemsCount == 0) return "no items";
    if(itemsCount == 1) return "1 item";
    return "" + itemsCount + " items";
  }

  protected abstract String getName();
  protected abstract int getItemsCount();
  protected abstract boolean showStatistics();

  public abstract Icon getIcon(boolean expanded);
  public abstract Collection<VirtualFile> getVirtualFiles();
  public abstract Collection<File> getFiles();

  public abstract SimpleTextAttributes getAttributes();

  public abstract boolean getSupportsDeletion();
}
