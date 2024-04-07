package com.intellij.debugger.ui.impl.tree;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: lex
 * Date: Sep 10, 2003
 * Time: 6:56:51 PM
 */
public abstract class TreeBuilder implements TreeModel {
  private Object userObject;
  private TreeBuilderNode myRoot;
  private List<TreeModelListener> myListeners = new ArrayList<TreeModelListener>();

  protected TreeBuilder(Object userObject) {
    this.userObject = userObject;
  }

  public Object getUserObject() {
    return userObject;
  }

  protected abstract void    buildChildren(TreeBuilderNode node);
  protected abstract boolean isExpandable (TreeBuilderNode node);

  public void setRoot(TreeBuilderNode root) {
    myRoot = root;
  }

  public Object getRoot() {
    return myRoot;
  }

  public int getChildCount(Object parent) {
    return ((TreeBuilderNode) parent).getChildCount();
  }

  public boolean isLeaf(Object node) {
    return ((TreeBuilderNode) node).isLeaf();
  }

  public void addTreeModelListener(TreeModelListener l) {
    myListeners.add(l);
  }

  public void removeTreeModelListener(TreeModelListener l) {
    myListeners.remove(l);
  }

  public Object getChild(Object parent, int index) {
    return ((TreeBuilderNode) parent).getChildAt(index);
  }

  public int getIndexOfChild(Object parent, Object child) {
    return ((TreeBuilderNode) parent).getIndex((TreeNode) child);
  }

  public void valueForPathChanged(TreePath path, Object newValue) {
    TreeBuilderNode  aNode = (TreeBuilderNode) path.getLastPathComponent();

    aNode.setUserObject(newValue);
    nodeChanged(aNode);
  }

  public void nodeChanged(TreeNode node) {
    TreeModelEvent event = null;
    TreeNode parent = node.getParent();
    if (parent != null) {
      int anIndex = parent.getIndex(node);
      event = new TreeModelEvent(this, getPathToRoot(parent, 0), new int[] {anIndex}, new Object[] {node});
    } else if (node == getRoot()) {
      event = new TreeModelEvent(this, getPathToRoot(node, 0), null, null);
    }
    if (event != null) {
      for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
        TreeModelListener treeModelListener = (TreeModelListener) iterator.next();
        treeModelListener.treeNodesChanged(event);
      }
    }
  }

  public void nodeStructureChanged(TreeNode node) {
    TreeModelEvent event = new TreeModelEvent(this, getPathToRoot(node, 0), null, null);
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      TreeModelListener treeModelListener = (TreeModelListener) iterator.next();
      treeModelListener.treeStructureChanged(event);
    }
  }

  protected TreeNode[] getPathToRoot(TreeNode aNode, int depth) {
      TreeNode[]              retNodes;
      if(aNode == null) {
          if(depth == 0)
              return null;
          else
              retNodes = new TreeNode[depth];
      }
      else {
          depth++;
          if(aNode == myRoot)
              retNodes = new TreeNode[depth];
          else
              retNodes = getPathToRoot(aNode.getParent(), depth);
          retNodes[retNodes.length - depth] = aNode;
      }
      return retNodes;
  }

  public void removeNodeFromParent(TreeBuilderNode node) {
    ((TreeBuilderNode) node.getParent()).remove(node);
  }

}
