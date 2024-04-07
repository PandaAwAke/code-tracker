
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.pom.Navigatable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerUtil;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

abstract class OccurenceNavigatorActionBase extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;

    OccurenceNavigator navigator = getNavigator(e.getDataContext());
    if (navigator == null) {
      return;
    }
    OccurenceNavigator.OccurenceInfo occurenceInfo = go(navigator);
    if (occurenceInfo == null) {
      return;
    }
    Navigatable descriptor = occurenceInfo.getNavigateable();
    if (descriptor != null) descriptor.navigate(false);
    if(occurenceInfo.getOccurenceNumber()==-1||occurenceInfo.getOccurencesCount()==-1){
      return;
    }
    WindowManager.getInstance().getStatusBar(project).setInfo("Occurrence "+occurenceInfo.getOccurenceNumber()+" of "+occurenceInfo.getOccurencesCount());
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      // make it invisible only in main menu to avoid initial invisibility in toolbars
      presentation.setVisible(!ActionPlaces.MAIN_MENU.equals(event.getPlace()));
      return;
    }
    OccurenceNavigator navigator = getNavigator(event.getDataContext());
    if (navigator == null) {
      presentation.setEnabled(false);
      // make it invisible only in main menu to avoid initial invisibility in toolbars
      presentation.setVisible(!ActionPlaces.MAIN_MENU.equals(event.getPlace()));
      return;
    }
    presentation.setVisible(true);
    presentation.setEnabled(hasOccurenceToGo(navigator));
    presentation.setText(getDescription(navigator));
  }

  protected abstract OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator);

  protected abstract boolean hasOccurenceToGo(OccurenceNavigator navigator);

  protected abstract String getDescription(OccurenceNavigator navigator);

  protected OccurenceNavigator getNavigator(DataContext dataContext) {
    ContentManager contentManager = ContentManagerUtil.getContentManagerFromContext(dataContext, false);
    if (contentManager != null) {
      Content content = contentManager.getSelectedContent();
      if (content == null) return null;
      JComponent component = content.getComponent();
      return findNavigator(component);
    }

    OccurenceNavigator occurenceNavigator = (OccurenceNavigator)getOccurenceNavigatorFromContext(dataContext);
    return occurenceNavigator;
  }

  private OccurenceNavigator findNavigator(JComponent parent) {
    LinkedList<JComponent> queue = new LinkedList<JComponent>();
    queue.addLast(parent);
    for (; !queue.isEmpty(); ) {
      JComponent component = queue.removeFirst();
      if (component instanceof OccurenceNavigator) return (OccurenceNavigator) component;
      if (component instanceof JTabbedPane) {
        queue.addLast((JComponent) ((JTabbedPane) component).getSelectedComponent());
      }
      else {
        for (int i = 0; i < component.getComponentCount(); i++) {
          Component child = component.getComponent(i);
          if (!(child instanceof JComponent)) continue;
          queue.addLast((JComponent) child);
        }
      }
    }
    return null;
  }

  private static Component getOccurenceNavigatorFromContext(DataContext dataContext) {
    Window window = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();

    if (window != null) {
      Component component = window.getFocusOwner();
      for (Component c = component; c != null; c = c.getParent()) {
        if (c instanceof OccurenceNavigator) {
          return c;
        }
      }
    }

    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return null;
    }

    ToolWindowManagerEx mgr = ToolWindowManagerEx.getInstanceEx(project);
    if (!mgr.isEditorComponentActive()) {
      return null;
    }

    String id = mgr.getLastActiveToolWindowId();
    if (id == null) {
      return null;
    }

    Component component = mgr.getToolWindow(id).getComponent();
    return component instanceof OccurenceNavigator ? component : null;
  }

}
