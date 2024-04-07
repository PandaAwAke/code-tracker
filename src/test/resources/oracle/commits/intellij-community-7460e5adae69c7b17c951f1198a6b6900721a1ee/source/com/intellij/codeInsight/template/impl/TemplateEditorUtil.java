package com.intellij.codeInsight.template.impl;

import com.intellij.lexer.CompositeLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerHighlighter;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.openapi.fileTypes.FileHighlighterBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class TemplateEditorUtil {
  public static Editor createEditor(boolean isReadOnly) {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document doc = editorFactory.createDocument("");
    Editor editor = (isReadOnly ? editorFactory.createViewer(doc) : editorFactory.createEditor(doc));

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    return editor;
  }

  public static void setHighlighter(Editor editor, TemplateContext templateContext) {
    int count1 =
      (templateContext.JAVA_CODE || templateContext.COMPLETION ? 1 : 0) +
      (templateContext.JAVA_COMMENT ? 1 : 0) +
      (templateContext.JAVA_STRING ? 1 : 0) +
      (templateContext.OTHER ? 1 : 0);
    int count2 =
      (templateContext.HTML ? 1 : 0) +
      (templateContext.XML ? 1 : 0) +
      (templateContext.JSP ? 1 : 0);
    int count = count1 + count2;

    FileType fileType;
    if ((templateContext.JAVA_CODE || templateContext.COMPLETION) && count == 1) {
      fileType = StdFileTypes.JAVA;
    }
    else if (templateContext.HTML && count1 == 0) {
      fileType = StdFileTypes.HTML;
    }
    else if (templateContext.XML && count1 == 0) {
      fileType = StdFileTypes.XML;
    }
    else if (templateContext.JSP && count1 == 0) {
      fileType = StdFileTypes.JSP;
    }
    else {
      fileType = StdFileTypes.PLAIN_TEXT;
    }

    FileHighlighter highlighter = createTemplateTextHighlighter(fileType.getHighlighter(null));
    ((EditorEx)editor).setHighlighter(new LexerHighlighter(highlighter, EditorColorsManager.getInstance().getGlobalScheme()));
  }

  private final static TokenSet TOKENS_TO_MERGE = TokenSet.create(new IElementType[]{TemplateTokenType.TEXT});

  private static FileHighlighter createTemplateTextHighlighter(final FileHighlighter original) {
    return new TemplateHighlighter(original);
  }

  private static class TemplateHighlighter extends FileHighlighterBase {
    private Lexer myLexer;
    private FileHighlighter myOriginalHighlighter;

    public TemplateHighlighter(FileHighlighter original) {
      myOriginalHighlighter = original;
      Lexer originalLexer = original.getHighlightingLexer();
      Lexer templateLexer = new TemplateTextLexer();
      templateLexer = new MergingLexerAdapter(templateLexer, TOKENS_TO_MERGE);

      myLexer = new CompositeLexer(originalLexer, templateLexer) {
        protected IElementType getCompositeTokenType(IElementType type1, IElementType type2) {
          if (type2 == TemplateTokenType.VARIABLE) {
            return type2;
          }
          else {
            return type1;
          }
        }
      };
    }

    public Lexer getHighlightingLexer() {
      return myLexer;
    }

    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
      if (tokenType == TemplateTokenType.VARIABLE) {
        return pack(myOriginalHighlighter.getTokenHighlights(tokenType), TemplateColors.TEMPLATE_VARIABLE_ATTRIBUTES);
      }

      return myOriginalHighlighter.getTokenHighlights(tokenType);
    }
  }
}