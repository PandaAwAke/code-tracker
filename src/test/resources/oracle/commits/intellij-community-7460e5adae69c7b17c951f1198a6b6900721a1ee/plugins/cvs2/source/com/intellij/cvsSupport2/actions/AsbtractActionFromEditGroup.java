package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.actionVisibility.CvsActionVisibility;

public abstract class AsbtractActionFromEditGroup extends ActionOnSelectedElement {
  public AsbtractActionFromEditGroup() {
    super(false);

    CvsActionVisibility visibility = getVisibility();
    visibility.canBePerformedOnSeveralFiles();
    visibility.addCondition(FILES_EXIST_IN_CVS);
    visibility.addCondition(FILES_ARE_NOT_DELETED);

  }
}