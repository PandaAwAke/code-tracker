package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;

public class XmlLexer extends MergingLexerAdapter {
  private final static TokenSet TOKENS_TO_MERGE = TokenSet.create(new IElementType[]{
    XmlTokenType.XML_DATA_CHARACTERS,
    XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
    XmlTokenType.XML_PI_TARGET,
  });

  public XmlLexer() {
    super(new _XmlLexer(), TOKENS_TO_MERGE);
  }
}
