package com.intellij.ide.projectView.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

public final class ChangeProjectViewAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    ProjectView projectView = ProjectView.getInstance(project);
    projectView.changeView();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }
    String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
    presentation.setEnabled(ToolWindowId.PROJECT_VIEW.equals(id));
  }
}
