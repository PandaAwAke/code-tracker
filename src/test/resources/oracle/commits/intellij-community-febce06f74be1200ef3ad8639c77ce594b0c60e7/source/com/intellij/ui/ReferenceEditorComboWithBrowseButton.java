package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author ven
 */
public class ReferenceEditorComboWithBrowseButton extends ComponentWithBrowseButton<EditorComboBox> implements TextAccessor {
  public ReferenceEditorComboWithBrowseButton(final ActionListener browseActionListener,
                                              final String text,
                                              @NotNull final PsiManager manager,
                                              boolean toAcceptClasses, final String recentsKey) {
    super(new EditorComboBox(createDocument(text, manager, toAcceptClasses), manager.getProject(), StdFileTypes.JAVA), browseActionListener);
    final List<String> recentEntries = RecentsManager.getInstance(manager.getProject()).getRecentEntries(recentsKey);
    if (recentEntries != null) {
      setHistory(ArrayUtil.toStringArray(recentEntries));
    }
    if (text != null && text.length() > 0) {
      prependItem(text);
    }
  }

  private static Document createDocument(final String text, PsiManager manager, boolean isClassesAccepted) {
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(manager.getProject()).findPackage("");
    final JavaCodeFragment fragment = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
    return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
  }

  public String getText(){
    return getChildComponent().getText().trim();
  }

  public void setText(final String text){
    getChildComponent().setText(text);
  }

  public boolean isEditable() {
    return !getChildComponent().getEditorEx().isViewer();
  }

  public void setHistory(String[] history) {
    getChildComponent().setHistory(history);
  }

  public void prependItem(String item) {
    getChildComponent().prependItem(item);
  }
}
