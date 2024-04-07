package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.diff.Diff;

public class DocumentEventImpl extends DocumentEvent {
  private final int myOffset;
  private CharSequence myOldString;
  private final int myOldLength;
  private CharSequence myNewString;
  private final int myNewLength;

  private boolean isOnlyOneLineChangedCalculated = false;
  private boolean isOnlyOneLineChanged;

  private boolean isStartOldIndexCalculated = false;
  private int myStartOldIndex;

  private final long myOldTimeStamp;
  private boolean myIsWholeDocReplaced = false;
  private Diff.Change myChange;

  public DocumentEventImpl(Document document,
                           int offset,
                           CharSequence oldString,
                           CharSequence newString,
                           long oldTimeStamp) {
    super(document);
    myOffset = offset;

    myOldString = oldString;
    if(myOldString == null) myOldString = "";
    myOldLength = myOldString.length();

    myNewString = newString;
    if(myNewString == null) myNewString = "";
    myNewLength = myNewString.length();

    myOldTimeStamp = oldTimeStamp;

    if(getDocument().getTextLength() == 0) {
      isOnlyOneLineChangedCalculated = true;
      isOnlyOneLineChanged = false;
    } else {
      myIsWholeDocReplaced = offset == 0 && document.getTextLength() == myOldLength;
    }
  }

  public int getOffset() {
    return myOffset;
  }

  public int getOldLength() {
    return myOldLength;
  }

  public int getNewLength() {
    return myNewLength;
  }

  public CharSequence getOldFragment() {
    return myOldString;
  }

  public CharSequence getNewFragment() {
    return myNewString;
  }

  public Document getDocument() {
    return (Document) getSource();
  }

  public int getStartOldIndex() {
    if(isStartOldIndexCalculated) return myStartOldIndex;

    isStartOldIndexCalculated = true;
    myStartOldIndex = getDocument().getLineNumber(myOffset);
    return myStartOldIndex;
  }

  public boolean isOnlyOneLineChanged() {
    if(isOnlyOneLineChangedCalculated) return isOnlyOneLineChanged;

    isOnlyOneLineChangedCalculated = true;
    isOnlyOneLineChanged = true;

    for(int i=0; i<myOldString.length(); i++) {
      if(myOldString.charAt(i) == '\n') {
        isOnlyOneLineChanged = false;
        break;
      }
    }

    if(isOnlyOneLineChanged) {
      for(int i=0; i<myNewString.length(); i++) {
        if(myNewString.charAt(i) == '\n') {
          isOnlyOneLineChanged = false;
          break;
        }
      }
    }
    return isOnlyOneLineChanged;
  }

  public long getOldTimeStamp() {
    return myOldTimeStamp;
  }

  public String toString() {
    return "DocumentEventImpl[myOffset=" + myOffset + ", myOldLength=" + myOldLength + ", myNewLength=" + myNewLength +", myOldString='" + myOldString + "', myNewString='" + myNewString + "']";
  }

  public boolean isWholeTextReplaced() {
    return myIsWholeDocReplaced;
  }

  public int translateLineViaDiff(int line) {
    if (myChange == null) buildDiff();
    if (myChange == null) return line;

    Diff.Change change = myChange;

    int newLine = line;

    while (change != null) {
      if (line < change.line0) break;
      if (line >= change.line0 + change.deleted) {
        newLine += change.inserted - change.deleted;
      } else {
        int delta = Math.min(change.inserted, line - change.line0);
        newLine = change.line1 + delta;
        break;
      }

      change = change.link;
    }

    return newLine;
  }

  public int translateLineViaDiffStrict(int line) {
    if (myChange == null) buildDiff();
    if (myChange == null) return line;

    Diff.Change change = myChange;

    int newLine = line;

    while (change != null) {
      if (line < change.line0) break;
      if (line >= change.line0 + change.deleted) {
        newLine += change.inserted - change.deleted;
      } else {
        return -1;
      }

      change = change.link;
    }

    return newLine;
  }

  public void buildDiff() {
    final String[] strings1 = LineTokenizer.tokenize(myOldString, false);
    final String[] strings2 = LineTokenizer.tokenize(myNewString, false);

    //Diff diff = new Diff(strings1, strings2);
    //myChange = diff.diff_2(false);
    myChange = Diff.buildChanges(strings1, strings2);
  }
}
