/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.util.ArrayUtil;


public abstract class ActionPlaces {
  public static final String UNKNOWN = "unknown";

  public static final String MAIN_MENU = "MainMenu";
  public static final String MAIN_TOOLBAR = "MainToolbar";
  public static final String EDITOR_POPUP = "EditorPopup";
  public static final String EDITOR_TAB_POPUP = "EditorTabPopup";
  public static final String COMMANDER_POPUP = "CommanderPopup";

  public static final String PROJECT_VIEW_POPUP = "ProjectViewPopup";
  public static final String PROJECT_VIEW_TOOLBAR = "ProjectViewToolbar";

  public static final String TESTTREE_VIEW_POPUP = "TestTreeViewPopup";
  public static final String TESTTREE_VIEW_TOOLBAR = "TestTreeViewToolbar";
  public static final String TESTSTATISTICS_VIEW_POPUP = "TestStatisticsViewPopup";

  public static final String TYPE_HIERARCHY_VIEW_POPUP = "TypeHierarchyViewPopup";
  public static final String TYPE_HIERARCHY_VIEW_TOOLBAR = "TypeHierarchyViewToolbar";
  public static final String METHOD_HIERARCHY_VIEW_POPUP = "MethodHierarchyViewPopup";
  public static final String METHOD_HIERARCHY_VIEW_TOOLBAR = "MethodHierarchyViewToolbar";
  public static final String CALL_HIERARCHY_VIEW_POPUP = "CallHierarchyViewPopup";
  public static final String CALL_HIERARCHY_VIEW_TOOLBAR = "CallHierarchyViewToolbar";
  public static final String J2EE_ATTRIBUTES_VIEW_POPUP = "J2EEAttributesViewPopup";
  public static final String J2EE_VIEW_POPUP = "J2EEViewPopup";
  public static final String DEBUGGER_TOOLBAR = "DebuggerToolbar";
  public static final String USAGE_VIEW_POPUP = "UsageViewPopup";
  public static final String USAGE_VIEW_TOOLBAR = "UsageViewToolbar";
  public static final String STRUCTURE_VIEW_POPUP = "StructureViewPopup";
  public static final String STRUCTURE_VIEW_TOOLBAR = "StructureViewToolbar";

  public static final String TODO_VIEW_POPUP = "TodoViewPopup";
  public static final String TODO_VIEW_TOOLBAR = "TodoViewToolbar";

  public static final String COMPILER_MESSAGES_POPUP = "CompilerMessagesPopup";
  public static final String COMPILER_MESSAGES_TOOLBAR = "CompilerMessagesToolbar";
  public static final String ANT_MESSAGES_POPUP = "AntMessagesPopup";
  public static final String ANT_MESSAGES_TOOLBAR = "AntMessagesToolbar";
  public static final String ANT_EXPLORER_POPUP = "AntExplorerPopup";
  public static final String ANT_EXPLORER_TOOLBAR = "AntExplorerToolbar";

  //todo: probably these context should be splitted into several contexts
  public static final String CODE_INSPECTION = "CodeInspection";
  public static final String JAVADOC_TOOLBAR = "JavadocToolbar";
  public static final String FILEHISTORY_VIEW_TOOLBAR = "FileHistoryViewToolbar";
  public static final String UPDATE_POPUP = "UpdatePopup";
  public static final String COMBO_PAGER = "ComboBoxPager";
  public static final String FILEVIEW_POPUP = "FileViewPopup";
  public static final String FILE_VIEW = "FileViewActionToolbal";
  public static final String CHECKOUT_POPUP = "CheckoutPopup";
  public static final String FILE_HISTORY_TOOLBAR = "FileHistoryToolbar";
  public static final String LVCS_DIRECTORY_HISTORY_POPUP = "LvcsHistoryPopup";
  public static final String LVCS_DIRECTORY_HISTORY_TOOLBAR = "LvcsDirectoryHistoryToolbar";
  public static final String GUI_DESIGNER_EDITOR_POPUP = "GuiDesigner.EditorPopup";
  public static final String GUI_DESIGNER_COMPONENT_TREE_POPUP = "GuiDesigner.ComponentTreePopup";
  public static final String GUI_DESIGNER_PROPERTY_INSPECTOR_POPUP = "GuiDesigner.PropertyInspectorPopup";

  public static final String RUN_CONFIGURATIONS_COMBOBOX = "RunConfigurationsCombobox";
  public static final String CREATE_EJB_POPUP = "CreateEjbPopup";

  private static final String[] ourToolbarPlaces = new String[]{
     PROJECT_VIEW_TOOLBAR, TESTTREE_VIEW_TOOLBAR, MAIN_TOOLBAR, ANT_EXPLORER_TOOLBAR, ANT_MESSAGES_TOOLBAR, COMPILER_MESSAGES_TOOLBAR, TODO_VIEW_TOOLBAR,
     STRUCTURE_VIEW_TOOLBAR, USAGE_VIEW_TOOLBAR, DEBUGGER_TOOLBAR, CALL_HIERARCHY_VIEW_TOOLBAR, METHOD_HIERARCHY_VIEW_TOOLBAR,
     TYPE_HIERARCHY_VIEW_TOOLBAR, JAVADOC_TOOLBAR, FILE_HISTORY_TOOLBAR, FILEHISTORY_VIEW_TOOLBAR, LVCS_DIRECTORY_HISTORY_TOOLBAR, };

  public static boolean isToolbarPlace(String place) {
    return ArrayUtil.find(ourToolbarPlaces, place) != -1;
  }
}
