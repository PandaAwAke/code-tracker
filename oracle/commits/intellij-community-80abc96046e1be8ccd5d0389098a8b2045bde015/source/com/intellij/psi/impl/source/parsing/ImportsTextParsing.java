package com.intellij.psi.impl.source.parsing;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;

/**
 *
 */
public class ImportsTextParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.ImportsTextParsing");

  public ImportsTextParsing(JavaParsingContext context) {
    super(context);
  }

  /**
   * @stereotype chameleon transforming
   */
  public TreeElement parseImportsText(PsiManager manager, Lexer lexer, char[] buffer, int startOffset, int endOffset, int state) {
    if (lexer == null){
      lexer = new JavaLexer(manager.getEffectiveLanguageLevel());
    }
    FilterLexer filterLexer = new FilterLexer(lexer, new FilterLexer.SetFilter(WHITE_SPACE_OR_COMMENT_BIT_SET));
    if (state < 0) filterLexer.start(buffer, startOffset, endOffset);
    else filterLexer.start(buffer, startOffset, endOffset, state);

    final FileElement dummyRoot = new DummyHolder(manager, null, myContext.getCharTable()).getTreeElement();

    CompositeElement invalidElementsGroup = null;
    while(filterLexer.getTokenType() != null){
      TreeElement element = (TreeElement)parseImportStatement(filterLexer);
      if (element != null){
        TreeUtil.addChildren(dummyRoot, element);
        invalidElementsGroup = null;
        continue;
      }

      if (invalidElementsGroup == null){
        invalidElementsGroup = Factory.createErrorElement("Unexpected token");
        TreeUtil.addChildren(dummyRoot, invalidElementsGroup);
      }
      TreeUtil.addChildren(invalidElementsGroup, ParseUtil.createTokenElement(filterLexer, myContext.getCharTable()));
      filterLexer.advance();
    }

    ParseUtil.insertMissingTokens(dummyRoot, lexer, startOffset, endOffset, -1, ParseUtil.WhiteSpaceAndCommentsProcessor.INSTANCE, myContext);
    return (TreeElement)dummyRoot.getFirstChildNode();
  }

  private ASTNode parseImportStatement(FilterLexer lexer) {
    if (lexer.getTokenType() != IMPORT_KEYWORD) return null;

    final TreeElement importToken = ParseUtil.createTokenElement(lexer, myContext.getCharTable());
    lexer.advance();
    final CompositeElement statement;
    final boolean isStatic;
    if (lexer.getTokenType() != STATIC_KEYWORD) {
      statement = Factory.createCompositeElement(IMPORT_STATEMENT);
      TreeUtil.addChildren(statement, importToken);
      isStatic = false;
    }
    else {
      statement = Factory.createCompositeElement(IMPORT_STATIC_STATEMENT);
      TreeUtil.addChildren(statement, importToken);
      TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
      isStatic = true;
    }

    if (lexer.getTokenType() != IDENTIFIER){
      TreeUtil.addChildren(statement, Factory.createErrorElement("Identifier expected"));
      return statement;
    }

    CompositeElement refElement = parseJavaCodeReference(lexer, true, false);
    final TreeElement refParameterList = (TreeElement)refElement.getLastChildNode();
    if (refParameterList.getTreePrev().getElementType() == ERROR_ELEMENT){
      final ASTNode qualifier = refElement.findChildByRole(ChildRole.QUALIFIER);
      LOG.assertTrue(qualifier != null);
      TreeUtil.remove(refParameterList.getTreePrev());
      TreeUtil.remove(refParameterList);
      TreeUtil.addChildren(statement, (TreeElement)qualifier);
      if (lexer.getTokenType() == ASTERISK){
        TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
        lexer.advance();
      }
      else{
        TreeUtil.addChildren(statement, Factory.createErrorElement("Identifier or '*' expected"));
        return statement;
      }
    }
    else{
      if (isStatic) {
        // convert JAVA_CODE_REFERENCE into IMPORT_STATIC_REFERENCE
        refElement = convertToImportStaticReference(refElement);
      }
      TreeUtil.addChildren(statement, refElement);
    }

    if (lexer.getTokenType() == SEMICOLON){
      TreeUtil.addChildren(statement, ParseUtil.createTokenElement(lexer, myContext.getCharTable()));
      lexer.advance();
    }
    else{
      TreeUtil.addChildren(statement, Factory.createErrorElement("';' expected"));
    }

    return statement;
  }

  public CompositeElement convertToImportStaticReference(CompositeElement refElement) {
    final CompositeElement importStaticReference = Factory.createCompositeElement(IMPORT_STATIC_REFERENCE);
    final CompositeElement referenceParameterList = (CompositeElement)refElement.findChildByRole(ChildRole.REFERENCE_PARAMETER_LIST);
    TreeUtil.addChildren(importStaticReference, (TreeElement)refElement.getFirstChildNode());
    if (referenceParameterList != null) {
      if (referenceParameterList.getFirstChildNode() == null) {
        TreeUtil.remove(referenceParameterList);
      }
      else {
        final CompositeElement errorElement = Factory.createErrorElement("Unexpected token");
        TreeUtil.replaceWithList(referenceParameterList, errorElement);
        TreeUtil.addChildren(errorElement, referenceParameterList);
      }
    }
    refElement = importStaticReference;
    return refElement;
  }
}
