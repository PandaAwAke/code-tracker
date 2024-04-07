package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.RootFormatter;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfo;
import com.intellij.cvsSupport2.cvsIgnore.UserDirIgnores;
import com.intellij.cvsSupport2.cvsstatuses.CvsEntriesListener;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * author: lesya
 */

public class CvsEntriesManager extends VirtualFileAdapter implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.application.CvsEntriesManager");

  private Map<VirtualFile, CvsInfo> myInfoByParentDirectoryPath = new com.intellij.util.containers.HashMap<VirtualFile, CvsInfo>();

  private static final String CVS_ADMIN_DIRECTORY_NAME = "CVS";

  private Collection<CvsEntriesListener> myEntriesListeners = new ArrayList<CvsEntriesListener>();
  private int myIsActive = 0;
  private Collection<String> myFilesToRefresh = new HashSet<String>();
  private int mySynchronizationActionLocks = 0;

  private final Map<String, CvsConnectionSettings> myStringToSettingsMap = new HashMap<String, CvsConnectionSettings>();
  private final UserDirIgnores myUserDirIgnores = new UserDirIgnores();

  public static CvsEntriesManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CvsEntriesManager.class);
  }

  public CvsEntriesManager() {
    myEntriesListeners = new ArrayList<CvsEntriesListener>();
  }

  public String getComponentName() {
    return "CvsEntriesAdapter";
  }

  public void initComponent() { }

  public void disposeComponent() {
    myInfoByParentDirectoryPath = new com.intellij.util.containers.HashMap<VirtualFile, CvsInfo>();
  }

  public void registerAsVirtualFileListener() {
    if (myIsActive == 0) {
      VirtualFileManager.getInstance().addVirtualFileListener(this);
    }
    myIsActive++;
  }

  public synchronized void unregisterAsVirtualFileListener() {
    LOG.assertTrue(isActive());
    myIsActive--;
    if (myIsActive == 0) {
      VirtualFileManager.getInstance().removeVirtualFileListener(this);
      myInfoByParentDirectoryPath.clear();
    }
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
    processEvent(event);
  }

  public void beforeContentsChange(VirtualFileEvent event) {
    processEvent(event);
  }

  public void contentsChanged(VirtualFileEvent event) {
    fireStatusChanged(event.getFile());
  }


  private synchronized CvsInfo getInfoFor(VirtualFile parent) {
    if (!myInfoByParentDirectoryPath.containsKey(parent)) {
      CvsInfo cvsInfo = new CvsInfo(parent, this);
      myInfoByParentDirectoryPath.put(cvsInfo.getKey(), cvsInfo);
    }
    return myInfoByParentDirectoryPath.get(parent);
  }

  public synchronized void clearChachedFiltersFor(final VirtualFile parent) {
    for (Iterator each = myInfoByParentDirectoryPath.keySet().iterator(); each.hasNext();) {
      VirtualFile file = (VirtualFile)each.next();
      if (file == null) continue;
      if (!file.isValid()) continue;
      if (VfsUtil.isAncestor(parent, file, false)) {
        myInfoByParentDirectoryPath.get(file)
          .clearFilter();
      }
    }
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      FileStatusManager.getInstance(openProjects[i]).fileStatusesChanged();
    }
  }

  private boolean isCvsIgnoreFile(VirtualFile file) {
    return CvsUtil.CVS_IGNORE_FILE.equals(file.getName());
  }

  public IgnoredFilesInfo getFilter(VirtualFile parent) {
    return getInfoFor(parent).getIgnoreFilter();
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    processEvent(event);
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
    processEvent(event);
  }

  public void fileCreated(VirtualFileEvent event) {
    processEvent(event);
  }

  private void processEvent(VirtualFileEvent event) {
    VirtualFile file = event.getFile();

    if (isUserHomeCvsIgnoreFile(file)) {
      myUserDirIgnores.clearInfo();
      fireAllStatusesChanged();
      return;
    }

    if (isCvsIgnoreFile(file)) {
      clearChachedFiltersFor(file.getParent());
      return;
    }

    if (isCvsAdminDir(file.getParent())) {
      VirtualFile parent = file.getParent().getParent();
      clearCachedEntriesFor(parent);
      return;
    }

    if (isCvsAdminDir(file)) {
      clearCachedEntriesFor(file.getParent());
      return;
    }

    if (file.isDirectory()) {
      clearCachedEntriesRecursive(file);
    }
  }

  private boolean isCvsAdminDir(VirtualFile file) {
    if (file == null) return false;
    return file.isDirectory() && CVS_ADMIN_DIRECTORY_NAME.equals(file.getName());
  }

  private synchronized void clearCachedEntriesRecursive(VirtualFile parent) {
    if (!parent.isDirectory()) return;

    for (Iterator each = myInfoByParentDirectoryPath.keySet().iterator(); each.hasNext();) {
      VirtualFile file = (VirtualFile)each.next();
      if (file == null) continue;
      if (!file.isValid()) continue;
      if (VfsUtil.isAncestor(parent, file, false)) clearCachedEntriesFor(file);
    }
  }

  public Entry getEntryFor(VirtualFile parent, String name) {
    return getCvsInfo(parent).getEntryNamed(name);
  }

  public Entry getEntryFor(VirtualFile file) {
    return getCvsInfo(CvsVfsUtil.getParentFor(file)).getEntryNamed(file.getName());
  }

  public void clearCachedEntriesFor(final VirtualFile parent) {
    if (parent == null) return;

    CvsInfo cvsInfo = getInfoFor(parent);
    cvsInfo.clearFilter();
    if (cvsInfo.isLoaded()) {
      cvsInfo.clearAll();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          onEntriesChanged(parent);
        }
      });
    }
  }

  public Entry getCashedEntry(VirtualFile parent, String fileName){
    if (parent == null) return null;

    CvsInfo cvsInfo = getInfoFor(parent);

    if (!cvsInfo.isLoaded()) return null;
    return cvsInfo.getEntryNamed(fileName);
  }

  public void setEntryForFile(final VirtualFile parent, final Entry entry) {
    if (parent == null) return;

    CvsInfo cvsInfo = getInfoFor(parent);

    if (!cvsInfo.isLoaded()) return;

    cvsInfo.setEntryAndReturnReplacedEntry(entry);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final VirtualFile file = CvsVfsUtil.findChild(parent, entry.getFileName());
        if (file != null) {
          onEntryChanged(file);
        }
      }
    });
  }

  public void removeEntryForFile(final File parent, final String fileName) {
    CvsInfo cvsInfo = getInfoFor(CvsVfsUtil.findFileByIoFile(parent));
    if (!cvsInfo.isLoaded()) return;

    cvsInfo.removeEntryNamed(fileName);

    final VirtualFile[] file = new VirtualFile[1];

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            file[0] = LocalFileSystem.getInstance().findFileByIoFile(new File(parent, fileName));
          }
        });
        if (file[0] != null) {
          onEntryChanged(file[0]);
        }
      }
    });
  }

  private void onEntriesChanged(final VirtualFile parent) {
    final CvsEntriesListener[] listeners = myEntriesListeners.toArray(new CvsEntriesListener[myEntriesListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].entriesChanged(parent);
    }
  }

  private void onEntryChanged(final VirtualFile file) {
    final CvsEntriesListener[] listeners = myEntriesListeners.toArray(new CvsEntriesListener[myEntriesListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      CvsEntriesListener listener = listeners[i];
      listener.entryChanged(file);
    }
  }

  public void watchForCvsAdminFiles(VirtualFile parent) {
    if (parent == null) return;
    synchronized (myFilesToRefresh) {
      myFilesToRefresh.add(parent.getPath() + "/" + CVS_ADMIN_DIRECTORY_NAME);
    }

    refreshFiles();
  }


  public Collection getEntriesIn(VirtualFile parent) {
    return getCvsInfo(parent).getEntries();

  }

  private CvsInfo getCvsInfo(VirtualFile parent) {
    LOG.assertTrue(isActive());
    if (parent == null) return CvsInfo.DUMMY;
    return getInfoFor(parent);
  }

  public void addCvsEntriesListener(CvsEntriesListener listener) {
    myEntriesListeners.add(listener);
  }

  public void removeCvsEntriesListener(CvsEntriesListener listener) {
    myEntriesListeners.remove(listener);
  }

  public synchronized void clearAll() {
    myInfoByParentDirectoryPath.clear();
  }

  public boolean fileIsIgnored(VirtualFile file) {
    VirtualFile parent = file.getParent();
    if (parent == null) {
      return false;
    }
    if (CvsUtil.fileIsUnderCvs(file)) return false;
    return getFilter(parent).shouldBeIgnored(file.getName());
  }

  public void lockSynchronizationActions() {
    mySynchronizationActionLocks++;
  }

  public void unlockSynchronizationActions() {
    LOG.assertTrue(mySynchronizationActionLocks > 0);
    mySynchronizationActionLocks--;
    refreshFiles();
  }

  private void refreshFiles() {
    Runnable requestVirtualFileAction = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            String[] paths;
            synchronized (myFilesToRefresh) {
              paths = myFilesToRefresh.toArray(new String[myFilesToRefresh.size()]);
              myFilesToRefresh.clear();
            }
            for (int i = 0; i < paths.length; i++) {
              String path = paths[i];
              VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
              if (virtualFile != null) virtualFile.getChildren();
            }
          }
        });
      }
    };
    ModalityContext.NON_MODAL.addRequest(requestVirtualFileAction);
  }

  public CvsConnectionSettings getCvsConnectionSettingsFor(VirtualFile root) {
    return getInfoFor(root).getConnectionSettings();
  }

  public CvsConnectionSettings getCvsConnectionSettingsFor(File root) {
    return getCvsConnectionSettingsFor(CvsVfsUtil.refreshAndFindFileByIoFile(root));
  }

  public CvsInfo getCvsInfoFor(VirtualFile directory) {
    return getInfoFor(directory);
  }

  public CvsConnectionSettings createConnectionSettingsOn(String cvsRoot) {
    if (!myStringToSettingsMap.containsKey(cvsRoot)) {
      CvsConnectionSettings settings = RootFormatter.createConfigurationOn(CvsApplicationLevelConfiguration.getInstance()
                                                                           .getConfigurationForCvsRoot(cvsRoot));
      myStringToSettingsMap.put(cvsRoot, settings);
    }
    return myStringToSettingsMap.get(cvsRoot);
  }

  public UserDirIgnores getUserdIgnores() {
    return myUserDirIgnores;
  }

  private void fireAllStatusesChanged() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      FileStatusManager.getInstance(openProjects[i]).fileStatusesChanged();
    }
  }

  private void fireStatusChanged(VirtualFile file) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      FileStatusManager.getInstance(openProjects[i]).fileStatusChanged(file);
    }
  }

  private boolean isUserHomeCvsIgnoreFile(VirtualFile file) {
    return myUserDirIgnores.userHomeCvsIgnoreFile().equals(CvsVfsUtil.getFileFor(file));
  }

  public boolean isActive() {
    return myIsActive > 0;
  }

  public String getRepositoryFor(VirtualFile root) {
    return getInfoFor(root).getRepository();
  }

  public void cacheCvsAdminInfoIn(VirtualFile root) {
    getInfoFor(root).cacheAll();
  }

  public String getStickyTagFor(VirtualFile fileByIoFile) {
    return getCvsInfo(fileByIoFile).getStickyTag();
  }
}


