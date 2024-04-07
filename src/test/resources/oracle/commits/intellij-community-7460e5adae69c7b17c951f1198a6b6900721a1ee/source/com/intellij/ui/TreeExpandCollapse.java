package com.intellij.ui;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeModel;

public class TreeExpandCollapse {
  public static void collapse(JTree tree) {
    TreePath selectionPath = tree.getSelectionPath();
    tree.collapsePath(selectionPath);
  }

  public static void expand(JTree tree) {
    TreePath selectionPath = tree.getSelectionPath();
    tree.expandPath(selectionPath);
  }

  public static void expandAll(JTree tree) {
    TreePath path = tree.getSelectionPath();
    if (path == null) path = new TreePath(tree.getModel().getRoot());
    new ExpandContext(300, 10).expand(tree, path);
  }

  private static class ExpandContext {
    private int myLevelsLeft;
    private int myExpansionLimit;

    public ExpandContext(int expansionLimit, int levelsLeft) {
      myExpansionLimit = expansionLimit;
      myLevelsLeft = levelsLeft;
    }

    public int expand(JTree tree, TreePath path) {
      if (myLevelsLeft == 0) return myExpansionLimit;
      TreeModel model = tree.getModel();
      Object node = path.getLastPathComponent();
      int levelDecrement = 0;
      if (!tree.isExpanded(path) && !model.isLeaf(node)) {
        tree.expandPath(path);
        levelDecrement = 1;
        myExpansionLimit--;
      }
      for (int i = 0; i < model.getChildCount(node); i++) {
        Object child = model.getChild(node, i);
        if (model.isLeaf(child)) continue;
        ExpandContext childContext = new ExpandContext(myExpansionLimit, myLevelsLeft - levelDecrement);
        myExpansionLimit = childContext.expand(tree, path.pathByAddingChild(child));
        if (myExpansionLimit <= 0) return 0;
      }
      return myExpansionLimit;
    }
  }
}
