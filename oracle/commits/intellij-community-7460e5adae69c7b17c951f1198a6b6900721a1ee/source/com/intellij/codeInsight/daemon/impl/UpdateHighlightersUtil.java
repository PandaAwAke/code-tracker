package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UpdateHighlightersUtil {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil");

  public static final int NORMAL_HIGHLIGHTERS_GROUP = 1;
  public static final int POST_HIGHLIGHTERS_GROUP = 2;
  public static final int INSPECTION_HIGHLIGHTERS_GROUP = 3;
  public static final int[] POST_HIGHLIGHT_GROUPS = new int[]{POST_HIGHLIGHTERS_GROUP,INSPECTION_HIGHLIGHTERS_GROUP,};
  public static final int[] NORMAL_HIGHLIGHT_GROUPS = new int[]{NORMAL_HIGHLIGHTERS_GROUP};

  private UpdateHighlightersUtil() {}

  public static void setHighlightersToEditor(Project project,
                                             final Document document,
                                             int startOffset,
                                             int endOffset,
                                             final HighlightInfo[] highlights,
                                             int group) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    List<HighlightInfo> array = new ArrayList<HighlightInfo>();

    HighlightInfo[] oldHighlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);

    if (oldHighlights != null) {
      for (int i = 0; i < oldHighlights.length; i++) {
        HighlightInfo info = oldHighlights[i];
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove;
        if (!highlighter.isValid()) {
          toRemove = true;
        }
        else {
          toRemove = info.group == group
                     && startOffset <= highlighter.getStartOffset()
                     && highlighter.getEndOffset() <= endOffset;
        }

        if (toRemove) {
          document.getMarkupModel(project).removeHighlighter(highlighter);
        }
        else {
          array.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed segment highlighters:" + (oldHighlights.length - array.size()));
      }
    }

    for (int i = 0; i < highlights.length; i++) {
      HighlightInfo info = highlights[i];
      int layer;
      if (info.startOffset < startOffset || info.endOffset > endOffset) continue;
      HighlightInfo.Severity severity = info.getSeverity();
      if (severity == HighlightInfo.INFORMATION) {
        layer = HighlighterLayer.ADDITIONAL_SYNTAX;
      }
      else if (severity == HighlightInfo.WARNING) {
        layer = HighlighterLayer.WARNING;
      }
      else {
        layer = HighlighterLayer.ERROR;
      }

      int infoEndOffset = info.endOffset;
      if (infoEndOffset > document.getTextLength()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Invalid HighlightInfo created: (" + info.startOffset + ":" + infoEndOffset + ")" + info.description);
        }

        infoEndOffset = document.getTextLength();
      }

      RangeHighlighterEx highlighter = (RangeHighlighterEx)document.getMarkupModel(project).addRangeHighlighter(
        info.startOffset,
        infoEndOffset,
        layer,
        info.getTextAttributes(),
        HighlighterTargetArea.EXACT_RANGE);
      //highlighter.setUserObject(info); // debug purposes only!
      info.highlighter = highlighter;
      highlighter.setAfterEndOfLine(info.isAfterEndOfLine);
      info.text = document.getCharsSequence().subSequence(info.startOffset, info.endOffset).toString();
      info.group = group;

      highlighter.setErrorStripeMarkColor(info.getErrorStripeMarkColor());
      highlighter.setErrorStripeTooltip(info);

      HashMap<TextRange, RangeMarker> ranges2markers = new HashMap<TextRange, RangeMarker>();
      ranges2markers.put(new TextRange(info.startOffset, info.endOffset), info.highlighter);
      if (info.quickFixActionRanges != null) {
        info.quickFixActionMarkers = new ArrayList<Pair<IntentionAction, RangeMarker>>();
        for (Iterator<Pair<IntentionAction, TextRange>> iterator = info.quickFixActionRanges.iterator();
             iterator.hasNext();) {
          Pair<IntentionAction, TextRange> pair = iterator.next();
          TextRange range = pair.second;
          RangeMarker marker= ranges2markers.get(range);
          if (marker == null) {
            marker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset());
            ranges2markers.put(range, marker);
          }
          info.quickFixActionMarkers.add(new Pair<IntentionAction, RangeMarker>(pair.first, marker));
        }
      }
      info.fixMarker = ranges2markers.get(new TextRange(info.fixStartOffset, info.fixEndOffset));
      if (info.fixMarker == null) {
        info.fixMarker = document.createRangeMarker(info.fixStartOffset, info.fixEndOffset);
      }

      array.add(info);
    }

    HighlightInfo[] newHighlights = array.toArray(new HighlightInfo[array.size()]);
    DaemonCodeAnalyzerImpl.setHighlights(document, newHighlights, project);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Added segment highlighters:" + highlights.length);
    }
  }

  public static final int NORMAL_MARKERS_GROUP = 1;
  public static final int OVERRIDEN_MARKERS_GROUP = 2;

  public static void setLineMarkersToEditor(Project project,
                                            final Document document,
                                            int startOffset,
                                            int endOffset,
                                            final LineMarkerInfo[] markers,
                                            int group) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    ArrayList<LineMarkerInfo> array = new ArrayList<LineMarkerInfo>();

    LineMarkerInfo[] oldMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project);
    if (oldMarkers != null) {
      for (int i = 0; i < oldMarkers.length; i++) {
        LineMarkerInfo info = oldMarkers[i];
        RangeHighlighter highlighter = info.highlighter;
        boolean toRemove;
        if (!highlighter.isValid()) {
          toRemove = true;
        }
        else {
          toRemove = isLineMarkerInGroup(info.type, group)
                     && startOffset <= highlighter.getStartOffset() && highlighter.getStartOffset() <= endOffset;
        }

        if (toRemove) {
          document.getMarkupModel(project).removeHighlighter(highlighter);
        }
        else {
          array.add(info);
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Removed line markers:" + (oldMarkers.length - array.size()));
      }
    }

    for (int i = 0; i < markers.length; i++) {
      LineMarkerInfo info = markers[i];
      RangeHighlighter marker = document.getMarkupModel(project).addRangeHighlighter(info.startOffset,
                                                                                     info.startOffset,
                                                                                     HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                                     info.attributes,
                                                                                     HighlighterTargetArea.LINES_IN_RANGE);
      marker.setGutterIconRenderer(info.createGutterRenderer());
      marker.setLineSeparatorColor(info.separatorColor);
      marker.setLineSeparatorPlacement(info.separatorPlacement);
      info.highlighter = marker;
      array.add(info);
    }

    LineMarkerInfo[] newMarkers = array.toArray(new LineMarkerInfo[array.size()]);
    DaemonCodeAnalyzerImpl.setLineMarkers(document, newMarkers, project);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Added line markers:" + markers.length);
    }
  }

  private static boolean isLineMarkerInGroup(int type, int group) {
    switch (type) {
      case LineMarkerInfo.OVERRIDEN_METHOD:
      case LineMarkerInfo.SUBCLASSED_CLASS:
      case LineMarkerInfo.BOUND_CLASS_OR_FIELD:
        return group == OVERRIDEN_MARKERS_GROUP;

      case LineMarkerInfo.OVERRIDING_METHOD:
        /*
        return true; // in both groups

        */
      case LineMarkerInfo.METHOD_SEPARATOR:
        return group == NORMAL_MARKERS_GROUP;

      default:
        LOG.assertTrue(false);
        return false;
    }
  }

  public static void updateHighlightersByTyping(Project project, DocumentEvent e) {
    Document document = e.getDocument();

    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(document, project);
    if (highlights != null) {
      int offset = e.getOffset();
      Editor[] editors = EditorFactory.getInstance().getEditors(document, project);
      if (editors.length > 0) {
        Editor editor = editors[0]; // use any editor - just to fetch Highlighter
        HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(Math.max(0, offset - 1));
        if (iterator.atEnd()) return;
        int start = iterator.getStart();
        while (iterator.getEnd() < e.getOffset() + e.getNewLength()) {
          iterator.advance();
          if (iterator.atEnd()) return;
        }
        int end = iterator.getEnd();

        ArrayList<HighlightInfo> array = new ArrayList<HighlightInfo>();
        boolean changes = false;
        for (int j = 0; j < highlights.length; j++) {
          HighlightInfo info = highlights[j];
          RangeHighlighter highlighter = info.highlighter;
          boolean toRemove = false;

          if (info.needUpdateOnTyping()) {
            int highlighterStart = highlighter.getStartOffset();
            int highlighterEnd = highlighter.getEndOffset();
            if (info.isAfterEndOfLine) {
              if (highlighterStart < document.getTextLength()) {
                highlighterStart += 1;
              }
              if (highlighterEnd < document.getTextLength()) {
                highlighterEnd += 1;
              }
            }

            if (!highlighter.isValid()) {
              toRemove = true;
            }
            else if (start < highlighterEnd && highlighterStart < end) {
              LOG.assertTrue(0 <= highlighterStart);
              LOG.assertTrue(highlighterStart < document.getTextLength());
              HighlighterIterator iterator1 = ((EditorEx)editor).getHighlighter().createIterator(highlighterStart);
              int start1 = iterator1.getStart();
              while (iterator1.getEnd() < highlighterEnd) {
                iterator1.advance();
              }
              int end1 = iterator1.getEnd();
              CharSequence chars = document.getCharsSequence();
              String token = chars.subSequence(start1, end1).toString();
              if (start1 != highlighterStart || end1 != highlighterEnd || !token.equals(info.text)) {
                toRemove = true;
              }
            }
          }

          if (toRemove) {
            document.getMarkupModel(project).removeHighlighter(highlighter);
            changes = true;
          }
          else {
            array.add(info);
          }
        }

        if (changes) {
          HighlightInfo[] newHighlights = array.toArray(new HighlightInfo[array.size()]);
          DaemonCodeAnalyzerImpl.setHighlights(document, newHighlights, project);
        }
      }
    }
  }

  /*
  // temp!!
  public static void checkConsistency(DaemonCodeAnalyzerImpl codeAnalyzer, Document document){
    LOG.assertTrue(ApplicationImpl.isDispatchThreadStatic());
    HighlightInfo[] highlights = codeAnalyzer.getHighlights(document);
    if (highlights == null){
      highlights = new HighlightInfo[0];
    }
    ArrayList infos = new ArrayList();
    for(int i = 0; i < highlights.length; i++){
      infos.add(highlights[i]);
    }

    RangeHighlighter[] highlighters = document.getMarkupModel().getAllHighlighters for(int i = 0; i < highlighters.length; i++){
      RangeHighlighter highlighter = highlighters[i];
      Object userObject = ((RangeHighlighterEx)highlighter).getUserObject();
      if (userObject instanceof HighlightInfo){
        LOG.assertTrue(infos.contains(userObject));
        infos.remove(userObject);
      }
    }
    LOG.assertTrue(infos.isEmpty());
  }
  */
}