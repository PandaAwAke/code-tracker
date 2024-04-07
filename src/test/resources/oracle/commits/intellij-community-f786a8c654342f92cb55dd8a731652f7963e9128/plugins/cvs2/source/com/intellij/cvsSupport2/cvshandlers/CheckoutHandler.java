package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsCheckOut.CheckoutFilesOperation;
import com.intellij.openapi.vcs.FilePath;

/**
 * author: lesya
 */
public class CheckoutHandler extends CommandCvsHandler{
  public CheckoutHandler(FilePath[] files, CvsConfiguration configuration) {
    super(com.intellij.CvsBundle.message("operation.name.check.out.files"), new CheckoutFilesOperation(files, configuration), FileSetToBeUpdated.selectedFiles(files));
  }

}
