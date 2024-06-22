package com.intellij.ide.hierarchy.type;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ToolbarHelpAction;
import com.intellij.ide.hierarchy.*;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.ui.AutoScrollToSourceHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.pom.Navigatable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class TypeHierarchyBrowser extends JPanel implements DataProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.type.TypeHierarchyBrowser");

  private final static String HELP_ID = "viewingStructure.classHierarchy";
  static final String TYPE_HIERARCHY_BROWSER_ID="TYPE_HIERARCHY_BROWSER_ID";

  private Content myContent;
  private final Project myProject;
  private final Hashtable<String, HierarchyTreeBuilder> myBuilders = new Hashtable<String, HierarchyTreeBuilder>();
  private final Hashtable<Object, JTree> myTrees = new Hashtable<Object, JTree>();

  private final RefreshAction myRefreshAction = new RefreshAction();
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private SmartPsiElementPointer mySmartPsiElementPointer;
  private boolean myIsInterface;
  private final ActionToolbar myToolbar;
  private final CardLayout myCardLayout;
  private final JPanel myTreePanel;
  private String myCurrentViewName;

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyDeleteProvider myDeleteElementProvider = new MyDeleteProvider();

  private boolean myCachedIsValidBase = false;

  private static final String TYPE_HIERARCHY_BROWSER_DATA_CONSTANT = "com.intellij.ide.hierarchy.type.TypeHierarchyBrowser";
  private List<Runnable> myRunOnDisposeList = new ArrayList<Runnable>();

  public TypeHierarchyBrowser(final Project project, final PsiClass psiClass) {
    myProject = project;

    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      protected boolean isAutoScrollMode() {
        return HierarchyBrowserManager.getInstance(myProject).IS_AUTOSCROLL_TO_SOURCE;
      }

      protected void setAutoScrollMode(final boolean state) {
        HierarchyBrowserManager.getInstance(myProject).IS_AUTOSCROLL_TO_SOURCE = state;
      }
    };

    setHierarchyBase(psiClass);
    setLayout(new BorderLayout());

    myToolbar = createToolbar();
    add(myToolbar.getComponent(), BorderLayout.NORTH);

    myCardLayout = new CardLayout();
    myTreePanel = new JPanel(myCardLayout);
    myTrees.put(TypeHierarchyTreeStructure.TYPE, createTree());
    myTrees.put(SupertypesHierarchyTreeStructure.TYPE, createTree());
    myTrees.put(SubtypesHierarchyTreeStructure.TYPE, createTree());
    final Enumeration<Object> keys = myTrees.keys();
    while (keys.hasMoreElements()) {
      final Object key = keys.nextElement();
      final JTree tree = myTrees.get(key);
      myTreePanel.add(new JScrollPane(tree), key);
    }
    add(myTreePanel, BorderLayout.CENTER);
  }

  public String getCurrentViewName() {
    return myCurrentViewName;
  }

  public boolean isInterface() {
    return myIsInterface;
  }

  private JTree createTree() {
    final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode("")));
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setToggleClickCount(-1);
    tree.setCellRenderer(new HierarchyNodeRenderer());
    tree.putClientProperty("JTree.lineStyle", "Angled");
    EditSourceOnDoubleClickHandler.install(tree);
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_TYPE_HIERARCHY_POPUP);
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.TYPE_HIERARCHY_VIEW_POPUP, ActionManager.getInstance());

    myRefreshAction.registerShortcutOn(tree);
    myRunOnDisposeList.add(new Runnable() {
      public void run() {
        myRefreshAction.unregisterCustomShortcutSet(tree);
      }
    });

    final BaseOnThisTypeAction baseOnThisTypeAction = new BaseOnThisTypeAction();
    baseOnThisTypeAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(),
                                                   tree);

    new TreeSpeedSearch(tree);
    TreeUtil.installActions(tree);
    TreeToolTipHandler.install(tree);
    myAutoScrollToSourceHandler.install(tree);
    return tree;
  }

  private void setHierarchyBase(final PsiClass psiClass) {
    mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(psiClass);
    myIsInterface = psiClass.isInterface();
  }

  public final void setContent(final Content content) {
    myContent = content;
  }

  private void restoreCursor() {
    /*int n =*/ myAlarm.cancelAllRequests();
    //    if (n == 0) {
    setCursor(Cursor.getDefaultCursor());
    //    }
  }

  private void setWaitCursor() {
    myAlarm.addRequest(
      new Runnable() {
      public void run() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    },
      100
    );
  }

  public final void changeView(final String typeName) {
    myCurrentViewName = typeName;

    final PsiElement element = mySmartPsiElementPointer.getElement();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final PsiClass psiClass = (PsiClass)element;

    if (myContent != null) {
      myContent.setDisplayName(typeName + ClassPresentationUtil.getNameForClass(psiClass, false));
    }

    myCardLayout.show(myTreePanel, typeName);

    if (!myBuilders.containsKey(typeName)) {
      setWaitCursor();

      // create builder
      final JTree tree = myTrees.get(typeName);
      final DefaultTreeModel model = /*(DefaultTreeModel)tree.getModel()*/ new DefaultTreeModel(
          new DefaultMutableTreeNode(""));
      tree.setModel(model);

      final HierarchyTreeStructure structure;
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (SupertypesHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new SupertypesHierarchyTreeStructure(myProject, psiClass);
      }
      else if (SubtypesHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new SubtypesHierarchyTreeStructure(myProject, psiClass);
      }
      else if (TypeHierarchyTreeStructure.TYPE.equals(typeName)) {
        structure = new TypeHierarchyTreeStructure(myProject, psiClass);
      }
      else {
        LOG.error("unexpected type: " + typeName);
        return;
      }
      final Comparator<NodeDescriptor> comparator = HierarchyBrowserManager.getInstance(myProject).getComparator();
      final HierarchyTreeBuilder builder = new HierarchyTreeBuilder(myProject, tree, model, structure, comparator);

      myBuilders.put(typeName, builder);

      final HierarchyNodeDescriptor baseDescriptor = structure.getBaseDescriptor();
      builder.buildNodeForElement(baseDescriptor);
      final DefaultMutableTreeNode node = builder.getNodeForElement(baseDescriptor);
      if (node != null) {
        final TreePath path = new TreePath(node.getPath());
        tree.expandPath(path);
        TreeUtil.selectPath(tree, path);
      }

      restoreCursor();
    }

    getCurrentTree().requestFocus();
  }

  private ActionToolbar createToolbar() {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new ViewClassHierarchyAction());
    actionGroup.add(new ViewSupertypesHierarchyAction());
    actionGroup.add(new ViewSubtypesHierarchyAction());
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(myRefreshAction);
    actionGroup.add(myAutoScrollToSourceHandler.createToggleAction());
    actionGroup.add(new CloseAction());
    actionGroup.add(new ToolbarHelpAction(HELP_ID));

    final ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR,
                                                                                  actionGroup, true);
    return toolBar;
  }

  final class RefreshAction extends com.intellij.ide.actions.RefreshAction {
    public RefreshAction() {
      super("Refresh", "Refresh", IconLoader.getIcon("/actions/sync.png"));
    }

    public final void actionPerformed(final AnActionEvent e) {
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      if (!isValidBase()) return;

      final Object[] storedInfo = new Object[1];
      if (myCurrentViewName != null) {
        final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
        storedInfo[0] = builder.storeExpandedAndSelectedInfo();
      }

      final PsiClass base = (PsiClass)mySmartPsiElementPointer.getElement();
      final String[] name = new String[]{myCurrentViewName};
      dispose();
      setHierarchyBase(base);
      validate();
      if (myIsInterface && TypeHierarchyTreeStructure.TYPE.equals(name)) {
        name[0] = SubtypesHierarchyTreeStructure.TYPE;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          changeView(name[0]);
          if (storedInfo != null) {
            final HierarchyTreeBuilder builder = myBuilders.get(myCurrentViewName);
            builder.restoreExpandedAndSelectedInfo(storedInfo[0]);
          }
        }
      });
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  boolean isValidBase() {
    if (PsiDocumentManager.getInstance(myProject).getUncommittedDocuments().length > 0) {
      return myCachedIsValidBase;
    }

    final PsiElement element = mySmartPsiElementPointer.getElement();
    myCachedIsValidBase = element instanceof PsiClass && element.isValid();
    return myCachedIsValidBase;
  }

  private JTree getCurrentTree() {
    if (myCurrentViewName == null) return null;
    final JTree tree = myTrees.get(myCurrentViewName);
    return tree;
  }

  public final class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      myContent.getManager().removeContent(myContent);
    }
  }

  private PsiClass getSelectedClass() {
    final TreePath path = getSelectedPath();
    return extractPsiClass(path);
  }

  private PsiClass extractPsiClass(final TreePath path) {
    if (path == null) return null;
    final Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();
    if (!(userObject instanceof TypeHierarchyNodeDescriptor)) return null;
    final PsiClass aClass = ((TypeHierarchyNodeDescriptor)userObject).getPsiClass();
    return aClass;
  }

  private TreePath getSelectedPath() {
    final JTree tree = getCurrentTree();
    if (tree == null) return null;
    return tree.getSelectionPath();
  }


  private PsiClass[] getSelectedClasses() {
    JTree currentTree = getCurrentTree();
    if (currentTree == null) return PsiClass.EMPTY_ARRAY;
    TreePath[] paths = currentTree.getSelectionPaths();
    ArrayList<PsiClass> psiClasses = new ArrayList<PsiClass>();
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      PsiClass psiClass = extractPsiClass(path);
      if (psiClass == null) continue;
      psiClasses.add(psiClass);
    }
    return psiClasses.toArray(new PsiClass[psiClasses.size()]);
  }

  public final Object getData(final String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      return getSelectedClass();
    }
    if (DataConstantsEx.DELETE_ELEMENT_PROVIDER.equals(dataId)) {
      return myDeleteElementProvider;
    }
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return HELP_ID;
    }
    if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      return getNavigatables();
    }
    if (TYPE_HIERARCHY_BROWSER_DATA_CONSTANT.equals(dataId)) {
      return this;
    }
    if (DataConstantsEx.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return getSelectedClasses();
    }
    if (TYPE_HIERARCHY_BROWSER_ID.equals(dataId)) {
      return this;
    }

    return null;
  }

  private Navigatable[] getNavigatables() {
    final PsiClass[] objects = getSelectedClasses();
    if (objects == null || objects.length == 0) return null;
    final ArrayList<Navigatable> result = new ArrayList<Navigatable>();
    for (int i = 0; i < objects.length; i++) {
      final PsiClass aClass = objects[i];
      if (aClass.isValid()) {
        result.add(aClass);
      }
    }
    return result.toArray(new Navigatable[result.size()]);

  }

  public final void dispose() {
    final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
    for (Iterator<HierarchyTreeBuilder> iterator = builders.iterator(); iterator.hasNext();) {
      final HierarchyTreeBuilder builder = iterator.next();
      builder.dispose();
    }
    for (Iterator<Runnable> it = myRunOnDisposeList.iterator(); it.hasNext();) {
      it.next().run();
    }
    myRunOnDisposeList.clear();
    myBuilders.clear();
  }

  private final class AlphaSortAction extends ToggleAction {
    public AlphaSortAction() {
      super("Sort Alphabetically", "Sort Alphabetically", IconLoader.getIcon("/objectBrowser/sorted.png"));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).SORT_ALPHABETICALLY;
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      final HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(myProject);
      hierarchyBrowserManager.SORT_ALPHABETICALLY = flag;
      final Comparator<NodeDescriptor> comparator = hierarchyBrowserManager.getComparator();
      final Collection<HierarchyTreeBuilder> builders = myBuilders.values();
      for (Iterator<HierarchyTreeBuilder> iterator = builders.iterator(); iterator.hasNext();) {
        final HierarchyTreeBuilder builder = iterator.next();
        builder.setNodeDescriptorComparator(comparator);
      }
    }

    public final void update(final AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  public static final class BaseOnThisTypeAction extends AnAction {
    public final void actionPerformed(final AnActionEvent event) {
      final DataContext dataContext = event.getDataContext();
      final TypeHierarchyBrowser browser = (TypeHierarchyBrowser)dataContext.getData(
          TYPE_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) return;

      final PsiClass selectedClass = browser.getSelectedClass();
      if (selectedClass == null) return;
      final String[] name = new String[]{browser.myCurrentViewName};
      browser.dispose();
      browser.setHierarchyBase(selectedClass);
      browser.validate();
      if (browser.myIsInterface && TypeHierarchyTreeStructure.TYPE.equals(name)) {
        name[0] = SubtypesHierarchyTreeStructure.TYPE;
      }
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          browser.changeView(name[0]);
        }
      });
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      registerCustomShortcutSet(
        ActionManager.getInstance().getAction(IdeActions.ACTION_TYPE_HIERARCHY).getShortcutSet(), null);

      final DataContext dataContext = event.getDataContext();
      final TypeHierarchyBrowser browser = (TypeHierarchyBrowser)dataContext.getData(TYPE_HIERARCHY_BROWSER_DATA_CONSTANT);
      if (browser == null) {
        presentation.setVisible(false);
        presentation.setEnabled(false);
        return;
      }

      presentation.setVisible(true);

      final PsiClass selectedClass = browser.getSelectedClass();
      if (selectedClass != null &&
          !selectedClass.equals(browser.mySmartPsiElementPointer.getElement()) &&
          !"java.lang.Object".equals(selectedClass.getQualifiedName()) &&
          selectedClass.isValid()
      ) {
        presentation.setText("Base on This " + (selectedClass.isInterface() ? "Interface" : "Class"));
        presentation.setEnabled(true);
      }
      else {
        presentation.setEnabled(false);
      }
    }
  }

  private final class MyDeleteProvider implements DeleteProvider {
    public final void deleteElement(final DataContext dataContext) {
      final PsiClass aClass = getSelectedClass();
      if (aClass == null || aClass instanceof PsiAnonymousClass) return;
      final com.intellij.openapi.localVcs.LvcsAction action = LvcsIntegration.checkinFilesBeforeRefactoring(myProject,
                                                                                                            "Deleting class " +
                                                                                                            aClass.getQualifiedName());
      try {
        final PsiElement[] elements = new PsiElement[]{aClass};
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        LvcsIntegration.checkinFilesAfterRefactoring(myProject, action);
      }
    }

    public final boolean canDeleteElement(final DataContext dataContext) {
      final PsiClass aClass = getSelectedClass();
      if (aClass == null || aClass instanceof PsiAnonymousClass) {
        return false;
      }
      final PsiElement[] elements = new PsiElement[]{aClass};
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }
}
