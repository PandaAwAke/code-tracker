package com.intellij.ide.actions;

import com.intellij.ui.content.ContentManagerUtil;
import com.intellij.ui.content.ContentManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public class CloseActiveTabAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(e.getDataContext(), true);
    if (contentManager != null && contentManager.canCloseContents()) {
      contentManager.removeContent(contentManager.getSelectedContent());
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    ContentManager contentManager=ContentManagerUtil.getContentManagerFromContext(event.getDataContext(), true);
    presentation.setEnabled(contentManager != null && contentManager.canCloseContents());
  }
}