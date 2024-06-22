package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.ide.IdeBundle;

public final class ModuleNameMacro extends Macro {
  public String getName() {
    return "ModuleName";
  }

  public String getDescription() {
    return IdeBundle.message("macro.module.file.name");
  }

  public String expand(DataContext dataContext) {
    final Module module = DataAccessor.MODULE.from(dataContext);
    if (module == null) {
      return null;
    }
    return module.getName();
  }
}