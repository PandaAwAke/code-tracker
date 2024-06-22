/**
 * @author Yura Cangea
 */
package com.intellij.tools;

import com.intellij.execution.filters.InvalidExpressionException;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class FilterDialog extends DialogWrapper {
  private final JTextField myRegexpField = new JTextField();
  private final JTextField myNameField = new JTextField();
  private final JTextField myDescriptionField = new JTextField();

  private JPopupMenu myPopup;

  private FilterDialog(Component component) {
    super(component, true);
    init();
    setOKActionEnabled(true);
    myRegexpField.setToolTipText("Press the right mouse button to see the list of available macros");
  }

  public static boolean editFilter(FilterInfo filterInfo, JComponent parentComponent, String title) throws InvalidExpressionException {
    FilterDialog dialog = new FilterDialog(parentComponent);
    dialog.setTitle(title);
    dialog.myNameField.setText(filterInfo.getName());
    dialog.myDescriptionField.setText(filterInfo.getDescription());
    dialog.myRegexpField.setText(filterInfo.getRegExp());
    dialog.show();
    if (!dialog.isOK()) return false;
    filterInfo.setName(dialog.myNameField.getText());
    filterInfo.setDescription(dialog.myDescriptionField.getText());
    filterInfo.setRegExp(dialog.myRegexpField.getText());
    return true;
  }

  public JComponent getPreferredFocusedComponent() {
    return myRegexpField;
  }

  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout());

    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints constr;

    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.weighty = 0;
    constr.gridwidth = 1;
    constr.insets = new Insets(5, 0, 0, 0);
    panel.add(new JLabel("Name:"), constr);

    constr.gridx = 0;
    constr.gridy = 1;
    constr.weightx = 1;
    constr.gridwidth = 3;
    constr.fill = GridBagConstraints.HORIZONTAL;
    panel.add(myNameField, constr);

    constr.gridx = 0;
    constr.gridy = 2;
    constr.weightx = 0;
    panel.add(new JLabel("Description:"), constr);

    constr.gridx = 0;
    constr.gridy = 3;
    constr.gridwidth = 2;
    constr.weightx = 1;
    panel.add(myDescriptionField, constr);

    constr.gridy = 4;
    constr.gridx = 0;
    constr.gridwidth = 2;
    constr.weightx = 0;
    panel.add(new JLabel("Regular expression to match output:"), constr);

    constr.gridx = 0;
    constr.gridy = 5;
    constr.gridwidth = 3;
    panel.add(myRegexpField, constr);

    makePopup();

    panel.setPreferredSize(new Dimension(335, 150));

    mainPanel.add(panel, BorderLayout.NORTH);

    return mainPanel;
  }

  private void makePopup() {
    myPopup = new JPopupMenu();
    String[] macrosName = RegexpFilter.getMacrosName();
    JMenuItem[] items = new JMenuItem[macrosName.length];
    for (int i = 0; i < macrosName.length; i++) {
      items[i] = myPopup.add(macrosName[i]);
      items[i].addActionListener(new MenuItemListener(macrosName[i]));
    }
    myRegexpField.addMouseListener(new PopupListener());
  }

  protected void doOKAction() {
    String errorMessage = null;
    if (noText(myNameField.getText())) {
      errorMessage = "Filter name is not defined";
    } else if (noText(myRegexpField.getText())) {
      errorMessage = "Regular expression must be defined";
    }

    if (errorMessage != null) {
      Messages.showMessageDialog(getContentPane(), errorMessage, "Error", Messages.getErrorIcon());
      return;
    }

    try {
      checkRegexp(myRegexpField.getText());
    } catch (InvalidExpressionException e) {
      Messages.showMessageDialog(getContentPane(), e.getMessage(), "Invalid Regular Expression", Messages.getErrorIcon());
      return;
    }
    super.doOKAction();
  }

  private void checkRegexp(String regexpText) {
    RegexpFilter.validate(regexpText);
  }

  private boolean noText(String text) {
    return "".equals(text);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.tools.FilterDialog";
  }

  private class MenuItemListener implements ActionListener {
    private final String myMacrosName;

    private MenuItemListener(String macrosName) {
      myMacrosName = macrosName;
    }

    public void actionPerformed(ActionEvent e) {
      int position = myRegexpField.getCaretPosition();
      try {
        if (myRegexpField.getText().indexOf(myMacrosName) == -1) {
          myRegexpField.getDocument().insertString(position, myMacrosName, null);
          myRegexpField.setCaretPosition(position + myMacrosName.length());
        }
      } catch (BadLocationException ex) {
      }
      myRegexpField.requestFocus();
    }
  }

  private class PopupListener extends PopupHandler {
    public void invokePopup(Component comp, int x, int y) {
      myPopup.show(comp, x, y);
    }
  }
}
