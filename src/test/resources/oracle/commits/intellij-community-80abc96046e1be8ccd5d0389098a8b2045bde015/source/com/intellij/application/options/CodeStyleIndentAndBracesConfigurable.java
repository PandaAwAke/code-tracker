package com.intellij.application.options;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleIndentAndBracesConfigurable extends CodeStyleAbstractConfigurable {
  public CodeStyleIndentAndBracesConfigurable(CodeStyleSettings settings, CodeStyleSettings cloneSettings) {
    super(settings, cloneSettings,"Alignment and Braces");
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
    return new CodeStyleIndentAndBracesPanel(settings);
  }

  public String getHelpTopic() {
    return "preferences.sourceCode.indentBrace";
  }
}