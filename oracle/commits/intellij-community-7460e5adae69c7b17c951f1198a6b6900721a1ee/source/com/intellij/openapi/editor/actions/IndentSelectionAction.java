/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 13, 2002
 * Time: 10:29:01 PM
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class IndentSelectionAction extends EditorAction {
  public IndentSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);
      indentSelection(editor, project);
    }
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(isEnabled(editor));
  }

  private boolean isEnabled(Editor editor) {
    return editor.getSelectionModel().hasSelection() && !editor.isOneLineMode();
  }

  private static void indentSelection(Editor editor, Project project) {
    if(!editor.getSelectionModel().hasSelection())
      return;

    int oldSelectionStart = editor.getSelectionModel().getSelectionStart();
    int oldSelectionEnd = editor.getSelectionModel().getSelectionEnd();

    Document document = editor.getDocument();
    int startIndex = document.getLineNumber(oldSelectionStart);
    if(startIndex == -1) {
      startIndex = document.getLineCount() - 1;
    }
    int endIndex = document.getLineNumber(oldSelectionEnd);
    if(endIndex > 0 && document.getLineStartOffset(endIndex) == oldSelectionEnd) {
      endIndex --;
    }
    if(endIndex == -1) {
      endIndex = document.getLineCount() - 1;
    }
    VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
    final FileType fileType = vFile == null ? null : FileTypeManager.getInstance().getFileTypeByFile(vFile);
    int blockIndent = CodeStyleSettingsManager.getSettings(project).getIndentSize(fileType);
    for(int i=startIndex; i<=endIndex; i++) {
      EditorActionUtil.indentLine(project, editor, i, blockIndent);
    }
  }
}
