package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class BackspaceHandler extends EditorWriteActionHandler {
  private EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (!handleBackspace(editor, dataContext)){
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean handleBackspace(Editor editor, DataContext dataContext){
    Project project = (Project)DataManager.getInstance().getDataContext(editor.getContentComponent()).getData(DataConstants.PROJECT);
    if (project == null) return false;

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) return false;

    FileType fileType = file.getFileType();
    final TypedHandler.QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(
      file.canContainJavaCode() && fileType != StdFileTypes.JSP? StdFileTypes.JAVA:
      fileType
    );
    if (quoteHandler==null) return false;

    if (editor.getSelectionModel().hasSelection()) return false;

    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    char c = chars.charAt(offset);

    myOriginalHandler.execute(editor, dataContext);

    if (offset >= editor.getDocument().getTextLength()) return true;
    chars = editor.getDocument().getCharsSequence();
    if (c == '(' || c == '['){
      char c1 = chars.charAt(offset);
      if (c == '(' ? c1 != ')' : c1 != ']') return true;

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      BraceMatchingUtil.BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType);
      if (!braceMatcher.isLBraceToken(iterator, chars, fileType) &&
          !braceMatcher.isRBraceToken(iterator, chars, fileType)
          ) {
        return true;
      }

      int rparenOffset = BraceMatchingUtil.findRightmostRParen(iterator, braceMatcher.getTokenType(c == '(' ? ')' : ']', iterator),chars,fileType);
      if (rparenOffset >= 0){
        iterator = ((EditorEx)editor).getHighlighter().createIterator(rparenOffset);
        boolean matched = BraceMatchingUtil.matchBrace(chars, fileType, iterator, false);
        if (matched) return true;
      }

      editor.getDocument().deleteString(offset, offset + 1);
    }
    else if (c == '"' || c == '\''){
      char c1 = chars.charAt(offset);
      if (c1 != c) return true;

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      if (!quoteHandler.isOpeningQuote(iterator,offset)) return true;

      if (file.canContainJavaCode()) {
        // TODO: does this mean == quoteHandler.isClosingQuote(....)
        char lastChar = chars.charAt(iterator.getEnd() - 1);
        boolean isClosed = iterator.getEnd() - iterator.getStart() > 1 && lastChar == c;
        if (isClosed) return true;
      }

      editor.getDocument().deleteString(offset, offset + 1);
    }

    return true;
  }
}