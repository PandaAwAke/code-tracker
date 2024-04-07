package com.intellij.cvsSupport2.cvsoperations.common;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.IAdminReader;
import org.netbeans.lib.cvsclient.command.AbstractCommand;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public abstract class CvsOperationOnFiles extends CvsCommandOperation {
  protected Collection myFiles = new ArrayList();
  private Map myRootsToFiles;

  public CvsOperationOnFiles(IAdminReader reader) {
    super(reader);
  }

  public CvsOperationOnFiles() {
  }

  public synchronized void execute(CvsExecutionEnvironment executionEnvironment) throws VcsException, CommandAbortedException {
    synchronized (CvsOperation.class) {
      if (!myFiles.isEmpty()) {
        try {
          super.execute(executionEnvironment);
        } finally {
          clearCachedEntriesForProcessedFiles();
        }
      }
    }
  }

  private void clearCachedEntriesForProcessedFiles() {
    final CvsEntriesManager entriesManager = CvsEntriesManager.getInstance();
    for (Iterator each = myFiles.iterator(); each.hasNext();) {
      final File parentFile = ((File) each.next()).getParentFile();
      try {
        VirtualFile vParent = CvsVfsUtil.findFileByPath(parentFile.getCanonicalPath().replace(File.separatorChar, '/'));
        if (vParent != null)
          entriesManager.clearCachedEntriesFor(vParent);
      } catch (IOException error) {
        LOG.error(error);
      }
    }
  }

  protected File[] getFilesAsArray(CvsRootProvider root) {
    try {
      Collection files = (Collection)getRootsToFilesMap().get(root);
      return (File[]) files.toArray(new File[files.size()]);
    } catch (CannotFindCvsRootException e) {
      LOG.error(e);
      return new File[0];
    }
  }


  public boolean addFile(VirtualFile file) {
    return addFile(CvsVfsUtil.getPathFor(file));
  }

  public boolean addFile(File file) {
    myRootsToFiles = null;
    return addFile(file.getAbsolutePath());
  }

  public boolean addFiles(VirtualFile[] file) {
    for (int i = 0; i < file.length; i++) {
      addFile(file[i]);
    }
    return true;
  }

  public boolean addFile(String path) {
    return myFiles.add(new File(path));
  }

  public int getFilesCount(){
    return myFiles.size();
  }

  public int getFilesToProcessCount() {
    return getFilesCount();
  }

  private Map buildRootsToFilesMap() throws CannotFindCvsRootException {
    com.intellij.util.containers.HashMap result = new com.intellij.util.containers.HashMap();
    for (Iterator iterator = myFiles.iterator(); iterator.hasNext();) {
      File file = (File) iterator.next();
      CvsRootProvider cvsRoot = CvsRootProvider.createOn(file);
      if (cvsRoot == null)
        throw new CannotFindCvsRootException(file);
      else {
        if (!result.containsKey(cvsRoot))
          result.put(cvsRoot, new ArrayList());
        ((ArrayList)result.get(cvsRoot)).add(file);
      }
    }
    return result;
  }

  protected Map getRootsToFilesMap() throws CannotFindCvsRootException {
    if (myRootsToFiles == null)
      myRootsToFiles = buildRootsToFilesMap();
    return myRootsToFiles;
  }

  protected Collection getAllCvsRoots() throws CannotFindCvsRootException {
    return getRootsToFilesMap().keySet();
  }

  protected void addFilesToCommand(CvsRootProvider root, AbstractCommand command) {
    CreateFileObjects createFileObjects = new CreateFileObjects(getLocalRootFor(root), getFilesAsArray(root));
    Collection fileObjects = createFileObjects.execute();
    for (Iterator iterator = fileObjects.iterator(); iterator.hasNext();) {
      AbstractFileObject abstractFileObject = (AbstractFileObject) iterator.next();
      command.getFileObjects().addFileObject(abstractFileObject);
    }
  }

}
