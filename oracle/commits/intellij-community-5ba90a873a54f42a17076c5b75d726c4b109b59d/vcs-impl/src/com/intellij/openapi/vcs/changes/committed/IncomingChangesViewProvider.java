package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class IncomingChangesViewProvider implements ChangesViewContentProvider {
  private Project myProject;
  private MessageBus myBus;
  private CommittedChangesTreeBrowser myBrowser;
  private MessageBusConnection myConnection;
  private JLabel myErrorLabel = new JLabel();

  public IncomingChangesViewProvider(final Project project, final MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public JComponent initContent() {
    myBrowser = new CommittedChangesTreeBrowser(myProject, Collections.<CommittedChangeList>emptyList());
    myBrowser.setEmptyText(VcsBundle.message("incoming.changes.not.loaded.message"));
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("IncomingChangesToolbar");
    final ActionToolbar toolbar = myBrowser.createGroupFilterToolbar(myProject, group, null, Collections.<AnAction>emptyList());
    myBrowser.addToolBar(toolbar.getComponent());
    myBrowser.setTableContextMenu(group, Collections.<AnAction>emptyList());
    myConnection = myBus.connect();
    myConnection.subscribe(CommittedChangesCache.COMMITTED_TOPIC, new MyCommittedChangesListener());
    loadChangesToBrowser();

    JPanel contentPane = new JPanel(new BorderLayout());
    contentPane.add(myBrowser, BorderLayout.CENTER);
    contentPane.add(myErrorLabel, BorderLayout.SOUTH);
    myErrorLabel.setForeground(Color.red);
    return contentPane;
  }

  public void disposeContent() {
    myConnection.disconnect();
    myBrowser = null;
  }

  private void updateModel() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (myBrowser != null) {
          loadChangesToBrowser();
        }
      }
    });
  }

  private void loadChangesToBrowser() {
    final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
    if (cache.hasCachesForAnyRoot()) {
      final List<CommittedChangeList> list = cache.getCachedIncomingChanges();
      if (list != null) {
        myBrowser.setEmptyText(VcsBundle.message("incoming.changes.empty.message"));
        myBrowser.setItems(list, false, CommittedChangesBrowserUseCase.INCOMING);
      }
      else {
        cache.loadIncomingChangesAsync(null);
      }
    }
  }

  private class MyCommittedChangesListener extends CommittedChangesAdapter {
    public void changesLoaded(final RepositoryLocation location, final List<CommittedChangeList> changes) {
      updateModel();
    }

    public void incomingChangesUpdated(final List<CommittedChangeList> receivedChanges) {
      updateModel();
    }

    public void refreshErrorStatusChanged(@Nullable final VcsException lastError) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (lastError != null) {
            myErrorLabel.setText("Error refreshing changes: " + lastError.getMessage());
          }
          else {
            myErrorLabel.setText("");
          }
        }
      });
    }
  }
}
