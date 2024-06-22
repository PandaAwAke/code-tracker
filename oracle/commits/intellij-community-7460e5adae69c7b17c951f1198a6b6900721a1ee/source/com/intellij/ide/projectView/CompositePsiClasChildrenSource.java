package com.intellij.ide.projectView;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import java.util.List;

public class CompositePsiClasChildrenSource implements PsiClassChildrenSource {
  private final PsiClassChildrenSource[] mySources;

  public CompositePsiClasChildrenSource(PsiClassChildrenSource[] sources) {
    mySources = sources;
  }

  public void addChildren(PsiClass psiClass, List<PsiElement> children) {
    for (int i = 0; i < mySources.length; i++) {
      PsiClassChildrenSource source = mySources[i];
      source.addChildren(psiClass, children);
    }
  }
}
