package com.intellij.execution.util;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;

import java.util.ArrayList;

public class RefactoringElementListenerComposite implements RefactoringElementListener {
  private ArrayList<RefactoringElementListener> myListeners;

  public RefactoringElementListenerComposite(){
    myListeners = new ArrayList<RefactoringElementListener>();
  }

  public void addListener(final RefactoringElementListener listener){
    myListeners.add(listener);
  }

  public void elementMoved(final PsiElement newElement){
    for (int i = 0; i < myListeners.size(); i++) {
      myListeners.get(i).elementMoved(newElement);
    }
  }

  public void elementRenamed(final PsiElement newElement){
    for (int i = 0; i < myListeners.size(); i++) {
      (myListeners.get(i)).elementRenamed(newElement);
    }
  }
}
