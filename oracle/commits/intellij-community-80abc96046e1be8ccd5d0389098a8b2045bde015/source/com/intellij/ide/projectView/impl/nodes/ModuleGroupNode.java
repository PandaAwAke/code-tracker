package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.*;

public abstract class ModuleGroupNode extends ProjectViewNode<ModuleGroup> {
  private static final Icon OPEN_ICON = IconLoader.getIcon("/nodes/moduleGroupOpen.png");
  private static final Icon CLOSED_ICON = IconLoader.getIcon("/nodes/moduleGroupClosed.png");

  public ModuleGroupNode(final Project project, final ModuleGroup value, final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }
   public ModuleGroupNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (ModuleGroup)value, viewSettings);
  }

  protected abstract Class<? extends AbstractTreeNode> getModuleNodeClass();
  protected abstract ModuleGroupNode createModuleGroupNode(ModuleGroup moduleGroup);

  public Collection<AbstractTreeNode> getChildren() {
    final Collection<ModuleGroup> childGroups = getValue().childGroups(getProject());
    final List<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    for (Iterator iterator = childGroups.iterator(); iterator.hasNext();) {
      ModuleGroup moduleGroup = (ModuleGroup)iterator.next();
      result.add(createModuleGroupNode(moduleGroup));
    }
    Module[] modules = getValue().modulesInGroup(getProject(), false);
    final List<AbstractTreeNode> childModules = ProjectViewNode.wrap(Arrays.asList(modules), getProject(), getModuleNodeClass(), getSettings());
    result.addAll(childModules);
    return result;
  }

  public boolean contains(VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    final String[] groupPath = getValue().getGroupPath();
    presentation.setPresentableText(groupPath[groupPath.length-1]);
    presentation.setOpenIcon(OPEN_ICON);
    presentation.setClosedIcon(CLOSED_ICON);    
  }

  public String getTestPresentation() {
    return "Group: " + getValue();
  }

  public String getToolTip() {
    return "Module Group";
  }
}
