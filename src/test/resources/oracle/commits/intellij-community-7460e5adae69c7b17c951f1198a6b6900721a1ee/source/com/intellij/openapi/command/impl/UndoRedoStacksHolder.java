package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 * author: lesya
 */

class UndoRedoStacksHolder {
  private HashMap<DocumentReference, LinkedList<UndoableGroup>> myStackOwnerToStack = new HashMap<DocumentReference, LinkedList<UndoableGroup>>();
  private LinkedList<UndoableGroup> myGlobalStack = new LinkedList<UndoableGroup>();

  private final Key<LinkedList<UndoableGroup>> STACK_IN_DOCUMENT_KEY = Key.create("STACK_IN_DOCUMENT_KEY");

  public LinkedList<UndoableGroup> getStack(Document document) {
    return getStack(DocumentReferenceByDocument.createDocumentReference(document));
  }

  public LinkedList<UndoableGroup> getStack(DocumentReference docRef) {
    if (docRef == null) {
      throw new IllegalArgumentException("docRef cannot be null");
    }

    LinkedList<UndoableGroup> result;
    if (docRef.getFile() != null) {
      result = myStackOwnerToStack.get(docRef);
      if (result == null) {
        result = new LinkedList<UndoableGroup>();
        myStackOwnerToStack.put(docRef, result);
      }
    }
    else {
      result = docRef.getDocument().getUserData(STACK_IN_DOCUMENT_KEY);
      if (result == null) {
        result = new LinkedList<UndoableGroup>();
        docRef.getDocument().putUserData(STACK_IN_DOCUMENT_KEY, result);
      }
    }

    return result;
  }

  public void clearFileQueue(DocumentReference docRef) {
    final LinkedList<UndoableGroup> queue = getStack(docRef);
    clear(queue);
    if (docRef.getFile() != null) {
      myStackOwnerToStack.remove(docRef);
    }
    else {
      docRef.getDocument().putUserData(STACK_IN_DOCUMENT_KEY, null);
    }
  }

  private void clear(LinkedList<UndoableGroup> stack) {
    for (Iterator each = stack.iterator(); each.hasNext();) {
      UndoableGroup undoableGroup = (UndoableGroup)each.next();
      undoableGroup.dispose();
    }
    stack.clear();
  }

  public LinkedList<UndoableGroup> getGlobalStack() {
    return myGlobalStack;
  }

  public void clearGlobalStack() {
    myGlobalStack.clear();
  }

  public void addToLocalStack(DocumentReference docRef, UndoableGroup commandInfo) {
    addToStack(getStack(docRef), commandInfo, UndoManagerImpl.LOCAL_UNDO_LIMIT);
  }

  public void addToGlobalStack(UndoableGroup commandInfo) {
    addToStack(getGlobalStack(), commandInfo, UndoManagerImpl.GLOBAL_UNDO_LIMIT);
  }

  private void addToStack(LinkedList stack, UndoableGroup commandInfo, int limit) {
    stack.addLast(commandInfo);
    while (stack.size() > limit) {
      UndoableGroup undoableGroup = (UndoableGroup)stack.removeFirst();
      undoableGroup.dispose();
    }
  }

  public void clearEditorStack(FileEditor editor) {
    Document[] documents = TextEditorProvider.getDocuments(editor);
    for (int i = 0; i < documents.length; i++) {
      clear(getStack(DocumentReferenceByDocument.createDocumentReference(documents[i])));
    }

  }

  public Set<DocumentReference> getAffectedDocuments() {
    return myStackOwnerToStack.keySet();
  }

  public Set<DocumentReference> getDocsInGlobalQueue() {
    HashSet<DocumentReference> result = new HashSet<DocumentReference>();
    for (Iterator<UndoableGroup> iterator = getGlobalStack().iterator(); iterator.hasNext();) {
      UndoableGroup group = iterator.next();
      result.addAll(group.getAffectedDocuments());
    }
    return result;
  }

  public int getYoungestCommandAge(DocumentReference docRef) {
    final LinkedList<UndoableGroup> stack = getStack(docRef);
    if (stack.isEmpty()) return 0;
    return Math.max(stack.getFirst().getCommandCounter(), stack.getLast().getCommandCounter());
  }

  public Collection<DocumentReference> getGlobalStackAffectedDocuments() {
    Collection<DocumentReference> result = new HashSet<DocumentReference>();
    for (Iterator each = myGlobalStack.iterator(); each.hasNext();) {
      UndoableGroup undoableGroup = (UndoableGroup)each.next();
      result.addAll(undoableGroup.getAffectedDocuments());
    }
    return result;
  }

  public void dropHistory() {
    clearGlobalStack();
    myStackOwnerToStack.clear();
  }
}
