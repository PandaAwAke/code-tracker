package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * @author max
 */
public class VirtualFileHolder implements FileHolder {
  private final Set<VirtualFile> myFiles = new HashSet<VirtualFile>();
  private final Project myProject;
  private final HolderType myType;

  public VirtualFileHolder(Project project, final HolderType type) {
    myProject = project;
    myType = type;
  }

  public HolderType getType() {
    return myType;
  }

  public synchronized void cleanAll() {
    myFiles.clear();
  }

  public void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        // to avoid deadlocks caused by incorrect lock ordering, need to lock on this after taking read action
        synchronized(VirtualFileHolder.this) {
          if (myProject.isDisposed() || myFiles.isEmpty()) return;
          final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myFiles);
          if (scope.getRecursivelyDirtyDirectories().size() == 0) {
            final Set<FilePath> dirtyFiles = scope.getDirtyFiles();
            boolean cleanedDroppedFiles = false;
            for(FilePath dirtyFile: dirtyFiles) {
              VirtualFile f = dirtyFile.getVirtualFile();
              if (f != null) {
                myFiles.remove(f);
              }
              else {
                if (!cleanedDroppedFiles) {
                  cleanedDroppedFiles = true;
                  for(VirtualFile file: currentFiles) {
                    if (fileDropped(file)) myFiles.remove(file);
                  }
                }
              }
            }
          }
          else {
            for (VirtualFile file : currentFiles) {
              if (fileDropped(file) || scope.belongsTo(new FilePathImpl(file))) {
                myFiles.remove(file);
              }
            }
          }
        }
      }
    });
  }

  private boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) == null;
  }

  public synchronized void addFile(VirtualFile file) {
    myFiles.add(file);
  }

  public synchronized void removeFile(VirtualFile file) {
    myFiles.remove(file);
  }

  public synchronized void removeFiles(final Collection<VirtualFile> files) {
    myFiles.removeAll(files);
  }

  public synchronized void addFiles(final Collection<VirtualFile> files) {
    myFiles.addAll(files);
  }

  public synchronized List<VirtualFile> getFiles() {
    return new ArrayList<VirtualFile>(myFiles);
  }

  public synchronized VirtualFileHolder copy() {
    final VirtualFileHolder copyHolder = new VirtualFileHolder(myProject, myType);
    copyHolder.myFiles.addAll(myFiles);
    return copyHolder;
  }

  public synchronized boolean containsFile(final VirtualFile file) {
    return myFiles.contains(file);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VirtualFileHolder that = (VirtualFileHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}
