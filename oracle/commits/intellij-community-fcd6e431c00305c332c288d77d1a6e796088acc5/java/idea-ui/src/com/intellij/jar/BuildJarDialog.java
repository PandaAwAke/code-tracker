package com.intellij.jar;

import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.deployment.ModuleLink;
import com.intellij.openapi.deployment.PackagingConfiguration;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditor;
import com.intellij.openapi.roots.ui.configuration.packaging.PackagingEditorImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.util.io.FileTypeFilter;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author cdr
 */
public class BuildJarDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.j2ee.jar.BuildJarDialog");
  @NonNls private static final String WARNING_TEMPLATE = "<html><body><b>{0}: </b>{1}</body><body>";
  private final Project myProject;
  private JPanel myModulesPanel;
  private JPanel myEditorPanel;
  private TextFieldWithBrowseButton myJarPath;
  private TextFieldWithBrowseButton myMainClass;
  private JPanel myPanel;
  private final Map<Module, SettingsEditor> mySettings = new THashMap<Module, SettingsEditor>();
  private Module myCurrentModule;
  private ElementsChooser<Module> myElementsChooser;
  private JPanel myModuleSettingsPanel;
  private LabeledComponent<TextFieldWithBrowseButton> myMainClassComponent;
  private LabeledComponent<TextFieldWithBrowseButton> myJarFilePathComponent;
  private JCheckBox myBuildJarsOnMake;
  private JLabel myWarningLabel;
  private final SplitterProportionsData mySplitterProportionsData = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();

  protected BuildJarDialog(Project project) {
    super(true);
    myProject = project;

    setupControls();

    setTitle(IdeBundle.message("jar.build.dialog.title"));
    mySplitterProportionsData.externalizeFromDimensionService(getDimensionKey());
    mySplitterProportionsData.restoreSplitterProportions(myPanel);
    getOKAction().putValue(Action.NAME, IdeBundle.message("jar.build.button"));
    init();
    updateWarning();
  }

  private void updateWarning() {
    for (Map.Entry<Module, SettingsEditor> entry : mySettings.entrySet()) {
      try {
        final SettingsEditor editor = entry.getValue();
        if (editor.myModule == myCurrentModule) {
          editor.saveUI();
        }
        editor.checkSettings();
      }
      catch (RuntimeConfigurationException e) {
        myWarningLabel.setText(MessageFormat.format(WARNING_TEMPLATE, entry.getKey().getName(), e.getMessage()));
        myWarningLabel.setVisible(true);
        repaint();
        return;
      }
    }
    repaint();
    myWarningLabel.setVisible(false);
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.generateJarFiles");
  }
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public static Collection<Module> getModulesToJar(Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    ArrayList<Module> result = new ArrayList<Module>();
    for (Module module : modules) {
      if (module.getModuleType() instanceof JavaModuleType) {
        result.add(module);
      }
    }
    Collections.sort(result, new Comparator<Module>() {
      public int compare(final Module o1, final Module o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    return result;

  }

  private void setupControls() {
    myWarningLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));

    myBuildJarsOnMake.setSelected(BuildJarProjectSettings.getInstance(myProject).isBuildJarOnMake());

    myJarPath = myJarFilePathComponent.getComponent();
    myMainClass = myMainClassComponent.getComponent();

    myElementsChooser = new ElementsChooser<Module>(true) {
      @Override
      protected String getItemText(@NotNull final Module value) {
        return value.getName();
      }
    };
    myElementsChooser.setColorUnmarkedElements(false);
    myModulesPanel.setLayout(new BorderLayout());
    myModulesPanel.add(myElementsChooser, BorderLayout.CENTER);

    final Collection<Module> modules = getModulesToJar(myProject);
    for (final Module module : modules) {
      BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(module);
      myElementsChooser.addElement(module, buildJarSettings.isBuildJar(), new ChooserElementProperties(module));
    }
    myElementsChooser.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (myCurrentModule != null) {
          saveEditor(myCurrentModule);
        }

        Module selectedModule = myElementsChooser.getSelectedElement();
        myCurrentModule = selectedModule;
        if (selectedModule != null) {
          BuildJarSettings buildJarSettings = BuildJarSettings.getInstance(selectedModule);
          SettingsEditor settingsEditor = mySettings.get(selectedModule);
          if (settingsEditor == null) {
            settingsEditor = new SettingsEditor(selectedModule, buildJarSettings);
            mySettings.put(selectedModule, settingsEditor);
          }
          settingsEditor.refreshControls();
          boolean isBuildJar = myElementsChooser.getMarkedElements().contains(selectedModule);
          GuiUtils.enableChildren(myModuleSettingsPanel, isBuildJar);
          settingsEditor.rebuildTree();
          TitledBorder titledBorder = (TitledBorder)myModuleSettingsPanel.getBorder();
          titledBorder.setTitle(IdeBundle.message("jar.build.module.0.jar.settings", selectedModule.getName()));
          myModuleSettingsPanel.repaint();
        }
      }
    });
    myElementsChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<Module>() {
      public void elementMarkChanged(final Module element, final boolean isMarked) {
        GuiUtils.enableChildren(myModuleSettingsPanel, isMarked);
        SettingsEditor settingsEditor = mySettings.get(element);
        if (isMarked) {
          setDefaultJarPath();
          if (settingsEditor != null) {
            PackagingConfiguration configuration = settingsEditor.getEditor().getModifiedConfiguration();
            if (configuration.getElements().length == 0) {
              ModuleLink moduleLink = DeploymentUtil.getInstance().createModuleLink(element, element);
              moduleLink.setPackagingMethod(PackagingMethod.COPY_FILES);
              moduleLink.setURI("/");
              configuration.addOrReplaceElement(moduleLink);
            }
          }
        }

        if (settingsEditor != null) {
          settingsEditor.rebuildTree();
        }
      }
    });
    myJarPath.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String lastFilePath = myJarPath.getText();
        String path = lastFilePath == null ? RecentProjectsManager.getInstance().getLastProjectPath() : lastFilePath;
        File file = new File(path);
        if (!file.exists()) {
          path = file.getParent();
        }
        JFileChooser fileChooser = new JFileChooser(path);
        FileView fileView = new FileView() {
          public Icon getIcon(File f) {
            if (f.isDirectory()) return super.getIcon(f);
            FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
            return fileType.getIcon();
          }
        };
        fileChooser.setFileView(fileView);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setDialogTitle(IdeBundle.message("jar.build.save.title"));
        fileChooser.addChoosableFileFilter(new FileTypeFilter(FileTypes.ARCHIVE));

        if (fileChooser.showSaveDialog(WindowManager.getInstance().suggestParentWindow(myProject)) != JFileChooser.APPROVE_OPTION) {
          return;
        }
        file = fileChooser.getSelectedFile();
        if (file == null) return;
        myJarPath.setText(file.getPath());
      }
    });

    myMainClass.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String mainClass = myMainClass.getText();
        GlobalSearchScope scope = createMainClassScope();
        PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(mainClass, scope);
        TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);
        final TreeClassChooser dialog =
          factory.createNoInnerClassesScopeChooser(IdeBundle.message("jar.build.main.class.title"), scope, null, aClass);
        dialog.showDialog();
        final PsiClass psiClass = dialog.getSelectedClass();
        if (psiClass != null && psiClass.getQualifiedName() != null) {
          myMainClass.setText(psiClass.getQualifiedName());
        }
        updateWarning();
      }
    });
    myMainClass.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateWarning();
      }
    });

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Module element = myElementsChooser.getElementAt(0);
        myElementsChooser.selectElements(Collections.singletonList(element));
      }
    });
    GuiUtils.replaceJSplitPaneWithIDEASplitter(myPanel);
  }

  private GlobalSearchScope createMainClassScope() {
    GlobalSearchScope result = null;
    for (Module module : mySettings.keySet()) {
      GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(module);
      if (result == null) {
        result = scope;
      }
      else {
        result = result.uniteWith(scope);
      }
    }
    return result != null ? result : GlobalSearchScope.allScope(myProject);
  }

  public JComponent getPreferredFocusedComponent() {
    return myJarPath;
  }

  private void setDefaultJarPath() {
    if (!Comparing.strEqual(myJarPath.getText(), "") || myCurrentModule == null) {
      return;
    }
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(myCurrentModule).getContentRoots();
    if (contentRoots.length == 0) return;
    VirtualFile contentRoot = contentRoots[0];
    if (contentRoot == null) return;
    VirtualFile moduleFile = myCurrentModule.getModuleFile();
    if (moduleFile == null) return;
    String jarPath = FileUtil.toSystemDependentName(contentRoot.getPath() + "/" + moduleFile.getNameWithoutExtension() + ".jar");
    myJarPath.setText(jarPath);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.j2ee.jar.BuildJarDialog";
  }

  public void dispose() {
    mySplitterProportionsData.saveSplitterProportions(myPanel);
    mySplitterProportionsData.externalizeToDimensionService(getDimensionKey());
    for (SettingsEditor editor : mySettings.values()) {
      editor.dispose();
    }
    super.dispose();
  }

  protected void doOKAction() {
    if (myCurrentModule != null) {
      saveEditor(myCurrentModule);
    }
    for (SettingsEditor editor : mySettings.values()) {
      editor.apply();
    }
    BuildJarProjectSettings.getInstance(myProject).setBuildJarOnMake(myBuildJarsOnMake.isSelected());
    super.doOKAction();
  }

  private void saveEditor(final Module module) {
    SettingsEditor settingsEditor = mySettings.get(module);
    if (settingsEditor != null) {
      settingsEditor.saveUI();
    }
  }

  private class SettingsEditor {
    private final Module myModule;
    private final BuildJarSettings myBuildJarSettings;
    private final BuildJarSettings myModifiedBuildJarSettings;
    private final PackagingEditor myEditor;

    public SettingsEditor(@NotNull Module module, @NotNull BuildJarSettings buildJarSettings) {
      myModule = module;
      myBuildJarSettings = buildJarSettings;

      myModifiedBuildJarSettings = new BuildJarSettings(module);
      copySettings(buildJarSettings, myModifiedBuildJarSettings);

      DefaultModulesProvider modulesProvider = new DefaultModulesProvider(myProject);
      PackagingConfiguration originalConfiguration = myBuildJarSettings.getPackagingConfiguration();
      PackagingConfiguration modifiedConfiguration = myModifiedBuildJarSettings.getPackagingConfiguration();
      JarPackagingEditorPolicy editorPolicy = new JarPackagingEditorPolicy(module);
      JarPackagingTreeBuilder treeBuilder = new JarPackagingTreeBuilder(module);
      myEditor = new PackagingEditorImpl(originalConfiguration, modifiedConfiguration, modulesProvider, DefaultFacetsProvider.INSTANCE,
                                         editorPolicy, treeBuilder, false);
      myEditor.reset();
      myEditor.createMainComponent();
    }

    private void refreshControls() {
      myEditorPanel.removeAll();
      myEditorPanel.setLayout(new BorderLayout());
      myEditorPanel.add(myEditor.getMainPanel(), BorderLayout.CENTER);
      myEditorPanel.revalidate();

      myJarPath.setText(FileUtil.toSystemDependentName(VfsUtil.urlToPath(myModifiedBuildJarSettings.getJarUrl())));
      myMainClass.setText(myModifiedBuildJarSettings.getMainClass());
    }

    public void dispose() {
    }

    public void apply() {
      copySettings(myModifiedBuildJarSettings, myBuildJarSettings);
    }

    public void rebuildTree() {
      getEditor().rebuildTree();
    }

    public PackagingEditor getEditor() {
      return myEditor;
    }

    public void saveUI() {
      myEditor.saveData();
      String url = VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(myJarPath.getText()));
      myModifiedBuildJarSettings.setJarUrl(url);
      boolean isBuildJar = myElementsChooser.getMarkedElements().contains(myModule);
      myModifiedBuildJarSettings.setBuildJar(isBuildJar);
      myModifiedBuildJarSettings.setMainClass(myMainClass.getText());
    }


    public void checkSettings() throws RuntimeConfigurationException {
      myModifiedBuildJarSettings.checkSettings();
    }
  }

  private static void copySettings(BuildJarSettings from, BuildJarSettings to) {
    @NonNls Element element = new Element("dummy");
    try {
      from.writeExternal(element);
    }
    catch (WriteExternalException ignored) {
    }
    try {
      to.readExternal(element);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private static class ChooserElementProperties implements ElementsChooser.ElementProperties {
    private final Module myModule;

    public ChooserElementProperties(final Module module) {
      myModule = module;
    }

    public Icon getIcon() {
      return myModule.getModuleType().getNodeIcon(false);
    }

    public Color getColor() {
      return null;
    }
  }
}
