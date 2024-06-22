/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;

public class ExcludeEntryDescription {
  private boolean myIsFile = true;
  private boolean myIncludeSubdirectories;
  private VirtualFilePointer myFilePointer;

  public ExcludeEntryDescription(VirtualFile virtualFile, boolean includeSubdirectories, boolean isFile) {
    myFilePointer = VirtualFilePointerManager.getInstance().create(virtualFile, null);
    myIncludeSubdirectories = includeSubdirectories;
    myIsFile = isFile;
  }

  public ExcludeEntryDescription(String url, boolean includeSubdirectories, boolean isFile) {
    myFilePointer = VirtualFilePointerManager.getInstance().create(url, null);
    myIncludeSubdirectories = includeSubdirectories;
    myIsFile = isFile;
  }

  public ExcludeEntryDescription copy() {
    return new ExcludeEntryDescription(getUrl(), myIncludeSubdirectories, myIsFile);
  }

  public boolean isFile() {
    return myIsFile;
  }

  public String getUrl() {
    return myFilePointer.getUrl();
  }

  public String getPresentableUrl() {
    return myFilePointer.getPresentableUrl();
  }

  public boolean isIncludeSubdirectories() {
    return myIncludeSubdirectories;
  }

  public void setIncludeSubdirectories(boolean includeSubdirectories) {
    myIncludeSubdirectories = includeSubdirectories;
  }

  public VirtualFile getVirtualFile() {
    return myFilePointer.getFile();
  }

  public boolean isValid() {
    return myFilePointer.isValid();
  }

  public boolean equals(Object obj) {
    if(!(obj instanceof ExcludeEntryDescription)) {
      return false;
    }
    ExcludeEntryDescription entryDescription = (ExcludeEntryDescription)obj;
    if(entryDescription.myIsFile != myIsFile) {
      return false;
    }
    if(entryDescription.myIncludeSubdirectories != myIncludeSubdirectories) {
      return false;
    }
    if(!Comparing.equal(entryDescription.getUrl(), getUrl())) {
      return false;
    }
    return true;
  }

  public int hashCode() {
    return getUrl().hashCode();
  }
}
