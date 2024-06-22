package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.DocumentUtil;
import com.intellij.openapi.diff.impl.util.GutterActionRenderer;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

public abstract class Change {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.incrementalMerge.Change");

  private void apply(FragmentSide original) {
    FragmentSide targetSide = original.otherSide();
    RangeMarker originalRangeMarker = getRangeMarker(original);
    RangeMarker rangeMarker = getRangeMarker(targetSide);

    if (originalRangeMarker != null && rangeMarker != null) {
      getType().apply(originalRangeMarker, rangeMarker);
      if (isValid()) {
        removeFromList();
      }
    }
  }

  protected abstract void removeFromList();

  public void addMarkup(Editor[] editors) {
    LOG.assertTrue(editors.length == 2);
    highligth(editors, FragmentSide.SIDE1);
    highligth(editors, FragmentSide.SIDE2);
  }

  private void highligth(Editor[] editors, FragmentSide side) {
    getHighlighterHolder(side).highlight(getChangeSide(side), editors[side.getIndex()], this.getType());
  }

  private void updateHighlighter(FragmentSide side) {
    getHighlighterHolder(side).updateHighlighter(getChangeSide(side), this.getType());
  }

  private Project getProject() { return getChangeList().getProject(); }

  public abstract ChangeType.ChangeSide getChangeSide(FragmentSide side);

  public abstract ChangeType getType();

  public abstract ChangeList getChangeList();

  private HightlighterHolder getHighlighterHolder(FragmentSide side) {
    return getChangeSide(side).getHighlighterHolder();
  }

  private RangeMarker getRangeMarker(FragmentSide side) {
    ChangeType.ChangeSide changeSide = getChangeSide(side);
    LOG.assertTrue(changeSide != null);
    return changeSide.getRange();
  }

  public abstract void onRemovedFromList();

  public abstract boolean isValid();

