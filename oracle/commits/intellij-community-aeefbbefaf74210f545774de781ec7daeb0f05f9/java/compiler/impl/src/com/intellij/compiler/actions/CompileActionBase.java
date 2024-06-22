package com.intellij.compiler.actions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiFile;

public abstract class CompileActionBase extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    PsiFile file = e.getData(LangDataKeys.PSI_FILE);
    if (file != null && editor != null && !DumbService.getInstance(project).isDumb()) {
      DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file); //let autoimport complete
    }
    doAction(dataContext, project);
  }

  protected abstract void doAction(final DataContext dataContext, final Project project);

  public void update(final AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setEnabled(!CompilerManager.getInstance(project).isCompilationActive());
    }
  }
}
