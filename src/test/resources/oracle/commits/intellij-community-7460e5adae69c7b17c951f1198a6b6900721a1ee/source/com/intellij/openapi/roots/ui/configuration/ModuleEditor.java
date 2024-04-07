package com.intellij.openapi.roots.ui.configuration;

import com.intellij.j2ee.ex.J2EEModulePropertiesEx;
import com.intellij.j2ee.j2eeDom.J2EEModuleProperties;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.module.impl.ModuleConfigurationStateImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.EventDispatcher;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 4, 2003
 *         Time: 6:29:56 PM
 */
public class ModuleEditor {
  private final Project myProject;
  private JPanel myPanel;
  private ModifiableRootModel myModifiableRootModel; // important: in order to correctly update OrderEntries UI use corresponding proxy for the model
  private String mySelectedTabName;
  private TabbedPaneWrapper myTabbedPane;
  private final ModulesProvider myModulesProvider;
  private final String myName;
  private List<ModuleConfigurationEditor> myEditors = new ArrayList<ModuleConfigurationEditor>();
  private DependenciesEditor myDependenciesEditor;
  private ModifiableRootModel myModifiableRootModelProxy;
  private EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public static interface ChangeListener extends EventListener {
    void moduleStateChanged(ModifiableRootModel moduleRootModel);
  }

  public ModuleEditor(Project project, ModulesProvider modulesProvider, String moduleName) {
    myProject = project;
    myModulesProvider = modulesProvider;
    myName = moduleName;
  }

  public void addChangeListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public Module getModule() {
    return myModulesProvider.getModule(myName);
  }

  public ModifiableRootModel getModifiableRootModel() {
    return myModifiableRootModel;
  }

  public ModifiableRootModel getModifiableRootModelProxy() {
    if (myModifiableRootModelProxy == null) {
      createPanel(); // this will initialize the proxy
    }
    return myModifiableRootModelProxy;
  }

  public boolean isModified() {
    for (int i = 0; i < myEditors.size(); i++) {
      ModuleConfigurationEditor moduleElementsEditor = myEditors.get(i);
      if (moduleElementsEditor.isModified()) {
        return true;
      }
    }
    return false;
  }

  private void createEditors(Module module) {
    Object[] providers = module.getComponents(ModuleConfigurationEditorProvider.class);
    ModuleConfigurationState state = new ModuleConfigurationStateImpl(myProject, module, myModulesProvider, myModifiableRootModelProxy);
    for (int i = 0; i < providers.length; i++) {
      ModuleConfigurationEditorProvider provider = (ModuleConfigurationEditorProvider)providers[i];
      final ModuleConfigurationEditor[] editors = provider.createEditors(state);
      myEditors.addAll(Arrays.asList(editors));
      if (myDependenciesEditor == null) {
        for (int idx = 0; idx < editors.length; idx++) {
          ModuleConfigurationEditor editor = editors[idx];
          if (editor instanceof DependenciesEditor) {
            myDependenciesEditor = (DependenciesEditor)editor;
            break;
          }
        }
      }
    }
  }

  private JPanel createPanel() {
    myModifiableRootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    myModifiableRootModelProxy = (ModifiableRootModel)Proxy.newProxyInstance(
      getClass().getClassLoader(), new Class[]{ModifiableRootModel.class}, new ModifiableRootModelInvocationHandler(myModifiableRootModel)
    );

    myPanel = new JPanel(new BorderLayout());

    createEditors(getModule());

    myTabbedPane = new TabbedPaneWrapper();
    for (Iterator<ModuleConfigurationEditor> it = myEditors.iterator(); it.hasNext();) {
      ModuleConfigurationEditor editor = it.next();
      if (editor == myDependenciesEditor && myModulesProvider.getModules().length <= 1) {
        continue;
      }
      myTabbedPane.addTab(editor.getDisplayName(), editor.getIcon(), editor.createComponent(), null);
      editor.reset();
    }
    myTabbedPane.installKeyboardNavigation();
    setSelectedTabName(mySelectedTabName);

    myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

    return myPanel;
  }

