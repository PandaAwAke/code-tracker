package com.intellij.application.options.pathMacros;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 *  @author dsl
 */
public class PathMacroListEditor {
  JPanel myPanel;
  JButton myAddButton;
  JButton myRemoveButton;
  JButton myEditButton;
  JScrollPane myScrollPane;
  private PathMacroTable myPathMacroTable;

  public PathMacroListEditor() {
    this(null, false);
  }

  public PathMacroListEditor(String[] undefinedMacroNames, boolean editOnlyPathsMode) {
    myPathMacroTable = undefinedMacroNames != null? new PathMacroTable(undefinedMacroNames, editOnlyPathsMode) : new PathMacroTable();
    myScrollPane.setViewportView(myPathMacroTable);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.addMacro();
      }
    });
    myAddButton.setMnemonic('A');
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.removeSelectedMacros();
      }
    });
    myRemoveButton.setMnemonic('R');
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myPathMacroTable.editMacro();
      }
    });
    myEditButton.setMnemonic('E');
    myPathMacroTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateControls();
      }
    });

    updateControls();
  }

  private void updateControls() {
    myAddButton.setEnabled(myPathMacroTable.isAddEnabled());
    myRemoveButton.setEnabled(myPathMacroTable.isRemoveEnabled());
    myEditButton.setEnabled(myPathMacroTable.isEditEnabled());
  }

  public void commit() throws ConfigurationException{
    final int count = myPathMacroTable.getRowCount();
    for (int idx = 0; idx < count; idx++) {
      String value = myPathMacroTable.getMacroValueAt(idx);
      if (value == null || value.length() == 0) {
        throw new ConfigurationException("Path variable \"" + myPathMacroTable.getMacroNameAt(idx) + "\" is undefined");
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myPathMacroTable.commit();
      }
    });
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void reset() {
    myPathMacroTable.reset();
  }

  public boolean isModified() {
    return myPathMacroTable.isModified();
  }
}
