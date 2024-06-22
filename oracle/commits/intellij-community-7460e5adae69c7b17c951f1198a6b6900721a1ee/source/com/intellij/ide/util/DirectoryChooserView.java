package com.intellij.ide.util;

import javax.swing.*;

/**
 * @author dsl
 */
public interface DirectoryChooserView {
  JComponent getComponent();

  void onSelectionChange(Runnable runnable);

  DirectoryChooser.ItemWrapper getItemByIndex(int i);

  void clearSelection();

  void selectItemByIndex(int selectionIndex);

  void addItem(DirectoryChooser.ItemWrapper itemWrapper);

  void listFilled();

  void clearItems();

  int getItemsSize();

  DirectoryChooser.ItemWrapper getSelectedItem();
}
