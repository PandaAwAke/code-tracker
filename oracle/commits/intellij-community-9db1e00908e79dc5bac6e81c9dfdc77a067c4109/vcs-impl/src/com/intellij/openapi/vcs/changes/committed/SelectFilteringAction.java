package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;

import javax.swing.*;

/**
 * @author yole
 */
public class SelectFilteringAction extends LabeledComboBoxAction {
  private final Project myProject;
  private final CommittedChangesTreeBrowser myBrowser;
  private String myPreviousSelection;

  public SelectFilteringAction(final Project project, final CommittedChangesTreeBrowser browser) {
    super(VcsBundle.message("committed.changes.filter.title"));
    myProject = project;
    myBrowser = browser;
    myPreviousSelection = null;
  }

  protected ComboBoxModel createModel() {
    final DefaultComboBoxModel model = new DefaultComboBoxModel(new Object[]{
      ChangeListFilteringStrategy.NONE,
      new ColumnFilteringStrategy(ChangeListColumn.NAME, null),
      new StructureFilteringStrategy(myProject)
    });
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    for(AbstractVcs vcs: vcss) {
      final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider != null) {
        for(ChangeListColumn column: provider.getColumns()) {
          if (ChangeListColumn.isCustom(column)) {
            model.addElement(new ColumnFilteringStrategy(column, provider.getClass()));
          }
        }
      }
    }
    return model;
  }

  protected void selectionChanged(final Object selection) {
    if (myPreviousSelection != null) {
        myBrowser.removeFilteringStrategy(myPreviousSelection);
    }
    if (! ChangeListFilteringStrategy.NONE.equals(selection)) {
      myBrowser.setFilteringStrategy(selection.toString(), (ChangeListFilteringStrategy) selection);
    }
    myPreviousSelection = selection.toString();
  }
}
