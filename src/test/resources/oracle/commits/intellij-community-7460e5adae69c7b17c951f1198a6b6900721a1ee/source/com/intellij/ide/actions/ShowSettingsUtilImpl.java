package com.intellij.ide.actions;

import com.intellij.application.options.CodeStyleSchemesConfigurable;
import com.intellij.application.options.ProjectCodeStyleConfigurable;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ControlPanelSettingsEditor;
import com.intellij.openapi.options.ex.ExplorerSettingsEditor;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.awt.*;

/**
 * @author max
 */
public class ShowSettingsUtilImpl extends ShowSettingsUtil implements ApplicationComponent {
  private static final String PREFER_CLASSIC_OPTIONS_EDITOR = "PREFER_CLASSIC_OPTIONS_EDITOR";

  public void showSettingsDialog(Project project, ConfigurableGroup[] group) {
    if ("true".equals(PropertiesComponent.getInstance().getValue(PREFER_CLASSIC_OPTIONS_EDITOR))) {
      showExplorerOptions(project, group);
    }
    else {
      showControlPanelOptions(project, group, null);
    }
  }

  public void showControlPanelOptions(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    PropertiesComponent.getInstance().setValue(PREFER_CLASSIC_OPTIONS_EDITOR, "false");

    ControlPanelSettingsEditor editor = new ControlPanelSettingsEditor(project, groups, preselectedConfigurable);
    editor.show();
  }

  public void showExplorerOptions(Project project, ConfigurableGroup[] group) {
    PropertiesComponent.getInstance().setValue(PREFER_CLASSIC_OPTIONS_EDITOR, "true");
    ExplorerSettingsEditor editor = new ExplorerSettingsEditor(project, group);
    editor.show();
  }

  public boolean editConfigurable(Project project, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(project, configurable);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Project project, String dimensionServiceKey, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(project, configurable, dimensionServiceKey);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Component parent, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(parent, configurable);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Component parent, String dimensionServiceKey,Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(parent, configurable, dimensionServiceKey);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization) {
    SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable);
    advancedInitialization.run();
    editor.show();
    return editor.isOK();
  }

  /**
   * Shows code style settings sutable for the project passed. I.e. it shows project code style page if one
   * is configured to use own code style scheme or global one in other case.
   * @param project
   * @return Returns true if settings were modified during editing session.
   */
  public boolean showCodeStyleSettings(Project project, final Class pageToSelect) {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    boolean usePerProject = settingsManager.USE_PER_PROJECT_SETTINGS;
    CodeStyleSettings savedSettings = (CodeStyleSettings)settingsManager.getCurrentSettings().clone();
    if (usePerProject) {
      final ProjectCodeStyleConfigurable configurable = ProjectCodeStyleConfigurable.getInstance(project);
      Runnable selectPage = new Runnable() {
        public void run() {
          if (pageToSelect != null) {
            configurable.selectPage(pageToSelect);
          }
        }
      };
      editConfigurable(project, configurable, selectPage);
    }
    else {
      final CodeStyleSchemesConfigurable configurable = CodeStyleSchemesConfigurable.getInstance();
      Runnable selectPage = new Runnable() {
        public void run() {
          if (pageToSelect != null) {
            configurable.selectPage(pageToSelect);
          }
        }
      };
      editConfigurable(project, configurable, selectPage);
    }

    return !savedSettings.equals(settingsManager.getCurrentSettings());
  }

  public String getComponentName() {
    return "ShowSettingsUtil";
  }

  public void initComponent() {}
  public void disposeComponent() {}
}
