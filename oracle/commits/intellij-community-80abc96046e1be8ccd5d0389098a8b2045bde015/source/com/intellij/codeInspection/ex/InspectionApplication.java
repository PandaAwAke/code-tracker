package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionMain;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;

import javax.swing.*;
import java.io.*;

/**
 * @author max
 */
public class InspectionApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionApplication");

  public String myProjectPath = null;
  public String myOutPath = null;
  public String mySourceDirectory = null;
  public String myProfilePath = null;
  private Project myProject;
  private int myVerboseLevel = 0;

  public void startup() {
    if (myProjectPath == null || myOutPath == null || myProfilePath == null) {
      InspectionMain.printHelp();
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        ApplicationEx application = ApplicationManagerEx.getApplicationEx();
        try {
          logMessage(1, "Starting up... ");
          application.doNotSave();
          application.load(PathManager.getOptionsPath());
          logMessageLn(1, "done.");

          InspectionApplication.this.run();
        }
        catch (Exception e) {
          LOG.error(e);
        }
        finally {
          application.exit();
        }
      }
    });
  }

  public void run() {
    try {
      myProjectPath = myProjectPath.replace(File.separatorChar, '/');
      VirtualFile vfsProject = LocalFileSystem.getInstance().findFileByPath(myProjectPath);
      if (vfsProject == null) {
        logError("File " + myProjectPath + " cannot be found");
        InspectionMain.printHelp();
      }

      File profileFile = new File(myProfilePath);
      if (!profileFile.exists()) {
        logError("File " + myProfilePath + " cannot be found");
        InspectionMain.printHelp();
      }


      logMessage(1, "Opening project... ");
      myProject = ProjectManagerEx.getInstanceEx().loadProject(myProjectPath);
      logMessageLn(1, "done.");
      logMessage(1, "Initializing project...");

      final InspectionManagerEx im = (InspectionManagerEx)InspectionManager.getInstance(myProject);
      final AnalysisScope scope;

      InspectionProfileImpl profile = new InspectionProfileImpl(profileFile, null);
      im.setProfile(profile);

      if (mySourceDirectory == null) {
        scope = new AnalysisScope(myProject);
        runStartupActivity();
      }
      else {
        mySourceDirectory = mySourceDirectory.replace(File.separatorChar, '/');

        VirtualFile vfsDir = LocalFileSystem.getInstance().findFileByPath(mySourceDirectory);
        if (vfsDir == null) {
          logError("Directory " + mySourceDirectory + " cannot be found");
          InspectionMain.printHelp();
        }

        runStartupActivity();
        PsiDirectory psiDirectory = PsiManager.getInstance(myProject).findDirectory(vfsDir);
        scope = new AnalysisScope(psiDirectory);
      }

      logMessageLn(1, "done.");

      final OutputStream outStream = new BufferedOutputStream(new FileOutputStream(myOutPath));

      PsiClass psiObjectClass = PsiManager.getInstance(myProject).findClass("java.lang.Object");
      if (psiObjectClass == null) {
        logError("The JDK is not configured properly for this project. Inspection cannot run.");
        return;
      }

      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          im.launchInspectionsOffline(scope, outStream);
          logMessageLn(1, "\nDone.\n");
        }
      }, new ProgressIndicatorBase() {
        private String lastPrefix = "";

        public void setText(String text) {
          if (myVerboseLevel == 0) return;

          if (myVerboseLevel == 1) {
            int idx = text.indexOf(" in ");
            if (idx == -1) {
              idx = text.indexOf(" of ");
            }

            if (idx == -1) return;
            String prefix = text.substring(0, idx);
            if (prefix.equals(lastPrefix)) {
              logMessage(1, ".");
              return;
            }
            lastPrefix = prefix;
            logMessageLn(1, "");
            logMessageLn(1, prefix);
            return;
          }

          logMessageLn(2, text);
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
      InspectionMain.printHelp();
    }
    catch (Exception e) {
      LOG.error(e);
      System.exit(1);
    }
  }

  private void runStartupActivity() {
    ((StartupManagerImpl)StartupManager.getInstance(myProject)).runStartupActivities();
  }

  /* TODO
  public Object getData(String dataId) {
    if (DataConstants.PROJECT.equals(dataId)) return myProject;
    return super.getData(dataId);
  }
  */

  public void setVerboseLevel(int verboseLevel) {
    myVerboseLevel = verboseLevel;
  }

  private void logMessage(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.print(message);
    }
  }

  private void logError(String message) {
    System.err.println(message);
  }

  private void logMessageLn(int minVerboseLevel, String message) {
    if (myVerboseLevel >= minVerboseLevel) {
      System.out.println(message);
    }
  }
}
