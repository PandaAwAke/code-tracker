package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.PostHighlightingPass;
import com.intellij.mock.MockProgressInidicator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.ArrayUtil;

public abstract class LightDaemonAnalyzerTestCase extends LightCodeInsightTestCase {
  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath);
    doDoTest(checkWarnings, checkInfos);
  }

  private void doDoTest(boolean checkWarnings, boolean checkInfos) {
    ((PsiManagerImpl) getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    ExpectedHighlightingData expectedData = new ExpectedHighlightingData(getEditor().getDocument(),checkWarnings, checkInfos);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    ((PsiManagerImpl) getPsiManager()).setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    HighlightInfo[] infos = doHighlighting();

    ((PsiManagerImpl) getPsiManager()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    expectedData.checkResult(infos, getEditor().getDocument().getText());
  }


  protected HighlightInfo[] doHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    Document document = getEditor().getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(getProject(), getFile(), document, 0, getFile().getTextLength(), false, true);
    action1.doCollectInformation(new MockProgressInidicator());
    HighlightInfo[] highlights1 = action1.getHighlights();

    PostHighlightingPass action2 = new PostHighlightingPass(getProject(), getFile(), getEditor(), 0, getFile().getTextLength(), false);
    action2.doCollectInformation(new MockProgressInidicator());
    HighlightInfo[] highlights2 = action2.getHighlights();

    return ArrayUtil.mergeArrays(highlights1, highlights2, HighlightInfo.class);
  }
}