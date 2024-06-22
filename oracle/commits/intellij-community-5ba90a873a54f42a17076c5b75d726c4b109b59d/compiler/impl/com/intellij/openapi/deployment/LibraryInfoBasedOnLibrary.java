/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.deployment;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.util.InvalidDataException;
import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

import java.util.List;
import java.util.Arrays;

/**
 * @author nik
*/
class LibraryInfoBasedOnLibrary implements LibraryInfo {
  private final Library myLibrary;
  private LibraryInfoImpl myInfoToRestore;

  public LibraryInfoBasedOnLibrary(@NotNull Library library) {
    assert !(library instanceof LibraryEx) || !((LibraryEx)library).isDisposed();
    myLibrary = library;
    myInfoToRestore = new LibraryInfoImpl(library);
  }

  public String getName() {
    return myLibrary.getName();
  }

  @NotNull
  public List<String> getUrls() {
    return Arrays.asList(myLibrary.getUrls(OrderRootType.CLASSES));
  }

  public String getLevel() {
    final LibraryTable table = myLibrary.getTable();
    return table == null ? LibraryLink.MODULE_LEVEL : table.getTableLevel();
  }

  @NotNull
  public Library getLibrary() {
    return myLibrary;
  }

  public void readExternal(Element element) throws InvalidDataException {
  }

  public LibraryInfoImpl getInfoToRestore() {
    return myInfoToRestore;
  }
}
