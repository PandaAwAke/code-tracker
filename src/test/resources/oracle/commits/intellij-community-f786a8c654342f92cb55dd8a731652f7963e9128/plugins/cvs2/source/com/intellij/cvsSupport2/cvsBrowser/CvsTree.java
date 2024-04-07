package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.UIHelper;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;

import org.jetbrains.annotations.NonNls;

public class CvsTree extends JPanel {
  private CvsElement[] myCurrentSelection = new CvsElement[0];
  private Tree myTree;
  private final CvsRootConfiguration myCvsRootConfiguration;
  private final Observable mySelectionObservable = new AlwaysNotificatedObservable();
  private final boolean myShowFiles;
  private final boolean myAllowRootSelection;
  private final boolean myShowModules;
  private final Project myProject;
  private final int mySelectionModel;

  @NonNls public static final String SELECTION_CHANGED = "Selection Changed";
  @NonNls public static final String LOGIN_ABORTED = "Login Aborted";

  public CvsTree(CvsRootConfiguration env,
                 Project project,
                 boolean showFiles,
                 int selectionMode,
                 boolean allowRootSelection, boolean showModules) {
    super(new BorderLayout());
    myProject = project;
    mySelectionModel = selectionMode;
    myShowModules = showModules;
    myAllowRootSelection = allowRootSelection;
    myShowFiles = showFiles;
    setSize(500, 500);
    myCvsRootConfiguration = env;
  }

  private void addSelectionListener() {
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        setCurrentSelection(myTree.getSelectionPaths());
      }
    });
  }

  private void setCurrentSelection(TreePath[] paths) {
    ArrayList<CvsElement> selection = new ArrayList<CvsElement>();
    if (paths != null) {
      for (int i = 0; i < paths.length; i++) {
        Object selectedObject = paths[i].getLastPathComponent();
        if (!(selectedObject instanceof CvsElement)) continue;
        CvsElement cvsElement = (CvsElement)selectedObject;
        if (cvsElement.getElementPath().equals(".") && (!myAllowRootSelection)) continue;
        selection.add(cvsElement);
      }
    }
    myCurrentSelection = selection.toArray(new CvsElement[selection.size()]);
    mySelectionObservable.notifyObservers(SELECTION_CHANGED);
  }

  private CvsElement createRoot(Project project) {
    String rootName = myCvsRootConfiguration.toString();
    CvsElement result = CvsElementFactory.FOLDER_ELEMENT_FACTORY
      .createElement(rootName, myCvsRootConfiguration, project);
    result.setName(rootName);
    result.setDataProvider(new RootDataProvider(myCvsRootConfiguration, myShowFiles, myShowModules));
    result.setPath(".");
    result.cannotBeCheckedOut();
    return result;
  }

  public CvsElement[] getCurrentSelection() {
    return myCurrentSelection;
  }

  public void addSelectionObserver(Observer observer) {
    mySelectionObservable.addObserver(observer);
  }

  public JTree getTree() {
    return myTree;
  }

  public void dispose() {
    mySelectionObservable.deleteObservers();
  }

  public void onLoginAborted() {
    mySelectionObservable.notifyObservers(LOGIN_ABORTED);
  }

  public void init() {
    CvsElement root = createRoot(myProject);
    TreeModel deafModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    CvsTreeModel model = new CvsTreeModel(root);
    root.setModel(model);
    myTree = new Tree(deafModel);
    model.setTree(myTree);
    model.setCvsTree(this);

    add(new JScrollPane(myTree), BorderLayout.CENTER);

    myTree.setModel(model);


    myTree.getSelectionModel().setSelectionMode(mySelectionModel);
    myTree.setCellRenderer(new Cvs2Renderer());
    addSelectionListener();

    UIHelper uiHelper = PeerFactory.getInstance().getUIHelper();
    uiHelper.installToolTipHandler(myTree);
    uiHelper.installTreeSpeedSearch(myTree);
    TreeUtil.installActions(myTree);

    myTree.requestFocus();
  }

  class AlwaysNotificatedObservable extends Observable{
    public void notifyObservers(Object arg) {
      setChanged();
      super.notifyObservers(arg);
    }
  }

}
