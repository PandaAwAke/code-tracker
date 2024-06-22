
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ide.IdeBundle;

public class CloseAllEditorsButActiveAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    FileEditorManagerEx fileEditorManager=FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (window != null){
      selectedFile = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
      final VirtualFile[] files = window.getFiles();
      for (int i = 0; i < files.length; i++) {
        VirtualFile file = files[i];
        if (file != selectedFile){
          window.closeFile(file);
        }
      }
      return;
    }
    selectedFile = fileEditorManager.getSelectedFiles()[0];
    VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    for(int i=0;i<siblings.length;i++){
      if(selectedFile!=siblings[i]){
        fileEditorManager.closeFile(siblings[i]);
      }
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.close.all.but.current"));
    }
    else if (ActionPlaces.EDITOR_TAB_POPUP.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.close.all.but.this"));
    }
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (window != null){
      presentation.setEnabled(window.getFiles().length > 1);
      return;
    } else {
      if (fileEditorManager.getSelectedFiles().length == 0) {
        presentation.setEnabled(false);
        return;
      }
      selectedFile = fileEditorManager.getSelectedFiles()[0];
    }
    VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    presentation.setEnabled(siblings.length > 1);
  }
}
