package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperaton;
import com.intellij.openapi.vcs.FilePath;

/**
 * author: lesya
 */

public class CheckoutFilesOperation extends CompositeOperaton {

  public CheckoutFilesOperation(FilePath[] files, CvsConfiguration cvsConfiguration) {
    for (int i = 0; i < files.length; i++) {
      addFile(files[i], cvsConfiguration);
    }
  }


  private void addFile(FilePath file, CvsConfiguration cvsConfiguration) {
    addOperation(
      new CheckoutFileOperation(file.getVirtualFileParent(), cvsConfiguration, file.getName(),
                                CvsEntriesManager.getInstance().getEntryFor(file.getVirtualFileParent(), file.getName()),
                                cvsConfiguration.MAKE_NEW_FILES_READONLY));
  }

  public int getFilesToProcessCount() {
    return CvsHandler.UNKNOWN_COUNT;
  }

}
