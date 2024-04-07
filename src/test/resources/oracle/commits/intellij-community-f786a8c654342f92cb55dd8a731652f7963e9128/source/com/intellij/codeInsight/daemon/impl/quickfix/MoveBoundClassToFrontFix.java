package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceList;
import com.intellij.util.IncorrectOperationException;

public class MoveBoundClassToFrontFix extends ExtendsListFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.MoveBoundClassToFrontFix");

  public MoveBoundClassToFrontFix(PsiClass aClass, PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
  }

  public String getText() {
    return QuickFixBundle.message("move.bound.class.to.front.fix.text",
                                  HighlightUtil.formatClass(myClassToExtendFrom),
                                  HighlightUtil.formatClass(myClass));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("move.class.in.extend.list.family");
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    PsiReferenceList extendsList = myClass.getExtendsList();
    if (extendsList == null) return;
    try {
      modifyList(extendsList, false, -1);
      modifyList(extendsList, true, 0);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    QuickFixAction.markDocumentForUndo(file);
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        myClass != null
        && myClass.isValid()
        && myClass.getManager().isInProject(myClass)
        && myClassToExtendFrom != null
        && myClassToExtendFrom.isValid()
    ;
  }
}
