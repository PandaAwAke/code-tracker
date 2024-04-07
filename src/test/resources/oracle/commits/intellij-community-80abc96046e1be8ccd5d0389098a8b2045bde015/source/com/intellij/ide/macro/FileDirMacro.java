
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;

public final class FileDirMacro extends Macro {
  public String getName() {
    return "FileDir";
  }

  public String getDescription() {
    return "File directory";
  }

  public String expand(DataContext dataContext) {
    //Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    //if (project == null) return null;
    //VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    //if (file == null) return null;
    //if (!file.isDirectory()) {
    //  file = file.getParent();
    //  if (file == null) return null;
    //}
    VirtualFile dir = DataAccessor.VIRTUAL_DIR_OR_PARENT.from(dataContext);
    if (dir == null) return null;
    return getPath(dir);
  }
}
