package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffToolbar;
import com.intellij.openapi.diff.ex.DiffStatusBar;
import com.intellij.openapi.diff.impl.DiffToolbarComponent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class DiffPanelOutterComponent extends JPanel implements DataProvider {
  private final DiffStatusBar myStatusBar;
  private final DiffToolbarComponent myToolbar;
  private final DiffRequest.ToolbarAddons myDefaultActions;
  private DataProvider myDataProvider = null;
  private DeferScrollToFirstDiff myScrollState = NO_SCROLL_NEEDED;
  private ScrollingPanel myScrollingPanel = null;

  public DiffPanelOutterComponent(List<TextDiffType> diffTypes, DiffRequest.ToolbarAddons defaultActions) {
    super(new BorderLayout());
    myStatusBar = new DiffStatusBar(diffTypes);
    add(myStatusBar, BorderLayout.SOUTH);
    myDefaultActions = defaultActions;
    myToolbar = new DiffToolbarComponent(this);
    disableToolbar(false);
  }

  public DiffToolbar resetToolbar() {
    myToolbar.resetToolbar(myDefaultActions);
    return myToolbar.getToolbar();
  }

  public void insertDiffComponent(JComponent component, ScrollingPanel scrollingPanel) {
    add(component, BorderLayout.CENTER);
    setScrollingPanel(scrollingPanel);
  }

  public void setDataProvider(DataProvider dataProvider) {
    myDataProvider = dataProvider;
  }

  public void setStatusBarText(String text) {
    myStatusBar.setText(text);
  }

  public Object getData(String dataId) {
    if (DataConstantsEx.SOURCE_NAVIGATION_LOCKED.equals(dataId)) return Boolean.TRUE;
    if (myDataProvider == null) return null;
    if (dataId == DataConstants.EDITOR) {
      FocusDiffSide side = (FocusDiffSide)myDataProvider.getData(FocusDiffSide.FOCUSED_DIFF_SIDE);
      if (side != null) return side.getEditor();
    }
    return myDataProvider.getData(dataId);
  }

  public void setScrollingPanel(ScrollingPanel scrollingPanel) {
    myScrollingPanel = scrollingPanel;
  }

  public void requestScrollEditors() {
    myScrollState = SCROLL_WHEN_POSSIBLE;
    tryScrollNow();
  }

  private void tryScrollNow() {
    if (myScrollingPanel == null) return;
    myScrollState.deferScroll(this);
  }

  private void performScroll() {
    DeferScrollToFirstDiff newState = myScrollState.scrollNow(myScrollingPanel, this);
    if (newState != null) myScrollState = newState;
  }

  public void addNotify() {
    super.addNotify();
    tryScrollNow();
  }

  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    tryScrollNow();
  }

  protected void validateTree() {
    super.validateTree();
    tryScrollNow();
  }

  public void cancelScrollEditors() {
    myScrollState = NO_SCROLL_NEEDED;
  }

  private interface DeferScrollToFirstDiff {
    DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component);

    void deferScroll(DiffPanelOutterComponent outter);
  }

  public interface ScrollingPanel {
    void scrollEditors();
  }

  private static final DeferScrollToFirstDiff NO_SCROLL_NEEDED = new DeferScrollToFirstDiff() {
    public DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component) {
      return NO_SCROLL_NEEDED;
    }

    public void deferScroll(DiffPanelOutterComponent outter) { }
  };

  private static final DeferScrollToFirstDiff SCROLL_WHEN_POSSIBLE = new DeferScrollToFirstDiff() {
    public DeferScrollToFirstDiff scrollNow(ScrollingPanel panel, JComponent component) {
      if (!component.isDisplayable()) return null;
      panel.scrollEditors();
      return NO_SCROLL_NEEDED;
    }

    public void deferScroll(final DiffPanelOutterComponent outter) {
      if (!outter.isDisplayable()) return;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          outter.performScroll();
        }
      });
    }
  };

  public void disableToolbar(boolean disable) {
    if (disable && isToolbarEnabled()) remove(myToolbar);
    else if (myToolbar.getParent() == null) add(myToolbar, BorderLayout.NORTH);
  }

  public boolean isToolbarEnabled() {
    return myToolbar.getParent() != null;
  }

  public void registerToolbarActions() {
    myToolbar.getToolbar().registerKeyboardActions(this);
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    myStatusBar.setColorScheme(scheme);
  }
}
