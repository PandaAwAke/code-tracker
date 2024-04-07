package com.intellij.cvsSupport2.checkinProject;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagNameFieldOwner;
import com.intellij.cvsSupport2.ui.experts.importToCvs.CvsFieldValidator;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AdditionalOptionsPanel implements RefreshableOnComponent, TagNameFieldOwner {

  private JPanel myPanel;
  private JTextField myTagName;
  private JCheckBox myTag;
  private JLabel myErrorLabel;

  private final boolean myCheckinProject;
  private final CvsConfiguration myConfiguration;
  private boolean myIsCorrect = true;
  private String myErrorMessage;
  private JCheckBox myOverrideExistings;

  public AdditionalOptionsPanel(boolean checkinProject,
                                CvsConfiguration configuration) {
    myCheckinProject = checkinProject;
    myConfiguration = configuration;
    myTag.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateEnable();
      }
    });
    CvsFieldValidator.installOn(this, myTagName, myErrorLabel, new AbstractButton[]{myTag});
  }

  private void updateEnable() {
    boolean tag = myTag.isSelected();
    myTagName.setEditable(tag);
    myOverrideExistings.setEnabled(tag);
  }

  public void refresh() {
    if (myCheckinProject) {
      myTag.setSelected(myConfiguration.TAG_AFTER_PROJECT_COMMIT);
      myTagName.setText(myConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);
      myOverrideExistings.setSelected(myConfiguration.OVERRIDE_EXISTING_TAG_FOR_PROJECT);
    }
    else {
      myTag.setSelected(myConfiguration.TAG_AFTER_FILE_COMMIT);
      myTagName.setText(myConfiguration.TAG_AFTER_FILE_COMMIT_NAME);
      myOverrideExistings.setSelected(myConfiguration.OVERRIDE_EXISTING_TAG_FOR_FILE);
    }
    updateEnable();
  }

  public void saveState() {
    if (!myIsCorrect) {
      throw new InputException("Tag name " + myErrorMessage, myTagName);
    }
    if (myCheckinProject) {
      myConfiguration.TAG_AFTER_PROJECT_COMMIT = myTag.isSelected();
      myConfiguration.OVERRIDE_EXISTING_TAG_FOR_PROJECT = myOverrideExistings.isSelected();
      myConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME = myTagName.getText().trim();
    }
    else {
      myConfiguration.TAG_AFTER_FILE_COMMIT = myTag.isSelected();
      myConfiguration.OVERRIDE_EXISTING_TAG_FOR_FILE = myOverrideExistings.isSelected();
      myConfiguration.TAG_AFTER_FILE_COMMIT_NAME = myTagName.getText().trim();
    }

  }

  public void restoreState() {
    refresh();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void enableOkAction() {
    myIsCorrect = true;
  }

  public void disableOkAction(String errorMeesage) {
    myIsCorrect = false;
    myErrorMessage = errorMeesage;
  }

  public boolean tagFieldIsActive() {
    return myTag.isSelected();
  }
}