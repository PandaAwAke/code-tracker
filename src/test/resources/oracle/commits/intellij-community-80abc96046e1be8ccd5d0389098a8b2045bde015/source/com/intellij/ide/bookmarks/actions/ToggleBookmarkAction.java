package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.bookmarks.EditorBookmark;
import com.intellij.ide.commander.Commander;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;

public class ToggleBookmarkAction extends AnAction {
  public ToggleBookmarkAction() {
    super("Toggle Bookmark");
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;
    BookmarkManager bookmarkManager = BookmarkManager.getInstance(project);

    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
      // toggle editor bookmark if editor is active
      if (editor != null) {
        EditorBookmark bookmark = bookmarkManager.findEditorBookmark(editor.getDocument(), editor.getCaretModel().getLogicalPosition().line);
        if (bookmark == null) {
          bookmarkManager.addEditorBookmark(editor.getDocument(), editor.getCaretModel().getLogicalPosition().line, EditorBookmark.NOT_NUMBERED);
        }
        else {
          bookmarkManager.removeBookmark(bookmark);
        }
      }
      return;
    }
    String id=ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      PsiElement element = ProjectView.getInstance(project).getParentOfCurrentSelection();
      if (element != null) {
        bookmarkManager.addCommanderBookmark(element);
      }
      return;
    }
    if (id.equals(ToolWindowId.COMMANDER)) {
      AbstractTreeNode parentNode = Commander.getInstance(project).getActivePanel().getBuilder().getParentNode();
      final Object element = parentNode != null? parentNode.getValue() : null;
      if (element instanceof PsiElement) {
        bookmarkManager.addCommanderBookmark((PsiElement)element);
      }
      return;
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    String s = "Toggle Bookmark";

    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setText(s);
      return;
    }

    if (ToolWindowManager.getInstance(project).isEditorComponentActive()) {
      presentation.setEnabled(dataContext.getData(DataConstants.EDITOR) != null);
      presentation.setText(s);
      return;
    }

    ProjectView projectView = ProjectView.getInstance(project);
    presentation.setText("Set Bookmark");
    String id=ToolWindowManager.getInstance(project).getActiveToolWindowId();
    if (ToolWindowId.PROJECT_VIEW.equals(id)) {
      presentation.setEnabled(projectView.getParentOfCurrentSelection() != null);
    }
    else if (ToolWindowId.COMMANDER.equals(id)) {
      final AbstractTreeNode parentNode = Commander.getInstance(project).getActivePanel().getBuilder().getParentNode();
      final Object parentElement = parentNode != null? parentNode.getValue() : null;
      presentation.setEnabled(parentElement instanceof PsiElement);
    }
    else {
      presentation.setEnabled(false);
    }
  }
}