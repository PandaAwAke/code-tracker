package com.intellij.openapi.projectRoots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;

import org.jetbrains.annotations.NonNls;

/*
 * @author: MYakovlev
 * Date: Aug 15, 2002
 * Time: 1:27:59 PM
 */

public class SdkEditor implements Configurable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.ui.SdkEditor");
  private ProjectJdk mySdk;
  private PathEditor myClassPathEditor;
  private PathEditor mySourcePathEditor;
  private PathEditor myJavadocPathEditor;

  private TextFieldWithBrowseButton myHomeComponent;
  private Map<SdkType, AdditionalDataConfigurable> myAdditionalDataConfigurables = new HashMap<SdkType, AdditionalDataConfigurable>();
  private Map<AdditionalDataConfigurable, JComponent> myAdditionalDataComponents = new HashMap<AdditionalDataConfigurable, JComponent>();
  private EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);
  private JPanel myAdditionalDataPanel;
  private SdkModificator myEditedSdkModificator = new EditedSdkModificator();

  // GUI components
  private JPanel myMainPanel;
  private JTextField myNameField;
  private TabbedPaneWrapper myTabbedPane;
  private final NotifiableSdkModel mySdkModel;
  private JLabel myHomeFieldLabel;
  private String myVersionString;
  @NonNls private static final String MAC_HOME_PATH = "/Home";

  public SdkEditor(NotifiableSdkModel sdkModel) {
    mySdkModel = sdkModel;
  }

  public ProjectJdk getEditedSdk(){
    return mySdk;
  }

  public void setSdk(ProjectJdk sdk){
    mySdk = sdk;
    final AdditionalDataConfigurable additionalDataConfigurable = getAdditionalDataConfigurable();
    if (additionalDataConfigurable != null) {
      additionalDataConfigurable.setSdk(sdk);
    }
    if (myMainPanel != null){
      reset();
    }
  }

  public String getDisplayName(){
    return ProjectBundle.message("sdk.configure.editor.title");
  }

  public Icon getIcon(){
    return null;
  }

  public String getHelpTopic(){
    return null;
  }

  public JComponent createComponent(){
    createMainPanel();
    return myMainPanel;
  }

  public JComponent getComponent(){
    return myMainPanel;
  }

  private void createMainPanel(){
    myClassPathEditor = new MyPathsEditor(ProjectBundle.message("sdk.configure.classpath.tab"), ProjectRootType.CLASS, new FileChooserDescriptor(true, true, true, false, true, true), false);
    mySourcePathEditor = new MyPathsEditor(ProjectBundle.message("sdk.configure.sourcepath.tab"), ProjectRootType.SOURCE, new FileChooserDescriptor(true, true, true, false, true, true), false);
    myJavadocPathEditor = new MyPathsEditor(ProjectBundle.message("sdk.configure.javadoc.tab"), ProjectRootType.JAVADOC, new FileChooserDescriptor(false, true, true, false, true, true), true);

    myMainPanel = new JPanel(new GridBagLayout());
    myNameField = new JTextField();

    JLabel nameLabel = new JLabel(ProjectBundle.message("sdk.configure.name.label"));
    nameLabel.setLabelFor(myNameField);
    myMainPanel.add(nameLabel,   new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 2, 2), 0, 0));
    myMainPanel.add(myNameField, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 2, 2, 0), 0, 0));

    myTabbedPane = new TabbedPaneWrapper();
    myTabbedPane.addTab(myClassPathEditor.getDisplayName(), myClassPathEditor.createComponent());
    myTabbedPane.addTab(mySourcePathEditor.getDisplayName(), mySourcePathEditor.createComponent());
    myTabbedPane.addTab(myJavadocPathEditor.getDisplayName(), myJavadocPathEditor.createComponent());

    myHomeComponent = new TextFieldWithBrowseButton(new ActionListener(){
      public void actionPerformed(ActionEvent e){
        doSelectHomePath();
      }
    });
    myHomeComponent.getTextField().setEditable(false);

    myHomeFieldLabel = new JLabel(getHomeFieldLabelValue());
    myMainPanel.add(myHomeFieldLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(2, 0, 2, 2), 0, 0));
    myMainPanel.add(myHomeComponent, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 0), 0, 0));

    myAdditionalDataPanel = new JPanel(new BorderLayout());
    myMainPanel.add(myAdditionalDataPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));

    myMainPanel.add(myTabbedPane.getComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        myEventDispatcher.getMulticaster().stateChanged(new ChangeEvent(SdkEditor.this));
      }
    });
  }

  private String getHomeFieldLabelValue() {
    if (mySdk != null) {
      return ProjectBundle.message("sdk.configure.type.home.path", mySdk.getSdkType().getPresentableName());
    }
    return ProjectBundle.message("sdk.configure.general.home.path");
  }

  public boolean isModified(){
    boolean isModified = false;
    final String initialName = (mySdk == null) ? "" : mySdk.getName();
    final String initialHome = (mySdk == null) ? "" : mySdk.getHomePath();
    isModified = isModified || !Comparing.equal(getNameValue(), initialName);
    isModified = isModified || !Comparing.equal(getHomeValue().replace(File.separatorChar, '/'), initialHome);
    isModified = isModified || myClassPathEditor.isModified();
    isModified = isModified || mySourcePathEditor.isModified();
    isModified = isModified || myJavadocPathEditor.isModified();
    final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
    if (configurable != null) {
      isModified = isModified || configurable.isModified();
    }
    return isModified;
  }

  public void apply() throws ConfigurationException{
    final String currName = getNameValue();
    if(!Comparing.equal(currName, (mySdk == null) ? "" : mySdk.getName())){
      if(currName.length() == 0){
        ApplicationManager.getApplication().invokeLater(new Runnable(){
          public void run(){
            focusToNameField();
          }
        });
        throw new ConfigurationException(ProjectBundle.message("sdk.list.name.required.error"));
      }
    }
    if (mySdk != null){
      final SdkModificator sdkModificator = mySdk.getSdkModificator();
      sdkModificator.setName(getNameValue());
      sdkModificator.setHomePath(getHomeValue().replace(File.separatorChar, '/'));
      myClassPathEditor.apply(sdkModificator);
      mySourcePathEditor.apply(sdkModificator);
      myJavadocPathEditor.apply(sdkModificator);
      ApplicationManager.getApplication().runWriteAction(new Runnable() { // fix SCR #29193
        public void run() {
          sdkModificator.commitChanges();
        }
      });
      final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
      if (configurable != null) {
        configurable.apply();
      }
    }
  }

  public void reset(){
    if (mySdk == null){
      setNameValue("");
      setHomePathValue("");
      myClassPathEditor.reset(null);
      mySourcePathEditor.reset(null);
      myJavadocPathEditor.reset(null);
    }
    else{
      final SdkModificator sdkModificator = mySdk.getSdkModificator();
      myClassPathEditor.reset(sdkModificator.getRoots(myClassPathEditor.getRootType()));
      mySourcePathEditor.reset(sdkModificator.getRoots(mySourcePathEditor.getRootType()));
      myJavadocPathEditor.reset(sdkModificator.getRoots(myJavadocPathEditor.getRootType()));
      sdkModificator.commitChanges();
      setNameValue(mySdk.getName());
      setHomePathValue(mySdk.getHomePath().replace('/', File.separatorChar));
    }
    myVersionString = null;
    myHomeFieldLabel.setText(getHomeFieldLabelValue());
    updateAdditionalDataComponent();
    final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
    if (configurable != null) {
      configurable.reset();
    }

    myNameField.setEnabled(mySdk != null);
    myHomeComponent.setEnabled(mySdk != null);

    for(int i = 0; i < myTabbedPane.getTabCount(); i++){
      myTabbedPane.setEnabledAt(i, mySdk != null);
    }
  }

  public void disposeUIResources(){
    myMainPanel = null;
    for (Iterator it = myAdditionalDataConfigurables.keySet().iterator(); it.hasNext();) {
      final SdkType sdkType = (SdkType)it.next();
      final AdditionalDataConfigurable configurable = myAdditionalDataConfigurables.get(sdkType);
      configurable.disposeUIResources();
    }
    myAdditionalDataConfigurables.clear();
    myAdditionalDataComponents.clear();
  }

  public void focusToNameField(){
    myNameField.requestFocus();
  }

  public String getNameValue(){
    return myNameField.getText().trim();
  }

  private String getHomeValue() {
    return myHomeComponent.getText().trim();
  }

  public void setNameValue(String value){
    myNameField.setText(value);
  }

  public void addChangeListener(ChangeListener listener){
    myEventDispatcher.addListener(listener);
  }

  public void removeChangeListener(ChangeListener listener){
    myEventDispatcher.removeListener(listener);
  }

  public void clearAllPaths(){
    myClassPathEditor.clearList();
    mySourcePathEditor.clearList();
    myJavadocPathEditor.clearList();
  }

  private void setHomePathValue(String absolutePath) {
    myHomeComponent.setText(absolutePath);
    final Color fg;
    if (absolutePath != null && absolutePath.length() > 0) {
      final File homeDir = new File(absolutePath);
      fg = homeDir.isDirectory() && homeDir.exists()? UIUtil.getFieldForegroundColor() : PathEditor.INVALID_COLOR;
    }
    else {
      fg = UIUtil.getFieldForegroundColor();
    }
    myHomeComponent.getTextField().setForeground(fg);
  }

  public static String selectSdkHome(final Component parentComponent, final SdkType sdkType){
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (files.length != 0){
          boolean valid = sdkType.isValidSdkHome(files[0].getPath());
          if (!valid){
            if (SystemInfo.isMac) {
              valid = sdkType.isValidSdkHome(files[0].getPath() + MAC_HOME_PATH);
            }
            if (!valid) {
              throw new Exception(ProjectBundle.message("sdk.configure.home.invalid.error", sdkType.getPresentableName()));
            }
          }
        }
      }
    };
    descriptor.setTitle(ProjectBundle.message("sdk.configure.home.title", sdkType.getPresentableName()));
    VirtualFile[] files = FileChooser.chooseFiles(parentComponent, descriptor, getSuggestedSdkRoot(sdkType));
    if (files.length != 0){
      final String path = files[0].getPath();
      if (sdkType.isValidSdkHome(path)) return path;
      return SystemInfo.isMac && sdkType.isValidSdkHome(path + MAC_HOME_PATH) ? path + MAC_HOME_PATH : null;
    }
    return null;
  }

  public static VirtualFile getSuggestedSdkRoot(SdkType sdkType) {
    final String homepath = sdkType.suggestHomePath();
    if (homepath == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(homepath);
  }

  private class MyPathsEditor extends PathEditor {
    private final String myDisplayName;
    private final ProjectRootType myRootType;
    private final FileChooserDescriptor myDescriptor;
    private final boolean myCanAddUrl;

    public MyPathsEditor(String displayName, ProjectRootType rootType, FileChooserDescriptor descriptor, boolean canAddUrl) {
      myDisplayName = displayName;
      myRootType = rootType;
      myDescriptor = descriptor;
      myCanAddUrl = canAddUrl;
    }

    protected ProjectRootType getRootType() {
      return myRootType;
    }

    protected FileChooserDescriptor createFileChooserDescriptor() {
      return myDescriptor;
    }

    public String getDisplayName() {
      return myDisplayName;
    }

    protected boolean isShowUrlButton() {
      return myCanAddUrl;
    }
  }

  private void doSelectHomePath(){
    final SdkType sdkType = mySdk.getSdkType();
    final String homePath = selectSdkHome(myHomeComponent, sdkType);
    doSetHomePath(homePath, sdkType);
  }

  private void doSetHomePath(final String homePath, final SdkType sdkType) {
    if (homePath == null){
      return;
    }
    setHomePathValue(homePath.replace('/', File.separatorChar));

    final String newSdkName = suggestSdkName(homePath);
    setNameValue(newSdkName);

    try {
      final ProjectJdk dummySdk = (ProjectJdk)mySdk.clone();
      SdkModificator sdkModificator = dummySdk.getSdkModificator();
      sdkModificator.setHomePath(homePath);
      sdkModificator.removeAllRoots();
      sdkModificator.commitChanges();

      sdkType.setupSdkPaths(dummySdk);

      clearAllPaths();
      myVersionString = dummySdk.getVersionString();
      sdkModificator = dummySdk.getSdkModificator();
      myClassPathEditor.addPaths(sdkModificator.getRoots(myClassPathEditor.getRootType()));
      mySourcePathEditor.addPaths(sdkModificator.getRoots(mySourcePathEditor.getRootType()));
      myJavadocPathEditor.addPaths(sdkModificator.getRoots(myJavadocPathEditor.getRootType()));

      mySdkModel.getMulticaster().sdkHomeSelected(mySdk, homePath);
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e); // should not happen in normal program
    }
  }

  private String suggestSdkName(final String homePath) {
    final String suggestedName = mySdk.getSdkType().suggestSdkName(getNameValue(), homePath);
    String newSdkName = suggestedName;
    final Set<String> allNames = new HashSet<String>();
    Sdk[] sdks = mySdkModel.getSdks();
    for (int idx = 0; idx < sdks.length; idx++) {
      allNames.add(sdks[idx].getName());
    }
    int i = 0;
    while(allNames.contains(newSdkName)){
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    return newSdkName;
  }

  private void updateAdditionalDataComponent() {
    myAdditionalDataPanel.removeAll();
    final AdditionalDataConfigurable configurable = getAdditionalDataConfigurable();
    if (configurable != null) {
      JComponent component = myAdditionalDataComponents.get(configurable);
      if (component == null) {
        component = configurable.createComponent();
        myAdditionalDataComponents.put(configurable, component);
      }      
      myAdditionalDataPanel.add(component, BorderLayout.CENTER);
    }
  }

  private AdditionalDataConfigurable getAdditionalDataConfigurable() {
    if (mySdk == null) {
      return null;
    }
    return initAdditionalDataConfigurable(mySdk);
  }

  public AdditionalDataConfigurable initAdditionalDataConfigurable(Sdk sdk) {
    final SdkType sdkType = sdk.getSdkType();
    AdditionalDataConfigurable configurable = myAdditionalDataConfigurables.get(sdkType);
    if (configurable == null) {
      configurable = sdkType.createAdditionalDataConfigurable(mySdkModel, myEditedSdkModificator);
      if (configurable != null) {
        myAdditionalDataConfigurables.put(sdkType, configurable);
      }
    }
    return configurable;
  }

  private class EditedSdkModificator implements SdkModificator {
    public String getName() {
      return getNameValue();
    }

    public void setName(String name) {
      setNameValue(name);
    }

    public String getHomePath() {
      return getHomeValue();
    }

    public void setHomePath(String path) {
      doSetHomePath(path, mySdk.getSdkType());
    }

    public String getVersionString() {
      return myVersionString != null? myVersionString : mySdk.getVersionString();
    }

    public void setVersionString(String versionString) {
      throw new UnsupportedOperationException(); // not supported for this editor
    }

    public SdkAdditionalData getSdkAdditionalData() {
      return mySdk.getSdkAdditionalData();
    }

    public void setSdkAdditionalData(SdkAdditionalData data) {
      throw new UnsupportedOperationException(); // not supported for this editor
    }

    public VirtualFile[] getRoots(ProjectRootType rootType) {
      if (ProjectRootType.CLASS.equals(rootType)) {
        return myClassPathEditor.getRoots();
      }
      if (ProjectRootType.JAVADOC.equals(rootType)) {
        return myJavadocPathEditor.getRoots();
      }
      if (ProjectRootType.SOURCE.equals(rootType)) {
        return mySourcePathEditor.getRoots();
      }
      return VirtualFile.EMPTY_ARRAY;
    }

    public void addRoot(VirtualFile root, ProjectRootType rootType) {
      if (ProjectRootType.CLASS.equals(rootType)) {
        myClassPathEditor.addPaths(new VirtualFile[] {root});
      }
      else if (ProjectRootType.JAVADOC.equals(rootType)) {
        myJavadocPathEditor.addPaths(new VirtualFile[] {root});
      }
      else if (ProjectRootType.SOURCE.equals(rootType)) {
        mySourcePathEditor.addPaths(new VirtualFile[] {root});
      }
    }

    public void removeRoot(VirtualFile root, ProjectRootType rootType) {
      if (ProjectRootType.CLASS.equals(rootType)) {
        myClassPathEditor.removePaths(new VirtualFile[] {root});
      }
      else if (ProjectRootType.JAVADOC.equals(rootType)) {
        myJavadocPathEditor.removePaths(new VirtualFile[] {root});
      }
      else if (ProjectRootType.SOURCE.equals(rootType)) {
        mySourcePathEditor.removePaths(new VirtualFile[] {root});
      }
    }

    public void removeRoots(ProjectRootType rootType) {
      if (ProjectRootType.CLASS.equals(rootType)) {
        myClassPathEditor.clearList();
      }
      else if (ProjectRootType.JAVADOC.equals(rootType)) {
        myJavadocPathEditor.clearList();
      }
      else if (ProjectRootType.SOURCE.equals(rootType)) {
        mySourcePathEditor.clearList();
      }
    }

    public void removeAllRoots() {
      myClassPathEditor.clearList();
      myJavadocPathEditor.clearList();
      mySourcePathEditor.clearList();
    }

    public void commitChanges() {
    }

    public boolean isWritable() {
      return true;
    }
  }
}
