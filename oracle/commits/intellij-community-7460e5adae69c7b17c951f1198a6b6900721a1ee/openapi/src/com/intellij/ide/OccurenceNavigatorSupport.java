package com.intellij.ide;

import com.intellij.pom.Navigatable;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Enumeration;

public abstract class OccurenceNavigatorSupport implements OccurenceNavigator {
  private JTree myTree;

  public OccurenceNavigatorSupport(JTree tree) {
    myTree = tree;
  }

  protected abstract Navigatable createDescriptorForNode(DefaultMutableTreeNode node);

  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    Counters counters = new Counters();
    DefaultMutableTreeNode node = findNode(myTree, true, counters);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorForNode(node);
    if (editSourceDescriptor == null) return null;
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    Counters counters = new Counters();
    DefaultMutableTreeNode node = findNode(myTree, false, counters);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorForNode(node);
    if (editSourceDescriptor == null) return null;
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  public boolean hasNextOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, true, null);
    if (node == null) return false;
    return true;
  }

  public boolean hasPreviousOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, false, null);
    if (node == null) return false;
    return true;
  }

  protected static class Counters {
    /**
     * Equals to <code>-1</code> if this value is unsupported.
     */
    public int myFoundOccurenceNumber;
    /**
     * Equals to <code>-1</code> if this value is unsupported.
     */
    public int myOccurencesCount;
  }

  protected DefaultMutableTreeNode findNode(JTree tree, boolean forward, Counters counters) {
    TreePath selectionPath = tree.getSelectionPath();
    TreeNode selectedNode = null;
    boolean[] ready = new boolean[] {true};
    if (selectionPath != null) {
      selectedNode = (TreeNode)selectionPath.getLastPathComponent();
      ready[0] = false;
    }

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();

    Enumeration enumeration = root.preorderEnumeration();
    ArrayList nodes = new ArrayList();
    while (enumeration.hasMoreElements()) {
      TreeNode node = (TreeNode)enumeration.nextElement();
      nodes.add(node);
    }

    DefaultMutableTreeNode result = null;

    if (forward) {
      for (int i=0; i < nodes.size(); i++) {
        TreeNode node = (TreeNode)nodes.get(i);
        DefaultMutableTreeNode nextNode = getNode(node, selectedNode, ready);
        if (nextNode != null) {
          result = nextNode;
          break;
        }
      }
    }
    else {
      for (int i=nodes.size() - 1; i >= 0; i--) {
        TreeNode node = (TreeNode)nodes.get(i);
        DefaultMutableTreeNode nextNode = getNode(node, selectedNode, ready);
        if (nextNode != null) {
          result = nextNode;
          break;
        }
      }
    }

    if (result == null) {
      return null;
    }

    if (counters != null) {
      counters.myFoundOccurenceNumber = 0;
      counters.myOccurencesCount = 0;
      for (int i=0; i < nodes.size(); i++) {
        TreeNode node = (TreeNode)nodes.get(i);
        if (!(node instanceof DefaultMutableTreeNode)) continue;

        Navigatable descriptor = createDescriptorForNode((DefaultMutableTreeNode)node);
        if (descriptor == null) continue;

        counters.myOccurencesCount++;
        if (result == node) {
          counters.myFoundOccurenceNumber = counters.myOccurencesCount;
        }
      }
    }

    return result;
  }

  protected DefaultMutableTreeNode getNode(TreeNode node, TreeNode selectedNode, boolean[] ready) {
    if (!ready[0]) {
      if (node == selectedNode) {
        ready[0] = true;
      }
      return null;
    }
    if (!(node instanceof DefaultMutableTreeNode)) return null;

    Navigatable descriptor = createDescriptorForNode((DefaultMutableTreeNode)node);
    if (descriptor == null) return null;
    return (DefaultMutableTreeNode)node;
  }
}
