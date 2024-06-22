package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.util.Enumeration;

/**
 * @author max
 */
public class InspectionNode extends InspectionTreeNode {
  public static final Icon TOOL;

  static {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(IconLoader.getIcon("/general/toolWindowInspection.png"), 0);
    layeredIcon.setIcon(IconUtilEx.getEmptyIcon(false), 1);
    TOOL = layeredIcon;
  }

  public InspectionNode(InspectionTool tool) {
    super(tool);
  }

  public String toString() {
    return getTool().getDisplayName();
  }

  public InspectionTool getTool() {
    return (InspectionTool)getUserObject();
  }

  public Icon getIcon(boolean expanded) {
    return TOOL;
  }

  public int getProblemCount() {
    int sum = 0;
    Enumeration children = children();
    while (children.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
      if (child instanceof EntryPointsNode) continue;
      sum += child.getProblemCount();
    }
    return sum;
  }
}
