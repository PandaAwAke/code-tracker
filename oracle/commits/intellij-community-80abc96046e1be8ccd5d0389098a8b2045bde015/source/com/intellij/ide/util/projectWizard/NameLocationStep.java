package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.FieldPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 29, 2003
 */
public class NameLocationStep extends ModuleWizardStep {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.NameLocationStep");
  private JPanel myPanel;
  private NamePathComponent myNamePathComponent;
  private final WizardContext myWizardContext;
  private final JavaModuleBuilder myBuilder;
  private final ModulesProvider myModulesProvider;
  private final Icon myIcon;
  private final String myHelpId;
  private static final String MODULE_FILE_EXTENSION = ".iml";
  private boolean myModuleFileDirectoryChangedByUser = false;
  private JTextField myTfModuleFilePath;
  private boolean myFirstTimeInitializationDone = false;

  public NameLocationStep(WizardContext wizardContext, JavaModuleBuilder builder,
                          ModulesProvider modulesProvider,
                          Icon icon,
                          String helpId) {
    myWizardContext = wizardContext;
    myBuilder = builder;
    myModulesProvider = modulesProvider;
    myIcon = icon;
    myHelpId = helpId;
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    final String text;
    final ModuleType moduleType = builder.getModuleType();
    if (ModuleType.J2EE_APPLICATION.equals(moduleType)) {
      text = "Please specify module name";
    }
    else {
      text = "Please specify module name and module content root.\nA module content root is a directory where the files that belong to the module are stored.";
    }
    final JLabel textLabel = new JLabel(text);
    textLabel.setUI(new MultiLineLabelUI());
    myPanel.add(textLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(8, 10, 8, 10), 0, 0));

