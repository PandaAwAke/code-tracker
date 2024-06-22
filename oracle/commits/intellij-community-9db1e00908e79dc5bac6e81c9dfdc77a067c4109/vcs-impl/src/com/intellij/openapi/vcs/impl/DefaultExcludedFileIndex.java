package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;

/**
 * @author yole
 */
public class DefaultExcludedFileIndex extends ExcludedFileIndex {
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public DefaultExcludedFileIndex(final Project project) {
    super(project);
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  public boolean isInContent(final VirtualFile file) {
    return VfsUtil.isAncestor(getBaseDir(), file, false);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return false;
  }

  public boolean isValidAncestor(final VirtualFile baseDir, final VirtualFile childDir) {
    return VfsUtil.isAncestor(baseDir, childDir, false);
  }

  private VirtualFile getBaseDir() {
    return ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
  }
}
