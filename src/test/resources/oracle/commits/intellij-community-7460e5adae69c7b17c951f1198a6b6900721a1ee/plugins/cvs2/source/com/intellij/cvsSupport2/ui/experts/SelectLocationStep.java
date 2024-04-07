package com.intellij.cvsSupport2.ui.experts;

import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.Project;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.io.File;

/**
 * author: lesya
 */
public abstract class SelectLocationStep extends WizardStep{
  protected FileSystemTree myFileSystemTree;
  private ActionToolbar myFileSystemToolBar;

  private final TreeSelectionListener myTreeSelectionListener = new TreeSelectionListener() {
        public void valueChanged(TreeSelectionEvent e) {
          getWizard().updateStep();
        }
      };

  public SelectLocationStep(String description, CvsWizard wizard, Project project) {
    super(description, wizard);
    myFileSystemTree = PeerFactory.getInstance().getFileSystemTreeFactory().createFileSystemTree(project,
                                          new FileChooserDescriptor(false, true, false, false, false, false));
    myFileSystemTree.updateTree();

    JTree tree = myFileSystemTree.getTree();
    tree.addSelectionPath(tree.getPathForRow(0));
  }

  protected void init() {

    final DefaultActionGroup fileSystemActionGroup = createFileSystemActionGroup();
    myFileSystemToolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                                          fileSystemActionGroup, true);

    myFileSystemTree.getTree().getSelectionModel().addTreeSelectionListener(myTreeSelectionListener);

    myFileSystemTree.getTree().setCellRenderer(new NodeRenderer());
     myFileSystemTree.getTree().addMouseListener(new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UPDATE_POPUP,
                                                                                      fileSystemActionGroup);
        popupMenu.getComponent().show(comp, x, y);
      }
    });

    super.init();
  }

  protected JComponent createComponent() {
    JPanel result = new JPanel(new BorderLayout());
    result.add(myFileSystemToolBar.getComponent(), BorderLayout.NORTH);
    result.add(ScrollPaneFactory.createScrollPane(myFileSystemTree.getTree()), BorderLayout.CENTER);
    return result;
  }

  protected void dispose() {
    myFileSystemTree.getTree().getSelectionModel().removeTreeSelectionListener(myTreeSelectionListener);
  }

  public boolean nextIsEnabled() {
    return myFileSystemTree.getSelectedFile() != null;
  }

  public boolean setActive() {
    return true;
  }

  private DefaultActionGroup createFileSystemActionGroup() {
    DefaultActionGroup group = PeerFactory.getInstance().getFileSystemTreeFactory().createDefaultFileSystemActions(myFileSystemTree);
    
    AnAction[] actions = getActions();

    if (actions.length > 0) group.addSeparator();

    for (int i = 0; i < actions.length; i++) {
      group.add(actions[i]);
    }

    return group;
  }

  protected AnAction[] getActions(){
    return new AnAction[0];
  }

  public File getSelectedFile() {
    return CvsVfsUtil.getFileFor(myFileSystemTree.getSelectedFile());
  }

  public Component getPreferredFocusedComponent() {
    return myFileSystemTree.getTree();
  }
}