    myNamePathComponent = new NamePathComponent("Module name:", "Module content root:", 'M', 'r', "Select Module Content Root", "");
    if (ModuleType.J2EE_APPLICATION.equals(moduleType)) {
      myNamePathComponent.setPathComponentVisible(false);
    }

    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(8, 10, 0, 10), 0, 0));

    final JLabel label = new JLabel("Module file will be saved in:");
    //label.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
    myPanel.add(label, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.SOUTHWEST, GridBagConstraints.HORIZONTAL, new Insets(30, 10, 0, 10), 0, 0));

    myTfModuleFilePath = new JTextField();
    myTfModuleFilePath.setEditable(false);
    final Insets borderInsets = myTfModuleFilePath.getBorder().getBorderInsets(myTfModuleFilePath);
    myTfModuleFilePath.setBorder(BorderFactory.createEmptyBorder(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right));
    final FieldPanel fieldPanel = createFieldPanel(myTfModuleFilePath, null, null);
    myPanel.add(fieldPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 10, 10, 0), 0, 0));

    JButton browseButton = new JButton("Change Directory...");
    browseButton.addActionListener(new BrowseModuleFileDirectoryListener(myTfModuleFilePath));
    myPanel.add(browseButton, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 10, 10), 0, 0));

    final DocumentListener documentListener = new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateModuleFilePathField();
      }
    };
    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(documentListener);
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(documentListener);
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        myWizardContext.requestWizardButtonsUpdate();
      }
    });
  }

  private String suggestModuleName(ModuleType moduleType) {
    if (moduleType.equals(ModuleType.J2EE_APPLICATION)) {
      return "MyApplication";
    }
    if (moduleType.equals(ModuleType.EJB)) {
      return "MyEjb";
    }
    if (moduleType.equals(ModuleType.WEB)) {
      return "MyWebApp";
    }
    return "untitled";
  }

  public void updateStep() {
    super.updateStep();

    // initial UI settings
    if (!myFirstTimeInitializationDone) {
      if (myWizardContext.isCreatingNewProject()) {
        setSyncEnabled(false);
      }
      else { // project already exists
        final VirtualFile projectFile = myWizardContext.getProject().getProjectFile();
        final String projectFileDirectory = (projectFile != null) ? VfsUtil.virtualToIoFile(projectFile).getParent() : null;
        if (projectFileDirectory != null) {
          final String name = ProjectWizardUtil.findNonExistingFileName(projectFileDirectory, suggestModuleName(myBuilder.getModuleType()), "");
          setModuleName(name);
          setContentEntryPath(projectFileDirectory + File.separatorChar + name);
        }
      }
    }

    if (myWizardContext.isCreatingNewProject()) { // creating new project
      // in this mode we depend on the settings of the "project name" step
      if (!isPathChangedByUser()) {
        setContentEntryPath(myWizardContext.getProjectFileDirectory().replace('/', File.separatorChar));
      }
      if (!isNameChangedByUser()) {
        setModuleName(myWizardContext.getProjectName());
      }
    }

    if (!myFirstTimeInitializationDone) {
      myNamePathComponent.getNameComponent().selectAll();
    }
    myFirstTimeInitializationDone = true;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public String getModuleName() {
    return myNamePathComponent.getNameValue();
  }

  public void setModuleName(String name) {
    myNamePathComponent.setNameValue(name);
  }

  public String getContentEntryPath() {
    final String path = myNamePathComponent.getPath();
    return path.length() == 0? null : path;
  }

  public void setContentEntryPath(String path) {
    myNamePathComponent.setPath(path);
  }

  public String getModuleFilePath() {
    return getModuleFileDirectory() + "/" + myNamePathComponent.getNameValue() + MODULE_FILE_EXTENSION;
  }

  public boolean isSyncEnabled() {
    return myNamePathComponent.isSyncEnabled();
  }

  public void setSyncEnabled(boolean isSyncEnabled) {
    myNamePathComponent.setSyncEnabled(isSyncEnabled);
  }

  private String getModuleFileDirectory() {
    return myTfModuleFilePath.getText().trim().replace(File.separatorChar, '/');
  }

  public boolean validate() {
    final String moduleName = getModuleName();
    if (moduleName.length() == 0) {
      Messages.showErrorDialog(myNamePathComponent.getNameComponent(), "Please specify module name", "Module Name Not Specified");
      return false;
    }
    if (isAlreadyExists(moduleName)) {
      Messages.showErrorDialog("Module with name \"" + moduleName + "\" already exists in the project", "Module Already Exists");
      return false;
    }
    final String moduleLocation = getModuleFileDirectory();
    if (moduleLocation.length() == 0) {
      Messages.showErrorDialog(myNamePathComponent.getPathComponent(), "Please specify module file location", "Module File Location Not Specified");
      return false;
    }

    if (!ModuleType.J2EE_APPLICATION.equals(myBuilder.getModuleType())) {
      final String contentEntryPath = getContentEntryPath();
      if (contentEntryPath != null) {
        // the check makes sence only for non-null module root
        Module[] modules = myModulesProvider.getModules();
        for (int j = 0; j < modules.length; j++) {
          final Module module = modules[j];
          ModuleRootModel rootModel = myModulesProvider.getRootModel(module);
          LOG.assertTrue(rootModel != null);
          final VirtualFile[] moduleContentRoots = rootModel.getContentRoots();
          final String moduleContentRoot = contentEntryPath.replace(File.separatorChar, '/');
          for (int k = 0; k < moduleContentRoots.length; k++) {
            final VirtualFile root = moduleContentRoots[k];
            if (moduleContentRoot.equals(root.getPath())) {
              Messages.showErrorDialog(myNamePathComponent.getPathComponent(), "Content root \"" + contentEntryPath + "\" already defined for module \"" + module.getName() + "\".\nTwo modules in a project cannot share the same content root.", "Module Content Root Already Exists");
              return false;
            }
          }
        }
        if (!ProjectWizardUtil.createDirectoryIfNotExists("The module content root\n", contentEntryPath, myNamePathComponent.isPathChangedByUser())) {
          return false;
        }
      }
    }
    final String moduleFileDirectory = getModuleFileDirectory();
    if (!ProjectWizardUtil.createDirectoryIfNotExists("The module file directory\n", moduleFileDirectory, myModuleFileDirectoryChangedByUser)) {
      return false;
    }
    return true;
  }

  public void updateDataModel() {
    myBuilder.setName(getModuleName());
    myBuilder.setModuleFilePath(getModuleFilePath());
    myBuilder.setContentEntryPath(getContentEntryPath());
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  private boolean isAlreadyExists(String moduleName) {
    final Module[] modules = myModulesProvider.getModules();
    for (int idx = 0; idx < modules.length; idx++) {
      Module module = modules[idx];
      if (moduleName.equals(module.getName())) {
        return true;
      }
    }
    return false;
  }

  private void updateModuleFilePathField() {
    if (!myModuleFileDirectoryChangedByUser) {
      final String dir = myNamePathComponent.getPath().replace('/', File.separatorChar);
      myTfModuleFilePath.setText(dir);
    }
  }

  public boolean isNameChangedByUser() {
    return myNamePathComponent.isNameChangedByUser();
  }

  public boolean isPathChangedByUser() {
    return myNamePathComponent.isPathChangedByUser();
  }

  private class BrowseModuleFileDirectoryListener extends BrowseFilesListener {
    public BrowseModuleFileDirectoryListener(final JTextField textField) {
      super(textField, "Select Module File Location", "The module file will be saved in selected directory", BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    }

    public void actionPerformed(ActionEvent e) {
      final String pathBefore = myTfModuleFilePath.getText().trim();
      super.actionPerformed(e);
      final String path = myTfModuleFilePath.getText().trim();
      if (!path.equals(pathBefore)) {
        myModuleFileDirectoryChangedByUser = true;
      }
    }
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getHelpId() {
    return myHelpId;
  }
}
