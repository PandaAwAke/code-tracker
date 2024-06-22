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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Two contents for general diff
 */
public class SimpleDiffRequest extends DiffRequest {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.SimpleDiffRequest");
  private final DiffContent[] myContents = new DiffContent[2];
  private final String[] myContentTitles = new String[2];
  private String myWindowTitle;
  private final HashSet myHints = new HashSet();

  public SimpleDiffRequest(Project project, String windowtitle) {
    super(project);
    myWindowTitle = windowtitle;
  }

  public DiffContent[] getContents() { return myContents; }
  public String[] getContentTitles() { return myContentTitles; }
  public String getWindowTitle() { return myWindowTitle; }

  public Collection getHints() {
    return Collections.unmodifiableCollection(myHints);
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void addHint(Object hint) {
    myHints.add(hint);
  }

  /**
   * @param hint
   * @see DiffRequest#getHints()
   */
  public void removeHint(Object hint) {
    myHints.remove(hint);
  }

  public void setContents(DiffContent content1, DiffContent content2) {
    myContents[0] = content1;
    myContents[1] = content2;
  }

  public void setContentTitles(String title1, String title2) {
    myContentTitles[0] = title1;
    myContentTitles[1] = title2;
  }


  public void setWindowTitle(String windowTitle) {
    myWindowTitle = windowTitle;
  }

  public static SimpleDiffRequest compareFiles(VirtualFile file1, VirtualFile file2, Project project, String title) {
    FileDiffRequest result = new FileDiffRequest(project, title);
    result.myVirtualFiles[0] = file1;
    result.myVirtualFiles[1] = file2;
    result.myContentTitles[0] = DiffContentUtil.getTitle(file1);
    result.myContentTitles[1] = DiffContentUtil.getTitle(file2);
    return result;
  }

  public static SimpleDiffRequest comapreFiles(VirtualFile file1, VirtualFile file2, Project project) {
    return compareFiles(file1, file2, project, file1.getPresentableUrl() + " vs " + file2.getPresentableUrl());
  }

  private static class FileDiffRequest extends SimpleDiffRequest {
    private final String[] myContentTitles = new String[2];
    private final VirtualFile[] myVirtualFiles = new VirtualFile[2];

    public FileDiffRequest(Project project, String title) {
      super(project, title);
    }

    public DiffContent[] getContents() {
      return new DiffContent[]{DocumentContent.fromFile(getProject(), myVirtualFiles[0]),
                               DocumentContent.fromFile(getProject(), myVirtualFiles[1])};
    }

    public String[] getContentTitles() {
      return myContentTitles;
    }
  }
}
