/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.impl.ProjectPaneSelectInTarget;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDirectory;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public final class ProjectViewPane extends AbstractProjectViewPSIPane implements ProjectComponent {

  public static final String ID = "ProjectPane";
  private static final Icon ICON = IconLoader.getIcon("/general/projectTab.png");

  public ProjectViewPane(Project project) {
    super(project);
  }

  public String getTitle() {
    return "Project";
  }

  public String getId() {
    return ID;
  }

  public Icon getIcon() {
    return ICON;
  }


  protected ProjectViewSelectInTarget createSelectInTarget() {
    return new ProjectPaneSelectInTarget(myProject);
  }

  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new ProjectViewTreeUpdater(treeBuilder);
  }

  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectTreeStructure(myProject, ID);
  }

  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }

      public DefaultMutableTreeNode getSelectedNode() {
        return ProjectViewPane.this.getSelectedNode();
      }
    };
  }

  public String getComponentName() {
    return "ProjectPane";
  }


  // should be first
  public int getWeight() {
    return 0;
  }

  private final class ProjectViewTreeUpdater extends AbstractTreeUpdater {
    private ProjectViewTreeUpdater(final AbstractTreeBuilder treeBuilder) {
      super(treeBuilder);
    }

    public boolean addSubtreeToUpdateByElement(Object element) {
      if (element instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)element;
        final ProjectTreeStructure treeStructure = (ProjectTreeStructure)myTreeStructure;
        PsiDirectory dirToUpdateFrom = dir;
        if (!treeStructure.isFlattenPackages() && treeStructure.isHideEmptyMiddlePackages()) {
          // optimization: this check makes sense only if flattenPackages == false && HideEmptyMiddle == true
          while (dirToUpdateFrom != null && dirToUpdateFrom.getPackage() != null && TreeViewUtil.isEmptyMiddlePackage(dirToUpdateFrom, true)) {
            dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
          }
        }
        boolean addedOk;
        while (!(addedOk = super.addSubtreeToUpdateByElement(dirToUpdateFrom == null? myTreeStructure.getRootElement() : dirToUpdateFrom))) {
          if (dirToUpdateFrom == null) {
            break;
          }
          dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
        }
        return addedOk;
      }

      return super.addSubtreeToUpdateByElement(element);
    }

  }
}