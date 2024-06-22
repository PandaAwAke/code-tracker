package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import java.util.List;

public class ShowErrorDescriptionHandler implements CodeInsightActionHandler {

  public void invoke(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo info = codeAnalyzer.findHighlightByOffset(editor.getDocument(), offset, false);
    if (info != null) {
      DaemonTooltipUtil.showInfoTooltip(info, editor, editor.getCaretModel().getOffset());
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