  public static void apply(final Change change, final FragmentSide fromSide) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(change.getProject(), new Runnable() {
          public void run() {
            change.apply(fromSide);
          }
        }, null, "MergeApply");
      }
    });
  }

  public void updateMarkup() {
    updateHighlighter(FragmentSide.SIDE1);
    updateHighlighter(FragmentSide.SIDE2);
  }

  public boolean canHasActions(FragmentSide fromSide) {
    FragmentSide targetSide = fromSide.otherSide();
    Document targetDocument = getChangeList().getDocument(targetSide);
    if (!targetDocument.isWritable()) return false;
    Editor targetEditor = getHighlighterHolder(targetSide).getEditor();
    if (targetEditor.isViewer()) return false;
    return true;
  }

  public static class ChangeOrder implements Comparator<Change> {
    private final FragmentSide myMainSide;

    public ChangeOrder(FragmentSide mainSide) {
      myMainSide = mainSide;
    }

    public int compare(Change change, Change change1) {
      int result1 = compareSide(change, change1, myMainSide);
      if (result1 != 0) return result1;
      return compareSide(change, change1, myMainSide.otherSide());
    }

    private int compareSide(Change change, Change change1, FragmentSide side) {
      return DocumentUtil.RANGE_ORDER.compare(change.getRangeMarker(side), change1.getRangeMarker(side));
    }
  }

  protected static class HightlighterHolder implements ChangeType.MarkupHolder {
    private static final RangeHighlighter[] NO_HIGHLIGHTERS = new RangeHighlighter[0];
    private Editor myEditor;
    private final ArrayList<RangeHighlighter> myHighlighters = new ArrayList<RangeHighlighter>(3);
    private RangeHighlighter myMainHighlighter = null;
    private AnAction[] myActions;
    private RangeHighlighter[] myActionHighlighters = NO_HIGHLIGHTERS;

    public void highlight(ChangeType.ChangeSide changeSide, Editor editor, ChangeType type) {
      LOG.assertTrue(myEditor == null || editor == myEditor);
      removeHighlighters();
      myEditor = editor;
      setHighlighter(changeSide, type);
    }

    private MarkupModel getMarkupModel() {
      return myEditor.getMarkupModel();
    }

    private void highlighterCreated(RangeHighlighter highlighter, TextAttributes attrs) {
      if (attrs != null) {
        highlighter.setErrorStripeMarkColor(attrs.getErrorStripeColor());
      }
      myHighlighters.add(highlighter);
    }

    public RangeHighlighter addLineHighlighter(int line, int layer, TextDiffType diffType) {
      RangeHighlighter highlighter = getMarkupModel().addLineHighlighter(line, layer, null);
      if (highlighter == null) return null;
      highlighter.setLineSeparatorColor(diffType.getTextBackground(myEditor));
      highlighterCreated(highlighter, diffType.getTextAttributes(myEditor));
      return highlighter;
    }

    public RangeHighlighter addRangeHighlighter(int start, int end, int layer, TextDiffType type, HighlighterTargetArea targetArea) {
      if (getMarkupModel().getDocument().getTextLength() == 0) return null;
      TextAttributes attributes = type.getTextAttributes(myEditor);
      RangeHighlighter highlighter = getMarkupModel().addRangeHighlighter(start, end, layer, attributes, targetArea);
      highlighterCreated(highlighter, attributes);
      return highlighter;
    }

    private void setHighlighter(ChangeType.ChangeSide changeSide, ChangeType type) {
      myMainHighlighter = type.addMarker(changeSide, this);
      updateAction();
    }

    public Editor getEditor() {
      return myEditor;
    }

    public void removeHighlighters() {
      if (myEditor == null) {
        LOG.assertTrue(myHighlighters.size() == 0);
        LOG.assertTrue(myMainHighlighter == null);
        return;
      }
      MarkupModel markupModel = myEditor.getMarkupModel();
      for (Iterator<RangeHighlighter> iterator = myHighlighters.iterator(); iterator.hasNext();) {
        RangeHighlighter highlighter = iterator.next();
        markupModel.removeHighlighter(highlighter);
        iterator.remove();
      }
      removeActionHighlighters();
      myMainHighlighter = null;
    }

    private void removeActionHighlighters() {
      MarkupModel markupModel = myEditor.getMarkupModel();
      for (int i = 0; i < myActionHighlighters.length; i++) {
        markupModel.removeHighlighter(myActionHighlighters[i]);
      }
      myActionHighlighters = NO_HIGHLIGHTERS;
    }

    public void setActions(AnAction[] action) {
      myActions = action;
      updateAction();
    }

    private void updateAction() {
      removeActionHighlighters();
      if (myMainHighlighter != null && myActions != null && myActions.length > 0) {
        myActionHighlighters = new RangeHighlighter[myActions.length];
        for (int i = 0; i < myActionHighlighters.length; i++) {
          RangeHighlighter highlighter = cloneMainHighlighter(myMainHighlighter);
          highlighter.setGutterIconRenderer(new GutterActionRenderer(myActions[i]));
          myActionHighlighters[i] = highlighter;
        }
      }
    }

    private RangeHighlighter cloneMainHighlighter(RangeHighlighter mainHighlighter) {
      LOG.assertTrue(mainHighlighter != null);
      RangeHighlighter highlighter = myEditor.getMarkupModel().addRangeHighlighter(mainHighlighter.getStartOffset(), mainHighlighter.getEndOffset(), mainHighlighter.getLayer(),
                                                                                   null, mainHighlighter.getTargetArea());
      // TODO[dyoma] copy greedyToLeft and greedyToRight
      return highlighter;
    }

    public void updateHighlighter(ChangeType.ChangeSide changeSide, ChangeType type) {
      LOG.assertTrue(myEditor != null);
      removeHighlighters();
      setHighlighter(changeSide, type);
    }
  }

  protected static class Side extends ChangeType.ChangeSide {
    private final FragmentSide mySide;
    private final DiffRangeMarker myRange;
    private final HightlighterHolder myHighlighterHolder = new HightlighterHolder();

    public Side(FragmentSide side, DiffRangeMarker rangeMarker) {
      mySide = side;
      myRange = rangeMarker;
    }

    public FragmentSide getFragmentSide() {
      return mySide;
    }

    public DiffRangeMarker getRange() {
      return myRange;
    }

    public HightlighterHolder getHighlighterHolder() {
      return myHighlighterHolder;
    }
  }
}
