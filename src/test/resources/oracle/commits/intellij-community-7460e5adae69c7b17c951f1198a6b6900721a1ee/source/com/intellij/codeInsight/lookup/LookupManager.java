package com.intellij.codeInsight.lookup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

import java.beans.PropertyChangeListener;

public abstract class LookupManager {
  public static LookupManager getInstance(Project project){
    return project.getComponent(LookupManager.class);
  }

  public abstract Lookup showLookup(
      Editor editor,
      LookupItem[] items,
      String prefix,
      LookupItemPreferencePolicy itemPreferencePolicy,
      CharFilter filter);
  public abstract void hideActiveLookup();
  public abstract Lookup getActiveLookup();

  public static final String PROP_ACTIVE_LOOKUP = "activeLookup";

  public abstract void addPropertyChangeListener(PropertyChangeListener listener);
  public abstract void removePropertyChangeListener(PropertyChangeListener listener);

  public abstract PsiElement[] getAllElementsForItem(LookupItem item);

  public abstract boolean isDisposed();
}