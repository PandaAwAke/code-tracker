
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public abstract class TemplateManager {
  public static TemplateManager getInstance(Project project) {
    return project.getComponent(TemplateManager.class);
  }

  public abstract void startTemplate(Editor editor, Template template);

  public abstract void startTemplate(Editor editor, String selectionString, Template template);

  public abstract void startTemplate(Editor editor, Template template, TemplateStateListener listener);

  public abstract boolean startTemplate(Editor editor, char shortcutChar);

  public abstract int getContextType(PsiFile file, int offset);

  public abstract Template createTemplate(String key, String group);

  public abstract Template createTemplate(String key, String group, String text);

  public abstract Template getActiveTemplate(Editor editor);
}
