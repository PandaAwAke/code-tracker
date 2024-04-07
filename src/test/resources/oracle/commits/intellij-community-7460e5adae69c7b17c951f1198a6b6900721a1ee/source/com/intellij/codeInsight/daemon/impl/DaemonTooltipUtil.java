package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.openapi.editor.Editor;

import javax.swing.*;
import java.awt.*;

/**
 * @author max
 */
public class DaemonTooltipUtil {
  private static final TooltipGroup DAEMON_INFO_GROUP = new TooltipGroup("DAEMON_INFO_GROUP", 0);

  public static void showInfoTooltip(HighlightInfo info, Editor editor, int defaultOffset) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int endOffset = info.highlighter.getEndOffset();
    int startOffset = info.highlighter.getStartOffset();

    Point top = editor.logicalPositionToXY(editor.offsetToLogicalPosition(startOffset));
    Point bottom = editor.logicalPositionToXY(editor.offsetToLogicalPosition(endOffset));

    Point bestPoint = new Point(top.x, bottom.y + editor.getLineHeight());

    if (!visibleArea.contains(bestPoint)) {
      bestPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(defaultOffset));
    }

    Point p = SwingUtilities.convertPoint(
      editor.getContentComponent(),
      bestPoint,
      editor.getComponent().getRootPane().getLayeredPane()
    );
    HintManager.getInstance().getTooltipController().showTooltip(editor, p, info, false, DAEMON_INFO_GROUP);
  }

  public static void cancelTooltips() {
    HintManager.getInstance().getTooltipController().cancelTooltip(DAEMON_INFO_GROUP);
  }
}
