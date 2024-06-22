/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.Collection;
import java.util.Collections;

import org.jetbrains.annotations.NonNls;

/**
 * Several {@link DiffContent}s to compare
 */
public abstract class DiffRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.DiffData");
  private String myGroupKey = COMMON_DIFF_GROUP_KEY;
  private final Project myProject;
  private ToolbarAddons myToolbarAddons = ToolbarAddons.NOTHING;
  @NonNls private static final String COMMON_DIFF_GROUP_KEY = "DiffWindow";

  protected DiffRequest(Project project) {
    myProject = project;
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   */
  public void setToolbarAddons(ToolbarAddons toolbarAddons) {
    LOG.assertTrue(toolbarAddons != null);
    myToolbarAddons = toolbarAddons;
  }

  public String getGroupKey() { return myGroupKey; }
  public void setGroupKey(@NonNls String groupKey) { myGroupKey = groupKey; }
  public Project getProject() { return myProject; }

  /**
   * @return contents to compare
   */
  public abstract DiffContent[] getContents();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   */
  public abstract String[] getContentTitles();

  /**
   * Used as window title
   */
  public abstract String getWindowTitle();

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   */
  public void customizeToolbar(DiffToolbar toolbar) {
    myToolbarAddons.customize(toolbar);
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   * @return not null (possibly empty) collection of hints for diff tool.
   */
  public Collection getHints() {
    return Collections.emptySet();
  }

  /**
   * <B>Work in progess. Don't rely on this functionality</B><br>
   */
  public static interface ToolbarAddons {
    /**
     * Does nothing
     */
    ToolbarAddons NOTHING = new ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
      }
    };

    /**
     * Removes some of default action to use {@link DiffToolbar} as child of main IDEA frame.
     * Removes actions:<p/>
     * {@link IdeActions#ACTION_COPY}<p/>
     * {@link IdeActions#ACTION_FIND}
     */
    ToolbarAddons IDE_FRAME = new ToolbarAddons() {
      public void customize(DiffToolbar toolbar) {
        toolbar.removeActionById(IdeActions.ACTION_COPY);
        toolbar.removeActionById(IdeActions.ACTION_FIND);
      }
    };

    void customize(DiffToolbar toolbar);
  }
}
