package com.intellij.ide.util;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.PsiClassChildrenSource;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.util.gotoByName.ChooseByNamePanel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.ui.Tree;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class TreeClassChooserDialog extends DialogWrapper implements TreeClassChooser{
  private Tree myTree;
  private PsiClass mySelectedClass = null;
  private Project myProject;
  private BaseProjectTreeBuilder myBuilder;
  private TabbedPaneWrapper myTabbedPane;
  private ChooseByNamePanel myGotoByNamePanel;
  private GlobalSearchScope myScope;
  private TreeClassChooser.ClassFilter myClassFilter;
  private final PsiClass myInitialClass;
  private final PsiClassChildrenSource myClassChildrens;

  public TreeClassChooserDialog(String title, Project project) {
    this(title, project, null);
  }

  public TreeClassChooserDialog(String title, Project project, PsiClass initialClass) {
    this(
      title,
      project,
      GlobalSearchScope.projectScope(project),
      null,
      initialClass
    );
  }

  public TreeClassChooserDialog(
    String title,
    Project project,
    GlobalSearchScope scope,
    TreeClassChooser.ClassFilter classFilter,
    PsiClass initialClass
  ){
    this(title, project, scope, classFilter, initialClass, PsiClassChildrenSource.NONE);
  }

  private TreeClassChooserDialog (String title,
                                  Project project,
                                  GlobalSearchScope scope,
                                  TreeClassChooser.ClassFilter classFilter, PsiClass initialClass, PsiClassChildrenSource classChildrens) {
    super(project, true);
    myScope = scope;
    myClassFilter = classFilter;
    myInitialClass = initialClass;
    myClassChildrens = classChildrens;
    setTitle(title);
    myProject = project;
    init();
    if (initialClass != null) {
      selectClass(initialClass);
    }

    handleSelectionChanged();
  }

  public static TreeClassChooserDialog withInnerClasses(String title, Project project, GlobalSearchScope scope,
                                                        final TreeClassChooser.ClassFilter classFilter, PsiClass initialClass) {
    return new TreeClassChooserDialog(title, project, scope, classFilter, initialClass, new PsiClassChildrenSource() {
      public void addChildren(PsiClass psiClass, java.util.List<PsiElement> children) {
        ArrayList<PsiElement> innerClasses = new ArrayList<PsiElement>();
        PsiClassChildrenSource.CLASSES.addChildren(psiClass, innerClasses);
        for (PsiElement innerClass : innerClasses) {
          if (classFilter.isAccepted((PsiClass)innerClass)) children.add(innerClass);
        }
      }
    });
  }

  protected JComponent createCenterPanel() {
    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);

    ProjectAbstractTreeStructureBase treeStructure = new AbstractProjectTreeStructure(
      myProject) {
      public boolean isFlattenPackages() {
        return false;
      }

      public boolean isShowMembers() {
        return myClassChildrens != PsiClassChildrenSource.NONE;
      }

      public boolean isHideEmptyMiddlePackages() {
        return true;
      }


      public boolean isAbbreviatePackageNames() {
        return false;
      }

      public boolean isShowLibraryContents() {
        return true;
      }

      public boolean isShowModules() {
        return false;
      }
    };
    myBuilder = new ProjectTreeBuilder(myProject, myTree, model, AlphaComparator.INSTANCE, treeStructure);

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.expandRow(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new NodeRenderer());
    myTree.putClientProperty("JTree.lineStyle", "Angled");

    JScrollPane scrollPane = new JScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(500, 300));

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path != null && myTree.isPathSelected(path)) {
            doOKAction();
          }
        }
      }
    });

    myTree.addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          handleSelectionChanged();
        }
      }
    );

    new TreeSpeedSearch(myTree);

    myTabbedPane = new TabbedPaneWrapper();

    final JPanel dummyPanel = new JPanel(new BorderLayout());
    String name = null;
    if (myInitialClass != null) {
      name = myInitialClass.getName();
    }
    myGotoByNamePanel = new ChooseByNamePanel(myProject, new MyGotoClassModel(myProject), name, myScope.isSearchInLibraries()) {
      protected void close(boolean isOk) {
        super.close(isOk);

        if (isOk) {
          doOKAction();
        }
        else {
          doCancelAction();
        }
      }

      protected void initUI(ChooseByNamePopupComponent.Callback callback, ModalityState modalityState, boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        dummyPanel.add(myGotoByNamePanel.getPanel(), BorderLayout.CENTER);
        IdeFocusTraversalPolicy.getPreferredFocusedComponent(myGotoByNamePanel.getPanel()).requestFocus();
      }

      protected void choosenElementMightChange() {
        handleSelectionChanged();
      }
    };

    myTabbedPane.addTab("Search by Name", dummyPanel);
    myTabbedPane.addTab("Project", scrollPane);

    myGotoByNamePanel.invoke(new MyCallback(), getModalityState(), false);

    myTabbedPane.addChangeListener(
      new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          handleSelectionChanged();
        }
      }
    );

    return myTabbedPane.getComponent();
  }

  private void handleSelectionChanged(){
    PsiClass selection = calcSelectedClass();
    setOKActionEnabled(selection != null);
  }

  protected void doOKAction() {
    mySelectedClass = calcSelectedClass();
    if (mySelectedClass == null) return;
    super.doOKAction();
  }

  public PsiClass getSelectedClass() {
    return mySelectedClass;
  }

  public void selectClass(final PsiClass aClass) {
    selectElementInTree(aClass);
  }

  public void selectDirectory(final PsiDirectory directory) {
    selectElementInTree(directory);
  }

  public void showDialog() {
    show();
  }

  public void showPopup() {
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(myProject, new MyGotoClassModel(myProject));
    popup.invoke(new ChooseByNamePopupComponent.Callback() {
      public void onClose () {

      }
      public void elementChosen(Object element) {
        mySelectedClass = (PsiClass)element;
        ((NavigationItem)element).navigate(true);
      }
    }, getModalityState(), true);
  }


  private void selectElementInTree(final PsiElement element) {
    if (element == null) {
      throw new IllegalArgumentException("aClass cannot be null");
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myBuilder == null) return;
        final VirtualFile vFile = BasePsiNode.getVirtualFile(element);
        myBuilder.select(element, vFile, false);
      }
    }, getModalityState());
  }

  private ModalityState getModalityState() {
    return ModalityState.stateForComponent(getRootPane());
  }

  private PsiClass calcSelectedClass() {
    if (myTabbedPane.getSelectedIndex() == 0) {
      return (PsiClass)myGotoByNamePanel.getChosenElement();
    }
    else {
      TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ClassTreeNode)) return null;
      ClassTreeNode descriptor = (ClassTreeNode)userObject;
      return descriptor.getPsiClass();
    }
  }


  protected void dispose() {
    if (myBuilder != null) {
      myBuilder.dispose();
      myBuilder = null;
    }
    super.dispose();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.TreeClassChooserDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myGotoByNamePanel.getPreferredFocusedComponent();
  }

  private class MyGotoClassModel extends GotoClassModel2 {
    public MyGotoClassModel(Project project) {
      super(project);
    }

    public Object[] getElementsByName(final String name, final boolean checkBoxState) {
      final PsiManager manager = PsiManager.getInstance(myProject);
      PsiClass[] classes = manager.getShortNamesCache().getClassesByName(name, myScope);

      ArrayList<PsiClass> list = new ArrayList<PsiClass>();
      for (PsiClass aClass : classes) {
        if (myClassFilter != null && !myClassFilter.isAccepted(aClass)) continue;
        list.add(aClass);
      }
      return list.toArray(new PsiClass[list.size()]);
    }

    public String getPromptText() {
      return null;
    }
  }

  private class MyCallback extends ChooseByNamePopupComponent.Callback {
    public void elementChosen(Object element) {
      mySelectedClass = (PsiClass)element;
      close(OK_EXIT_CODE);
    }
  }

  public static class InheritanceClassFilterImpl implements TreeClassChooser.InheritanceClassFilter{
    private final PsiClass myBase;
    private final boolean myAcceptsSelf;
    private final boolean myAcceptsInner;
    private final Condition<PsiClass> myAddtionalCondition;

    public InheritanceClassFilterImpl(PsiClass base, boolean acceptsSelf, boolean acceptInner,
                                      Condition<PsiClass> addtionalCondition
                                  ) {
      myAcceptsSelf = acceptsSelf;
      myAcceptsInner = acceptInner;
      if (addtionalCondition == null){
        addtionalCondition = FilteringIterator.alwaysTrueCondition(PsiClass.class);
      }
      myAddtionalCondition = addtionalCondition;
      myBase = base;
    }

    public boolean isAccepted(PsiClass aClass) {
      if (!myAcceptsInner && !(aClass.getParent() instanceof PsiJavaFile)) return false;
      if (!myAddtionalCondition.value(aClass)) return false;
      if (myBase == null) return true;
      return myAcceptsSelf ?
             InheritanceUtil.isInheritorOrSelf(aClass, myBase, true) :
             aClass.isInheritor(myBase, true);
    }
  }
}