  private int getEditorTabIndex(final String editorName) {
    if (myTabbedPane != null && editorName != null) {
      final int tabCount = myTabbedPane.getTabCount();
      for (int idx = 0; idx < tabCount; idx++) {
        if (editorName.equals(myTabbedPane.getTitleAt(idx))) {
          return idx;
        }
      }
    }
    return -1;
  }

  public JPanel getPanel() {
    if (myPanel == null) {
      myPanel = createPanel();
    }
    return myPanel;
  }

  public void moduleCountChanged(int oldCount, int newCount) {
    if (myTabbedPane != null && myDependenciesEditor != null) {
      if (newCount > 1) {
        if (getEditorTabIndex(myDependenciesEditor.getDisplayName()) == -1) { // if not added yet
          final int indexToInsert = myEditors.indexOf(myDependenciesEditor);
          myTabbedPane.insertTab(myDependenciesEditor.getDisplayName(), myDependenciesEditor.getIcon(),
                                 myDependenciesEditor.getComponent(), null, indexToInsert);
        }
      }
      else {
        final int editorTabIndex = getEditorTabIndex(myDependenciesEditor.getDisplayName());
        if (editorTabIndex >= 0) { // already added
          myTabbedPane.removeTabAt(editorTabIndex);
        }
      }
    }
    updateOrderEntriesInEditors();
  }

  private void updateOrderEntriesInEditors() {
    for (Iterator<ModuleConfigurationEditor> it = myEditors.iterator(); it.hasNext();) {
      it.next().moduleStateChanged();
    }
    myEventDispatcher.getMulticaster().moduleStateChanged(getModifiableRootModelProxy());
  }

  public ModifiableRootModel dispose() {
    try {
      for (Iterator<ModuleConfigurationEditor> it = myEditors.iterator(); it.hasNext();) {
        it.next().disposeUIResources();
      }

      myEditors.clear();

      if (myTabbedPane != null) {
        mySelectedTabName = getSelectedTabName();
        myTabbedPane = null;
      }
      myPanel = null;
      return myModifiableRootModel;
    }
    finally {
      myModifiableRootModel = null;
      myModifiableRootModelProxy = null;
    }
  }

  public ModifiableRootModel applyAndDispose() throws ConfigurationException {
    for (Iterator<ModuleConfigurationEditor> it = myEditors.iterator(); it.hasNext();) {
      ModuleConfigurationEditor editor = it.next();
      editor.saveData();
      editor.apply();
    }

    if (getModule().getModuleType().isJ2EE() && myModifiableRootModel != null) {
      final J2EEModulePropertiesEx j2EEModulePropertiesEx = (J2EEModulePropertiesEx)J2EEModuleProperties.getInstance(getModule());
      if (j2EEModulePropertiesEx != null) {
        j2EEModulePropertiesEx.commit(myModifiableRootModel);
      }
    }

    return dispose();
  }

  public String getName() {
    return myName;
  }

  public String getSelectedTabName() {
    return myTabbedPane == null ? null : myTabbedPane.getTitleAt(myTabbedPane.getSelectedIndex());
  }

  public void setSelectedTabName(String name) {
    if (name != null) {
      final int editorTabIndex = getEditorTabIndex(name);
      if (editorTabIndex >= 0 && editorTabIndex < myTabbedPane.getTabCount()) {
        myTabbedPane.setSelectedIndex(editorTabIndex);
        mySelectedTabName = name;
      }
    }
  }

  private class ModifiableRootModelInvocationHandler implements InvocationHandler {
    private final ModifiableRootModel myDelegateModel;
    private final Set<String> myCheckedNames = new HashSet<String>(
      Arrays.asList(
        new String[]{"addOrderEntry", "addLibraryEntry", "addInvalidLibrary", "addModuleOrderEntry", "addInvalidModuleEntry",
                     "removeOrderEntry", "setJdk", "inheritJdk"}
      ));

