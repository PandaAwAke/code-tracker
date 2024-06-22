package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.components.LoadCancelledException;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 5, 2004
 */
public class NewModuleAction extends AnAction {
  public NewModuleAction() {
    super(ProjectBundle.message("module.new.action"), ProjectBundle.message("module.new.action.description"), null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = getProject(e);
    if (project == null) {
      return;
    }
    final AddModuleWizard wizard = new AddModuleWizard(project, new ModulesProvider() {
      public Module[] getModules() {
        return ModuleManager.getInstance(project).getModules();
      }

      public Module getModule(String name) {
        return ModuleManager.getInstance(project).findModuleByName(name);
      }

      public ModuleRootModel getRootModel(Module module) {
        return ModuleRootManager.getInstance(module);
      }
    });

    wizard.show();

    if (wizard.isOK()) {
      final ModuleBuilder moduleBuilder = wizard.getModuleBuilder();
      Exception ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
        public Exception compute() {
          try {
            final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
            final Module module = moduleBuilder.createModule(moduleModel);
            if (module != null) {
              moduleModel.commitAssertingNoCircularDependency();
            }
            return null;
          }
          catch (Exception e) {
            return e;
          }
        }
      });
      if (ex != null) {
        if (ex instanceof LoadCancelledException) {
          LoadCancelledException cancelled = (LoadCancelledException)ex;
          Messages.showInfoMessage(
            ProjectBundle.message("module.new.creation.cancelled.message", cancelled.getIssuer().getComponentName(), cancelled.getMessage()),
            ProjectBundle.message("module.new.creation.cancelled.title"));
        } else {
          Messages.showErrorDialog(ProjectBundle.message("module.new.error.message", ex.getMessage()),
                                   ProjectBundle.message("module.new.error.title"));
        }
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(getProject(e) != null);
  }

  private Project getProject(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    return project;
  }
}
