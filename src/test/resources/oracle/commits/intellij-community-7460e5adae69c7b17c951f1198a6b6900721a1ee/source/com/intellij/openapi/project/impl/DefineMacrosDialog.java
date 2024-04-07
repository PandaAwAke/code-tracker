/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.Table;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;

import gnu.trove.TObjectIntHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 3, 2004
 */
public class DefineMacrosDialog extends DialogWrapper{
  public static final int MACRO_NAME = 0;
  public static final int MACRO_VALUE = 1;
  private final String[][] myMacroTable;
  private final TObjectIntHashMap myIndex = new TObjectIntHashMap();

  public DefineMacrosDialog(String[] macroNames) {
    super(true);
    myMacroTable = new String[macroNames.length][2];

    for (int idx = 0; idx < macroNames.length; idx++) {
      final String macroName = macroNames[idx];
      myMacroTable[idx] = new String[]{macroName, ""};
      myIndex.put(macroName, idx);
    }
    setCancelButtonText("Cancel Project Load");
    init();
  }

  protected void doOKAction() {
    for (int idx = 0; idx < myMacroTable.length; idx++) {
      String[] row = myMacroTable[idx];
      String path = row[MACRO_VALUE];

      if (path == null || path.length() == 0) {
        Messages.showErrorDialog(getContentPane(), "Value for the path variable \"" + row[MACRO_NAME] + "\" is not defined", "Undefined Path Variable");
        return;
      }
      if (!new File(path).exists()) {
        Messages.showErrorDialog(getContentPane(), "Value for the path variable \"" + row[MACRO_NAME] + "\" is not defined", "Undefined Path Variable");
        return;
      }
    }
    super.doOKAction();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    Table table = new Table(new MyTableModel());
    JLabel label = new JLabel("TODO: message");
    label.setBorder(IdeBorderFactory.createEmptyBorder(6, 6, 6, 6));
    panel.add(label, BorderLayout.NORTH);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    return panel;
  }

  public String getMacroValue(String macro) {
    final int index = myIndex.get(macro);
    return (index >= 0 && index < myMacroTable.length)? myMacroTable[index][MACRO_VALUE] : null;
  }

  private class MyTableModel extends AbstractTableModel {
    public String getColumnName(int column) {
      switch(column) {
        case MACRO_NAME : return "Macro Name";
        case MACRO_VALUE : return "Path";
      }
      return "";
    }

    public int getColumnCount() {
      return myMacroTable[0].length;
    }

    public int getRowCount() {
      return myMacroTable.length;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      return myMacroTable[rowIndex][columnIndex];
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == MACRO_VALUE && aValue instanceof String) {
        myMacroTable[rowIndex][MACRO_VALUE] = (String)aValue;
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == MACRO_VALUE;
    }
  }

}