    ModifiableRootModelInvocationHandler(ModifiableRootModel model) {
      myDelegateModel = model;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof LibraryTable) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LibraryTable.class},
                                        new LibraryTableInvocationHandler((LibraryTable)result));
        }
        return result;
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  private class LibraryTableInvocationHandler implements InvocationHandler {
    private final LibraryTable myDelegateTable;
    private final Set<String> myCheckedNames = new HashSet<String>(Arrays.asList(new String[]{"removeLibrary", /*"createLibrary"*/}));

    LibraryTableInvocationHandler(LibraryTable table) {
      myDelegateTable = table;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = myCheckedNames.contains(method.getName());
      try {
        final Object result = method.invoke(myDelegateTable, unwrapParams(params));
        if (result instanceof Library) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                        new LibraryInvocationHandler((Library)result));
        }
        else if (result instanceof LibraryTable.ModifiableModel) {
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LibraryTable.ModifiableModel.class},
                                        new LibraryTableModelInvocationHandler((LibraryTable.ModifiableModel)result));
        }
        if (result instanceof Library[]) {
          Library[] libraries = (Library[])result;
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
            (Library)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                            new LibraryInvocationHandler(library));
          }
        }
        return result;
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }

  }

  private class LibraryInvocationHandler implements InvocationHandler, ProxyDelegateAccessor {
    private final Library myDelegateLibrary;

    LibraryInvocationHandler(Library delegateLibrary) {
      myDelegateLibrary = delegateLibrary;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final Object result = method.invoke(myDelegateLibrary, unwrapParams(params));
      if (result instanceof Library.ModifiableModel) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.ModifiableModel.class},
                                      new LibraryModifiableModelInvocationHandler((Library.ModifiableModel)result));
      }
      return result;
    }

    public Object getDelegate() {
      return myDelegateLibrary;
    }
  }

  private class LibraryModifiableModelInvocationHandler implements InvocationHandler {
    private final Library.ModifiableModel myDelegateModel;

    LibraryModifiableModelInvocationHandler(Library.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = "commit".equals(method.getName());
      try {
        return method.invoke(myDelegateModel, unwrapParams(params));
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  private class LibraryTableModelInvocationHandler implements InvocationHandler {
    private final LibraryTable.ModifiableModel myDelegateModel;

    LibraryTableModelInvocationHandler(LibraryTable.ModifiableModel delegateModel) {
      myDelegateModel = delegateModel;
    }

    public Object invoke(Object object, Method method, Object[] params) throws Throwable {
      final boolean needUpdate = "commit".equals(method.getName());
      try {
        Object result = method.invoke(myDelegateModel, unwrapParams(params));
        if (result instanceof Library[]) {
          Library[] libraries = (Library[])result;
          for (int idx = 0; idx < libraries.length; idx++) {
            Library library = libraries[idx];
            libraries[idx] =
            (Library)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                            new LibraryInvocationHandler(library));
          }
        }
        if (result instanceof Library) {
          result =
          Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Library.class},
                                 new LibraryInvocationHandler((Library)result));
        }
        return result;
      }
      finally {
        if (needUpdate) {
          updateOrderEntriesInEditors();
        }
      }
    }
  }

  public static interface ProxyDelegateAccessor {
    Object getDelegate();
  }

  private Object[] unwrapParams(Object[] params) {
    if (params == null || params.length == 0) {
      return params;
    }
    final Object[] unwrappedParams = new Object[params.length];
    for (int idx = 0; idx < params.length; idx++) {
      Object param = params[idx];
      if (param != null && Proxy.isProxyClass(param.getClass())) {
        final InvocationHandler invocationHandler = Proxy.getInvocationHandler(param);
        if (invocationHandler instanceof ProxyDelegateAccessor) {
          param = ((ProxyDelegateAccessor)invocationHandler).getDelegate();
        }
      }
      unwrappedParams[idx] = param;
    }
    return unwrappedParams;
  }

  public String getHelpTopic() {
    if (myTabbedPane == null || myEditors.isEmpty()) {
      return null;
    }
    final ModuleConfigurationEditor moduleElementsEditor = myEditors.get(myTabbedPane.getSelectedIndex());
    return moduleElementsEditor.getHelpTopic();
  }

}
