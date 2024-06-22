package com.intellij.codeInsight.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class LayoutProjectCodeDialog extends DialogWrapper {
  private static final String OPTIMIZE_IMPORTS_KEY = "LayoutCode.optimizeImports";
  public static final String HELP_ID = "editing.codeReformatting";

  private String myText;
  private boolean mySuggestOptmizeImports;
  private JCheckBox myCbOptimizeImports;

  public LayoutProjectCodeDialog(Project project, String title, String text, boolean suggestOptmizeImports) {
    super(project, false);
    myText = text;
    mySuggestOptmizeImports = suggestOptmizeImports;
    setOKButtonText("Run");
    setTitle(title);
    init();
  }

  protected JComponent createCenterPanel() {
    if (!mySuggestOptmizeImports) return new JLabel(myText);
    JPanel panel = new JPanel(new GridLayout(2, 1));
    panel.add(new JLabel(myText));
    myCbOptimizeImports = new JCheckBox("Optimize imports");
    panel.add(myCbOptimizeImports);
    myCbOptimizeImports.setSelected("true".equals(PropertiesComponent.getInstance().getValue(OPTIMIZE_IMPORTS_KEY)));
    return panel;
  }

  private void setOptimizeImportsOption(boolean state) {
    PropertiesComponent.getInstance().setValue(OPTIMIZE_IMPORTS_KEY, state ? "true" : "false");
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected void doOKAction() {
    super.doOKAction();
    if (mySuggestOptmizeImports) {
      setOptimizeImportsOption(isOptimizeImports());
    }
  }

  public boolean isOptimizeImports() {
    return myCbOptimizeImports.isSelected();
  }
}
