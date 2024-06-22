/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.util.containers.HashMap;

import java.util.Map;

public class LibraryTablesRegistrarImpl extends LibraryTablesRegistrar implements ApplicationComponent {
  static final Map<String, LibraryTable> myLibraryTables = new HashMap<String, LibraryTable>();

  public LibraryTable getLibraryTable() {
    return ApplicationManager.getApplication().getComponent(LibraryTable.class);
  }

  public LibraryTable getLibraryTable(Project project) {
    return project.getComponent(LibraryTable.class);
  }

  public LibraryTable getLibraryTableByLevel(String level, Project project) {
    if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(level)) return getLibraryTable(project);
    if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(level)) return getLibraryTable();
    return myLibraryTables.get(level);
  }

  public void registerLibraryTable(LibraryTable libraryTable) {
    String tableLevel = libraryTable.getTableLevel();
    final LibraryTable oldTable = myLibraryTables.put(tableLevel, libraryTable);
    if (oldTable != null) {
      throw new IllegalArgumentException("Library table '" + tableLevel + "' already registered.");
    }
  }

  public LibraryTable registerLibraryTable(final String customLevel) {
    LibraryTable table = new LibraryTableBase() {
      public String getTableLevel() {
        return customLevel;
      }
    };

    registerLibraryTable(table);
    return table;
  }

  public String getComponentName() {
    return "LibraryTablesRegistrar";
  }

  public void initComponent() { }

  public void disposeComponent() {
    myLibraryTables.clear();
  }
}