package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ToolWindow;

public class ToggleDockModeAction extends ToggleAction{

  public boolean isSelected(AnActionEvent event){
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return false;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id=windowManager.getActiveToolWindowId();
    if(id==null){
      return false;
    }
    return ToolWindowType.DOCKED==windowManager.getToolWindow(id).getType();
  }

  public void setSelected(AnActionEvent event,boolean flag){
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id=windowManager.getActiveToolWindowId();
    if(id==null){
      return;
    }
    ToolWindow toolWindow=windowManager.getToolWindow(id);
    ToolWindowType type=toolWindow.getType();
    if(ToolWindowType.DOCKED==type){
      toolWindow.setType(ToolWindowType.SLIDING, null);
    } else if(ToolWindowType.SLIDING==type){
      toolWindow.setType(ToolWindowType.DOCKED, null);
    }
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager mgr=ToolWindowManager.getInstance(project);
    String id=mgr.getActiveToolWindowId();
    if(id==null){
      presentation.setEnabled(false);
      return;
    }
    ToolWindow toolWindow=mgr.getToolWindow(id);
    presentation.setEnabled(toolWindow.isAvailable()&&ToolWindowType.FLOATING!=toolWindow.getType());
  }
}
