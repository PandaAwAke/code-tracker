
package com.intellij.execution.actions;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import java.awt.*;

public class RunConfigurationAction extends ComboBoxAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.RunConfigurationAction");
  private static final Key<ComboBoxAction.ComboBoxButton> BUTTON_KEY = Key.create("COMBOBOX_BUTTON");

  public RunConfigurationAction() {
    super(ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX);
  }

  public void actionPerformed(final AnActionEvent e) {
    final IdeFrame ideFrame = findFrame(e.getDataContext());
    final ComboBoxAction.ComboBoxButton button = (ComboBoxAction.ComboBoxButton)ideFrame.getRootPane().getClientProperty(BUTTON_KEY);
    if (button == null || !button.isShowing()) return;
    button.showPopup();
  }

  private IdeFrame findFrame(final Component component) {
    return IJSwingUtilities.findParentOfType(component, IdeFrame.class);
  }

  private IdeFrame findFrame(final DataContext dataContext) {
    return findFrame((Component)dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT));
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      presentation.setDescription("Open run/debug configurations dropdown");
      presentation.setEnabled(findFrame(dataContext) != null);
      return;
    }

    if (project == null) {
      //if (ProjectManager.getInstance().getOpenProjects().length > 0) {
      //  // do nothing if frame is not active
      //  return;
      //}

      updateButton(null, null, presentation);
      presentation.setEnabled(false);
    }
    else {
      final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
      RunnerAndConfigurationSettings selected = runManager.getSelectedConfiguration();
      updateButton(selected == null? null : selected.getConfiguration(), project, presentation);
      presentation.setEnabled(true);
    }
  }

  private void updateButton(final RunConfiguration configuration, final Project project, final Presentation presentation) {
    if (project != null && configuration != null) {
      presentation.setText(getConfigurationDescription(configuration), false);
      setConfigurationIcon(presentation, configuration, project);
    }
    else {
      presentation.setText(" ");
      presentation.setIcon(null);
    }
  }

  private void setConfigurationIcon(final Presentation presentation, final RunConfiguration configuration, final Project project) {
    presentation.setIcon(ExecutionUtil.getConfigurationIcon(project, configuration));
  }

  public JComponent createCustomComponent(final Presentation presentation) {
    final ComboBoxAction.ComboBoxButton comboboxButton = new ComboBoxAction.ComboBoxButton(presentation) {
      protected void updateButtonSize() {
        super.updateButtonSize();
        final Dimension preferredSize = getPreferredSize();
        final int width = preferredSize.width;
        final int height = preferredSize.height;
        if (width > height * 15) {
          setPreferredSize(new Dimension(height * 15, height));
        }
      }

      public void addNotify() {
        super.addNotify();    //To change body of overriden methods use Options | File Templates.;
        final IdeFrame frame = findFrame(this);
        LOG.assertTrue(frame != null);
        frame.getRootPane().putClientProperty(BUTTON_KEY, this);
      }
    };
    return comboboxButton;
  }


  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    final DefaultActionGroup allActionsGroup = new DefaultActionGroup();
    final Project project = (Project)DataManager.getInstance().getDataContext(button).getData(DataConstants.PROJECT);
    if (project != null) {
      final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);

      final ConfigurationType[] types = runManager.getConfigurationFactories();
      for (int i = 0; i < types.length; i++) {
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        final RunnerAndConfigurationSettingsImpl[] configurations = runManager.getConfigurationSettings(types[i]);
        for (int j = 0; j < configurations.length; j++) {
          final RunnerAndConfigurationSettingsImpl configuration = configurations[j];
          //if (runManager.canRunConfiguration(configuration)) {
            final MenuAction action = new MenuAction(configuration, project);
            actionGroup.add(action);
          //}
        }
        allActionsGroup.add(actionGroup);
        allActionsGroup.addSeparator();
      }

      allActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_RUN_CONFIGURATIONS));
      allActionsGroup.add(new SaveTemporaryAction());
    }
    return allActionsGroup;
  }

  class SaveTemporaryAction extends AnAction {
    public void actionPerformed(final AnActionEvent e) {
      final RunManager runManager = RunManager.getInstance(getProject(e));
      runManager.makeStable(runManager.getTempConfiguration());
    }

    public void update(final AnActionEvent e) {
      final Presentation presentation = e.getPresentation();
      final Project project = getProject(e);
      if (project == null) {
        disable(presentation);
        return;
      }
      final RunConfiguration tempConfiguration = RunManager.getInstance(project).getTempConfiguration();
      if (tempConfiguration == null) {
        disable(presentation);
        return;
      }
      presentation.setText("&Save " + tempConfiguration.getName() + " Configuration");
      presentation.setVisible(true);
      presentation.setEnabled(true);
    }

    private Project getProject(final AnActionEvent e) {
      return (Project)e.getDataContext().getData(DataConstants.PROJECT);
    }

    private void disable(final Presentation presentation) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
  }

  class MenuAction extends AnAction {
    private RunnerAndConfigurationSettingsImpl myConfiguration;
    private Project myProject;

    public MenuAction(final RunnerAndConfigurationSettingsImpl configuration, final Project project) {
      myConfiguration = configuration;
      myProject = project;
      String description = getConfigurationDescription(configuration.getConfiguration());
      if (description == null || description.length() == 0) {
        description = " ";
      }
      final Presentation presentation = getTemplatePresentation();
      presentation.setText(description, false);
      updateIcon(presentation);
    }

    private void updateIcon(final Presentation presentation) {
      setConfigurationIcon(presentation, myConfiguration.getConfiguration(), myProject);
    }

    public void actionPerformed(final AnActionEvent e){
      RunManagerEx.getInstanceEx(myProject).setActiveConfiguration(myConfiguration);
      updateButton(myConfiguration.getConfiguration(), myProject, e.getPresentation());
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      updateIcon(e.getPresentation());
    }
  }

  private String getConfigurationDescription(final RunConfiguration configuration) {
    return configuration.getName();
  }
}
