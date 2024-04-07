package com.intellij.packageDependencies.packageSet;

import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.TokenTypeEx;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.analysis.AnalysisScopeBundle;

public class PackageSetFactoryImpl extends PackageSetFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.packageDependencies.packageSet.PackageSetFactoryImpl");

  public PackageSetFactoryImpl() {}

  public PackageSet compile(String text) throws ParsingException {
    Lexer lexer = new FilterLexer(new JavaLexer(LanguageLevel.JDK_1_3),
                                  new FilterLexer.SetFilter(ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET));
    lexer.start(text.toCharArray());
    return new Parser(lexer).parse();
  }

  private static class Parser {
    private Lexer myLexer;

    public Parser(Lexer lexer) {
      myLexer = lexer;
    }

    public PackageSet parse() throws ParsingException {
      PackageSet set = parseUnion();
      if (myLexer.getTokenType() != null) error(AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText()));
      return set;
    }

    private PackageSet parseUnion() throws ParsingException {
      PackageSet result = parseIntersection();
      while (true) {
        if (myLexer.getTokenType() != TokenTypeEx.OROR) break;
        myLexer.advance();
        result = new UnionPackageSet(result, parseIntersection());
      }
      return result;
    }

    private PackageSet parseIntersection() throws ParsingException {
      PackageSet result = parseTerm();
      while (true) {
        if (myLexer.getTokenType() != TokenTypeEx.ANDAND) break;
        myLexer.advance();
        result = new IntersectionPackageSet(result, parseTerm());
      }
      return result;
    }

    private PackageSet parseTerm() throws ParsingException {
      if (myLexer.getTokenType() == TokenTypeEx.EXCL) {
        myLexer.advance();
        return new ComplementPackageSet(parseTerm());
      }

      if (myLexer.getTokenType() == TokenTypeEx.LPARENTH) return parseParenthesized();
      if (myLexer.getTokenType() == TokenTypeEx.IDENTIFIER && myLexer.getBuffer()[myLexer.getTokenStart()] == '$') {
        NamedPackageSetReference namedPackageSetReference = new NamedPackageSetReference(getTokenText());
        myLexer.advance();
        return namedPackageSetReference;
      }
      return parsePattern();
    }

    private PackageSet parsePattern() throws ParsingException {
      String scope = parseScope();
      String modulePattern = parseModulePattern();

      if (myLexer.getTokenType() == TokenTypeEx.COLON) {
        if (scope == PatternPackageSet.SCOPE_ANY && modulePattern == null) {
          error(AnalysisScopeBundle.message("error.packageset.common.expectations"));
        }
        myLexer.advance();
      }

      String pattern = parseAspectJPattern();

      return new PatternPackageSet(pattern, scope, modulePattern);
    }

    private String parseScope() {
      if (myLexer.getTokenType() != TokenTypeEx.IDENTIFIER) return PatternPackageSet.SCOPE_ANY;
      String id = getTokenText();
      String scope = PatternPackageSet.SCOPE_ANY;
      if (PatternPackageSet.SCOPE_SOURCE.equals(id)) {
        scope = PatternPackageSet.SCOPE_SOURCE;
      }
      if (PatternPackageSet.SCOPE_TEST.equals(id)) {
        scope = PatternPackageSet.SCOPE_TEST;
      }
      if (PatternPackageSet.SCOPE_LIBRARY.equals(id)) {
        scope = PatternPackageSet.SCOPE_LIBRARY;
      }

      char[] buf = myLexer.getBuffer();
      int end = myLexer.getTokenEnd();
      int bufferEnd = myLexer.getBufferEnd();

      if (scope == PatternPackageSet.SCOPE_ANY || end >= bufferEnd || buf[end] != ':' && buf[end] != '[') {
        return PatternPackageSet.SCOPE_ANY;
      }

      myLexer.advance();

      return scope;
    }

    private String parseAspectJPattern() throws ParsingException {
      StringBuffer pattern = new StringBuffer();
      boolean wasIdentifier = false;
      while (true) {
        if (myLexer.getTokenType() == TokenTypeEx.DOT) {
          pattern.append('.');
          wasIdentifier = false;
        }
        else if (myLexer.getTokenType() == TokenTypeEx.ASTERISK) {
          pattern.append('*');
          wasIdentifier = false;
        }
        else if (myLexer.getTokenType() == TokenTypeEx.IDENTIFIER) {
          if (wasIdentifier) error(AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText()));
          wasIdentifier = true;
          pattern.append(getTokenText());
        }
        else {
          break;
        }
        myLexer.advance();
      }

      if (pattern.length() == 0) {
        error(AnalysisScopeBundle.message("error.packageset.pattern.expectations"));
      }

      return pattern.toString();
    }

    private String getTokenText() {
      int start = myLexer.getTokenStart();
      int end = myLexer.getTokenEnd();
      return new String(myLexer.getBuffer(), start, end - start);
    }

    private String parseModulePattern() throws ParsingException {
      if (myLexer.getTokenType() != TokenTypeEx.LBRACKET) return null;
      myLexer.advance();
      StringBuffer pattern = new StringBuffer();
      while (true) {
        if (myLexer.getTokenType() == TokenTypeEx.RBRACKET) {
          myLexer.advance();
          break;
        }

        if (myLexer.getTokenType() == TokenTypeEx.IDENTIFIER || myLexer.getTokenType() == TokenTypeEx.ASTERISK) {
          pattern.append(getTokenText());
          myLexer.advance();
        }
        else {
          error(AnalysisScopeBundle.message("error.packageset.module.pattern.expectations"));
        }
      }
      return pattern.toString();
    }

    private PackageSet parseParenthesized() throws ParsingException {
      LOG.assertTrue(myLexer.getTokenType() == TokenTypeEx.LPARENTH);
      myLexer.advance();

      PackageSet result = parseUnion();
      if (myLexer.getTokenType() != TokenTypeEx.RPARENTH) error(AnalysisScopeBundle.message("error.packageset.).expectations"));
      myLexer.advance();

      return result;
    }

    private void error(String message) throws ParsingException {
      throw new ParsingException(
        AnalysisScopeBundle.message("error.packageset.position.parsing.error", message, (myLexer.getTokenStart() + 1)));
    }
  }
}