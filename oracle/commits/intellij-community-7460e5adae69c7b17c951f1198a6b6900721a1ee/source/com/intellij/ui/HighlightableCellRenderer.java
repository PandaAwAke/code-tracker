package com.intellij.ui;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

public class HighlightableCellRenderer extends HighlightableComponent implements TreeCellRenderer, ListCellRenderer {
  public Component getTreeCellRendererComponent(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
    ) {
    setText(tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus));
    setFont(UIManager.getFont("Tree.font"));
    setIcon(null);

    myIsSelected = selected;
    myHasFocus = hasFocus;
    return this;
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    setText((value == null) ? "" : value.toString());
    setFont(UIManager.getFont("List.font"));
    setIcon(null);

    myIsSelected = isSelected;
    myHasFocus = cellHasFocus;
    return this;
  }
}
