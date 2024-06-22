package com.intellij.psi.impl.light;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

/**
 *
 */
public class LightKeyword extends LightElement implements PsiKeyword, PsiJavaToken {
  private String myText;

  public LightKeyword(PsiManager manager, String text) {
    super(manager);
    myText = text;
  }

  public String getText(){
    return myText;
  }

  public IElementType getTokenType(){
    Lexer lexer = new JavaLexer(myManager.getEffectiveLanguageLevel());
    lexer.start(myText.toCharArray());
    return lexer.getTokenType();
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitKeyword(this);
  }

  public PsiElement copy(){
    return new LightKeyword(getManager(), myText);
  }

  public String toString(){
    return "PsiKeyword:" + getText();
  }
}
