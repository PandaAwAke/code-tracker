package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.lang.ASTNode;

/**
 *
 */
public class FileTextParsing extends Parsing {
  public FileTextParsing(JavaParsingContext context) {
    super(context);
  }

  public static TreeElement parseFileText(PsiManager manager, Lexer lexer, char[] buffer, int startOffset, int endOffset, CharTable table) {
    return parseFileText(manager, lexer, buffer, startOffset, endOffset, false, table);
  }

  private static final TokenSet IMPORT_LIST_STOPPER_BIT_SET = TokenSet.create(new IElementType[]{CLASS_KEYWORD, INTERFACE_KEYWORD, ENUM_KEYWORD, ASPECT_ASPECT, AT});

  public static TreeElement parseFileText(PsiManager manager, Lexer lexer, char[] buffer, int startOffset, int endOffset, boolean skipHeader, CharTable table) {
    if (lexer == null){
      lexer = new JavaLexer(manager.getEffectiveLanguageLevel());
    }
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    filterLexer.start(buffer, startOffset, endOffset);
    final FileElement dummyRoot = new DummyHolder(manager, null, table).getTreeElement();
    JavaParsingContext context = new JavaParsingContext(dummyRoot.getCharTable(), manager.getEffectiveLanguageLevel());

    if (!skipHeader){
      TreeElement packageStatement = (TreeElement)context.getFileTextParsing().parsePackageStatement(filterLexer);
      if (packageStatement != null) {
        TreeUtil.addChildren(dummyRoot, packageStatement);
      }

      final TreeElement importList = (TreeElement)context.getFileTextParsing().parseImportList(filterLexer);
      TreeUtil.addChildren(dummyRoot, importList);
    }

    CompositeElement invalidElementsGroup = null;
    while (true) {
      if (filterLexer.getTokenType() == null) break;

      if (filterLexer.getTokenType() == ElementType.SEMICOLON){
        TreeUtil.addChildren(dummyRoot, ParseUtil.createTokenElement(filterLexer, dummyRoot.getCharTable()));
        filterLexer.advance();
        invalidElementsGroup = null;
        continue;
      }

      TreeElement first = context.getDeclarationParsing().parseDeclaration(filterLexer, DeclarationParsing.Context.FILE_CONTEXT);
      if (first != null) {
        TreeUtil.addChildren(dummyRoot, first);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement("'class' or 'interface' expected");
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }
      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, context.getCharTable()));
      filterLexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, context);
    return (TreeElement)dummyRoot.getFirstChildNode();
  }

  public ASTNode parseImportList(Lexer lexer) {
    CompositeElement importList = Factory.createCompositeElement(IMPORT_LIST);
    if (lexer.getTokenType() == IMPORT_KEYWORD) {
      int startPos = lexer.getTokenStart();
      int lastPos = lexer.getTokenEnd();
      boolean prevImportKeyword = true;
      while (true) {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == IMPORT_KEYWORD) {
          prevImportKeyword = true;
        }
        else if (prevImportKeyword && tokenType == STATIC_KEYWORD) {
          prevImportKeyword = false;
        }
        else {
          prevImportKeyword = WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(tokenType);
          if (tokenType == null || IMPORT_LIST_STOPPER_BIT_SET.isInSet(tokenType) || MODIFIER_BIT_SET.isInSet(tokenType)) break;
        }
        lastPos = lexer.getTokenEnd();
        lexer.advance();
      }
      LeafElement chameleon = Factory.createLeafElement(IMPORT_LIST, lexer.getBuffer(), startPos, lastPos, lexer.getState(), myContext.getCharTable());
      return chameleon;
    }

    return importList;
  }

  public ASTNode parsePackageStatement(Lexer lexer) {
    final LexerPosition startPos = lexer.getCurrentPosition();
    CompositeElement packageStatement = Factory.createCompositeElement(PACKAGE_STATEMENT);

    if (lexer.getTokenType() != PACKAGE_KEYWORD) {
      FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
      TreeUtil.addChildren(packageStatement, myContext.getDeclarationParsing().parseAnnotationList(filterLexer));
      if (lexer.getTokenType() != PACKAGE_KEYWORD) {
        lexer.restore(startPos);
        return null;
      }
    }

    TreeUtil.addChildren(packageStatement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
    lexer.advance();
    TreeElement packageReference = parseJavaCodeReference(lexer, true, false);
    if (packageReference == null) {
      lexer.restore(startPos);
      return null;
    }
    TreeUtil.addChildren(packageStatement, packageReference);
    if (lexer.getTokenType() == SEMICOLON) {
      TreeUtil.addChildren(packageStatement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    } else {
      TreeUtil.addChildren(packageStatement, Factory.createErrorElement("';' expected"));
    }
    return packageStatement;
  }
}



