package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.util.XmlUtil;

/**
 * @author mike
 */
public class IgnoreExtResourceAction extends BaseIntentionAction {
  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) return false;

    int offset = editor.getCaretModel().getOffset();
    String uri = FetchExtResourceAction.findUri(file, offset);

    if (uri == null) return false;

    XmlFile xmlFile = XmlUtil.findXmlFile(file, uri);
    if (xmlFile != null) return false;

    if (!uri.startsWith("http://") && !uri.startsWith("ftp://")) return false;

    setText("Ignore External Resource");
    return true;
  }

  public String getFamilyName() {
    return "Ignore External Resource";
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    String uri = FetchExtResourceAction.findUri(file, offset);
    if (uri == null) return;

    ExternalResourceManagerEx.getInstanceEx().addIgnoredResource(uri);
  }

}
