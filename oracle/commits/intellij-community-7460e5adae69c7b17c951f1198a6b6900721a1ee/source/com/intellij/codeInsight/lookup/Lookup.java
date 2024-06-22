package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.UserDataHolder;

import java.awt.Rectangle;

public interface Lookup extends UserDataHolder{
  char NORMAL_SELECT_CHAR = '\n';
  char REPLACE_SELECT_CHAR = '\t';

  LookupItem getCurrentItem();
  void setCurrentItem(LookupItem item);

  void addLookupListener(LookupListener listener);
  void removeLookupListener(LookupListener listener);

  /**
   * @return bounds in layered pane coordinate system
   */
  Rectangle getBounds();

  /**
   * @return bounds of the current item in the layered pane coordinate system.
   */
  Rectangle getCurrentItemBounds();
  boolean isPositionedAboveCaret();

  boolean fillInCommonPrefix(boolean toCompleteUniqueName);
}