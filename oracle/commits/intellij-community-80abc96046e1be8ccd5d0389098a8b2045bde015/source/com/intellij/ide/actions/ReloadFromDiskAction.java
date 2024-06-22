

package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public class ReloadFromDiskAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null) return;
    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;

    int res = Messages.showOkCancelDialog(
      project,
      "Reload \"" + psiFile.getVirtualFile().getPresentableUrl() + "\" from disk and lose all changes?",
      "Reload File",
      Messages.getWarningIcon()
    );
    if (res != 0) return;

    CommandProcessor.getInstance().executeCommand(
        project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(
            new Runnable() {
              public void run() {
                PsiManager.getInstance(project).reloadFromDisk(psiFile);
                CommandProcessor.getInstance().markCurrentCommandAsComplex(project);
              }
            }
          );
        }
      },
      "Reload from Disk",
      null
    );
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }
    Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null || psiFile.getVirtualFile() == null){
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(document.getModificationStamp() != psiFile.getVirtualFile().getModificationStamp());
  }
}
