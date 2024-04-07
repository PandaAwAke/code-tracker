package com.intellij.ide.startup.impl;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
public class StartupManagerImpl extends StartupManagerEx implements ProjectComponent {
  private List<Runnable> myActivities = new ArrayList<Runnable>();
  private List<Runnable> myPostStartupActivities = new ArrayList<Runnable>();
  private List<Runnable> myPreStartupActivities = new ArrayList<Runnable>();

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private FileSystemSynchronizer myFileSystemSynchronizer = new FileSystemSynchronizer();
  private boolean myStartupActivityRunning = false;
  private boolean myStartupActivityPassed = false;

  private Project myProject;

  public StartupManagerImpl(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public String getComponentName() {
    return "StartupManager";
  }

  public void registerStartupActivity(Runnable runnable) {
    myActivities.add(runnable);
  }

  public void registerPostStartupActivity(Runnable runnable) {
    myPostStartupActivities.add(runnable);
  }

  public boolean startupActivityRunning() {
    return myStartupActivityRunning;
  }

  public boolean startupActivityPassed() {
    return myStartupActivityPassed;
  }

  public void registerPreStartupActivity(Runnable runnable) {
    myPreStartupActivities.add(runnable);
  }

  public FileSystemSynchronizer getFileSystemSynchronizer() {
    return myFileSystemSynchronizer;
  }

  public void runStartupActivities() {
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          runActivities(myPreStartupActivities);
          myFileSystemSynchronizer.execute();
          myFileSystemSynchronizer = null;
          myStartupActivityRunning = true;
          runActivities(myActivities);

          myStartupActivityRunning = false;
          myStartupActivityPassed = true;
        }
      }
    );
  }

  public void runPostStartupActivities() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    runActivities(myPostStartupActivities);
  }

  private void runActivities(final List<Runnable> activities) {
    try {
      for (Iterator<Runnable> iterator = activities.iterator(); iterator.hasNext();) {
        Runnable runnable = iterator.next();
        try {
          runnable.run();
        }
        catch (Exception ex) {
          LOG.error(ex);
        }
      }
    }
    finally {
      activities.clear();
    }
  }

  public void runWhenProjectIsInitialized(final Runnable action) {
    final Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };

    if (myProject.isInitialized()) {
      runnable.run();
    }
    else {
      registerPostStartupActivity(new Runnable(){
        public void run() {
          runnable.run();
        }
      });
    }
  }
}
