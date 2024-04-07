
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ide.IdeBundle;

import java.io.File;

public final class FilePathRelativeToSourcepathMacro2 extends FilePathRelativeToSourcepathMacro {
  public String getName() {
    return "/FilePathRelativeToSourcepath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.file.path.relative.to.sourcepath.root.fwd.slash");
  }

  public String expand(DataContext dataContext) {
    String s = super.expand(dataContext);
    return s != null ? s.replace(File.separatorChar, '/') : null;
  }
}
