/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ListWithSelection;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.io.IOException;

class FileInfo {
  private final VirtualFile myFile;
  private final EditFileProvider myEditFileProvider;
  private final ListWithSelection myHandleType = new ListWithSelection();

  public FileInfo(VirtualFile file, Project project) {
    myFile = file;
    myHandleType.add(HandleType.USE_FILE_SYSTEM);
    myHandleType.selectFirst();
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) {
      myEditFileProvider = null;
    }
    else {
      boolean fileExistsInVcs = vcs.fileExistsInVcs(new FilePathImpl(file));
      if (fileExistsInVcs) {
        myEditFileProvider = vcs.getEditFileProvider();
        if (myEditFileProvider != null) {
          HandleType handleType = HandleType.createForVcs(vcs);
          myHandleType.add(handleType);
          myHandleType.select(handleType);
        }
      }
      else {
        myEditFileProvider = null;
      }
    }


  }

  public VirtualFile getFile() {
    return myFile;
  }

  public boolean getUseVersionControl() {
    return ( (HandleType)myHandleType.getSelection()).getUseVcs();
  }

  public boolean hasVersionControl() {
    return myEditFileProvider != null;
  }

  public ListWithSelection getHandleType(){
    return myHandleType;
  }

  public void handle(){
    if (getUseVersionControl()){
      myEditFileProvider.editFiles(new VirtualFile[]{getFile()});
      getFile().refresh(false, false);
    } else {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            ReadOnlyAttributeUtil.setReadOnlyAttribute(getFile(), false);
          }
          catch (IOException e) {
            //ignore
          }
        }
      });
    }
  }
}
