
package com.intellij.find.findUsages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScopeCache;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.find.FindBundle;

import javax.swing.*;

public class FindPackageUsagesDialog extends FindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbClassesUsages;

  public FindPackageUsagesDialog(PsiElement element,
                                 Project project,
                                 FindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab,
                                 boolean isShowInNewTabEnabled,
                                 boolean isSingleFile) {
    super(element, project, findUsagesOptions, toShowInNewTab, isShowInNewTabEnabled, isSingleFile);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
  }

  public FindUsagesOptions getShownOptions(){
    FindUsagesOptions options = new FindUsagesOptions(SearchScopeCache.getInstance(myProject));
    options.clear();
    options.isUsages = true;
    options.isClassesUsages = isToChange(myCbClassesUsages);
    options.isSearchForTextOccurences = isToChange(myCbToSearchForTextOccurences);
    return options;
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages);
    if (isToChange(myCbClassesUsages)){
      options.isClassesUsages = isSelected(myCbClassesUsages);
    }
    options.isSkipPackageStatements = false;
    options.isSkipImportStatements = false;
  }

  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group")));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), myFindUsagesOptions.isUsages, findWhatPanel, true);
    myCbClassesUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.of.classes.and.interfaces"), myFindUsagesOptions.isClassesUsages, findWhatPanel, true);

    return findWhatPanel;
  }

  protected void update() {
    if(myCbToSearchForTextOccurences != null){
      if (isSelected(myCbUsages)){
        myCbToSearchForTextOccurences.makeSelectable();
      }
      else{
        myCbToSearchForTextOccurences.makeUnselectable(false);
      }
    }

    boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbClassesUsages);
    setOKActionEnabled(hasSelected);
  }
}