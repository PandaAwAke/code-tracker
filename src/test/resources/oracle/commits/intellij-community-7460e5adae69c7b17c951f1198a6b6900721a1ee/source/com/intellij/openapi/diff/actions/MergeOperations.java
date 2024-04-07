package com.intellij.openapi.diff.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;

public class MergeOperations {
  private final DiffPanelImpl myDiffPanel;
  private final FragmentSide mySide;
  private static final ArrayList<Operation> NO_OPERATIONS = new ArrayList<Operation>(0);
  private static final Condition<Fragment> NOT_EQUAL_FRAGMENT = new Condition<Fragment>() {
          public boolean value(Fragment fragment) {
            return fragment.getType() != null;
          }
        };

  public MergeOperations(DiffPanelImpl diffPanel, FragmentSide side) {
    myDiffPanel = diffPanel;
    mySide = side;
  }

  public ArrayList<Operation> getOperations() {
    Fragment fragment = getCurrentFragment();
    if (fragment == null) return NO_OPERATIONS;
    ArrayList<Operation> operations = new ArrayList<Operation>(3);
    TextRange range = fragment.getRange(mySide);
    if (range.getLength() > 0) {
      if (isWritable(mySide)) operations.add(removeOperation(range, getDocument()));
      TextRange otherRange = fragment.getRange(mySide.otherSide());
      boolean otherIsWritable = isWritable(mySide.otherSide());
      if (otherIsWritable) operations.add(insertOperation(range, otherRange.getEndOffset(), getDocument(), getOtherDocument()));
      if (otherRange.getLength() > 0 && otherIsWritable) operations.add(replaceOperation(range, otherRange, getDocument(), getOtherDocument()));
    }
    return operations;
  }

  private boolean isWritable(FragmentSide side) {
    Editor editor = myDiffPanel.getEditor(side);
    return !editor.isViewer() && editor.getDocument().isWritable();
  }

  public void selectSuggestion() {
    Fragment fragment = getCurrentFragment();
    if (fragment == null) return;
    setSelection(fragment, mySide);
    setSelection(fragment, mySide.otherSide());
  }

  private void setSelection(Fragment fragment, FragmentSide side) {
    TextRange range = fragment.getRange(side);
    if (range.getLength() > 0)
    myDiffPanel.getEditor(side).getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
  }

  private static Operation replaceOperation(TextRange range, TextRange otherRange, Document document, Document otherDocument) {
    Operation operation = new Operation("Replace", GutterActionRenderer.REPLACE_ARROW);
    operation.addModification(replaceModification(range, document, otherRange, otherDocument));
    return operation;
  }

  public static Operation mostSensible(Document document, Document otherDocument, TextRange range, TextRange otherRange) {
    if (!document.isWritable() && !otherDocument.isWritable()) return null;
    if (range.getLength() != 0) {
      if (otherDocument.isWritable())
        return otherRange.getLength() != 0 ?
               replaceOperation(range, otherRange, document, otherDocument) :
               insertOperation(range, otherRange.getEndOffset(), document, otherDocument);
      else return otherRange.getLength() == 0 ? removeOperation(range, document) : null;
    }
    return null;
  }

  private static Runnable replaceModification(TextRange range, Document document,
                                       final TextRange otherRange, final Document otherDocument) {
    final String replacement = getSubstring(document, range);
    return new Runnable() {
      public void run() {
        otherDocument.replaceString(otherRange.getStartOffset(), otherRange.getEndOffset(), replacement);
      }
    };
  }

  private static Operation insertOperation(TextRange range, int offset, Document document, Document otherDocument) {
    Operation operation = new Operation("Insert", GutterActionRenderer.REPLACE_ARROW);
    operation.addModification(insertModification(range, document, offset, otherDocument));
    return operation;
  }

  private static Runnable insertModification(TextRange range, Document document,
                                      final int offset, final Document otherDocument) {
    final String insertion = getSubstring(document, range);
    return new Runnable(){
      public void run() {
        otherDocument.insertString(offset, insertion);
      }
    };
  }

  private static String getSubstring(Document document, TextRange range) {
    return document.getText().substring(range.getStartOffset(), range.getEndOffset());
  }

  private Document getOtherDocument() {
    return myDiffPanel.getEditor(mySide.otherSide()).getDocument();
  }

  private static Operation removeOperation(TextRange range, Document document) {
    Operation operation = new Operation("Remove", GutterActionRenderer.REMOVE_CROSS);
    operation.addModification(removeModification(range, document));
    return operation;
  }

  private static Runnable removeModification(final TextRange range, final Document document) {
    return new Runnable(){
      public void run() {
        document.deleteString(range.getStartOffset(), range.getEndOffset());
      }
    };
  }

  private Document getDocument() {
    return myDiffPanel.getEditor(mySide).getDocument();
  }

  public Fragment getCurrentFragment() {
    FragmentList fragments = myDiffPanel.getFragments();
    int caretPosition = myDiffPanel.getEditor(mySide).getCaretModel().getOffset();
    return fragments.getFragmentAt(caretPosition, mySide, NOT_EQUAL_FRAGMENT);
  }

  public static class Operation {
    private final String myName;
    private final ArrayList<Runnable> myModifications = new ArrayList<Runnable>();
    private final Icon myGlutterIcon;

    public Operation(String name, Icon icon) {
      myName = name;
      myGlutterIcon = icon;
    }

    public Icon getGlutterIcon() {
      return myGlutterIcon;
    }

    public String getName() {
      return myName;
    }

    private void addModification(Runnable modification) {
      myModifications.add(modification);
    }

    public void perform(final Project project) {
      for (Iterator<Runnable> iterator = myModifications.iterator(); iterator.hasNext();) {
        final Runnable modification = iterator.next();
        ApplicationManager.getApplication().runWriteAction(new Runnable(){
          public void run() {
            CommandProcessor.getInstance().executeCommand(project, modification, getName(), null);
          }
        });
      }
    }
  }
}
