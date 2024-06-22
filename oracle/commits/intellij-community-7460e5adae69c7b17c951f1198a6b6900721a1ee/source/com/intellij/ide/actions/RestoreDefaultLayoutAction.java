package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;

/**
 * @author Vladimir Kondratyev
 */
public final class RestoreDefaultLayoutAction extends AnAction{
  public void actionPerformed(AnActionEvent e){
    Project project=(Project)e.getDataContext().getData(DataConstants.PROJECT);
    if(project==null){
      return;
    }
    DesktopLayout layout=WindowManagerEx.getInstanceEx().getLayout();
    ToolWindowManagerEx.getInstanceEx(project).setLayout(layout);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    presentation.setEnabled((Project)event.getDataContext().getData(DataConstants.PROJECT)!=null);
  }
}
