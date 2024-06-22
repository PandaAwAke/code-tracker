package com.intellij.jar;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.util.Collection;

/**
 * @author cdr
 */
public class BuildJarAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    Collection<Module> modulesToJar = BuildJarDialog.getModulesToJar(project);
    if (modulesToJar.isEmpty()) {
      Messages.showErrorDialog(project, IdeBundle.message("jar.no.java.modules.in.project.error"),
                               IdeBundle.message("jar.no.java.modules.in.project.title"));
      return;
    }
    BuildJarDialog dialog = new BuildJarDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      BuildJarProjectSettings.getInstance(project).buildJarsWithProgress();
    }
  }

  public void update(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null);
  }
}
