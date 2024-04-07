package com.intellij.codeInsight.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public abstract class AbstractLayoutCodeProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.AbstractLayoutCodeProcessor");

  protected final Project myProject;
  protected final Module myModule;

  private PsiDirectory myDirectory;
  private PsiFile myFile;
  private PsiFile[] myFiles;
  private boolean myIncludeSubdirs;

  private final String myProgressText;
  private final String myCommandName;
  private final Runnable myPostRunnable;

  protected AbstractLayoutCodeProcessor(Project project, String commandName, String progressText) {
    myProject = project;
    myModule = null;
    myDirectory = null;
    myIncludeSubdirs = true;
    myCommandName = commandName;
    myProgressText = progressText;
    myPostRunnable = null;
  }

  protected AbstractLayoutCodeProcessor(Project project, Module module, String commandName, String progressText) {
    myProject = project;
    myModule = module;
    myDirectory = null;
    myIncludeSubdirs = true;
    myCommandName = commandName;
    myProgressText = progressText;
    myPostRunnable = null;
  }

  protected AbstractLayoutCodeProcessor(Project project, PsiDirectory directory, boolean includeSubdirs, String progressText, String commandName) {
    myProject = project;
    myModule = null;
    myDirectory = directory;
    myIncludeSubdirs = includeSubdirs;
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = null;
  }

  protected AbstractLayoutCodeProcessor(Project project, PsiFile file, String progressText, String commandName) {
    myProject = project;
    myModule = null;
    myFile = file;
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = null;
  }

  protected AbstractLayoutCodeProcessor(Project project, PsiFile[] files, String progressText, String commandName, Runnable postRunnable) {
    myProject = project;
    myModule = null;
    myFiles = filterFiles(files);
    myProgressText = progressText;
    myCommandName = commandName;
    myPostRunnable = postRunnable;
  }

  private static PsiFile[] filterFiles(PsiFile[] files){
    ArrayList<PsiFile> list = new ArrayList<PsiFile>();
    for(int i = 0; i < files.length; i++){
      PsiFile file = files[i];
      if (isFormatable(file)){
        list.add(file);
      }
    }
    return list.toArray(new PsiFile[list.size()]);
  }

  protected abstract Runnable preprocessFile(PsiFile file) throws IncorrectOperationException;

  public void run() {
    if (myDirectory != null){
      runProcessDirectory(myDirectory, myIncludeSubdirs);
    }
    else if (myFiles != null){
      runProcessFiles(myFiles);
    }
    else if (myFile != null) {
      runProcessFile(myFile);
    }
    else if (myModule != null) {
      runProcessOnModule(myModule);
    }
    else if (myProject != null) {
      runProcessOnProject(myProject);
    }
  }

  private void runProcessFile(final PsiFile file) {
    if (!file.isWritable()){
      Messages.showMessageDialog(
        myProject,
        "Cannot modify a read-only file.",
        "File Is Read-Only",
        Messages.getErrorIcon()
      );
      Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      document.fireReadOnlyModificationAttempt();
      return;
    }

    final Runnable[] resultRunnable = new Runnable[1];
    Runnable readAction = new Runnable() {
      public void run() {
        if (!checkFileWritable(file)) return;
        try{
          resultRunnable[0] = preprocessFile(file);
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
      }
    };
    Runnable writeAction = new Runnable() {
      public void run() {
        if (resultRunnable[0] != null){
          resultRunnable[0].run();
        }
      }
    };
    runLayoutCodeProcess(readAction, writeAction);
  }

  private boolean checkFileWritable(final PsiFile file){
    if (!file.isWritable()){
      MessagesEx.fileIsReadOnly(myProject, file.getVirtualFile())
          .setTitle("Cannot Process File")
          .showLater();
      return false;
    }
    else{
      return true;
    }
  }

  private Runnable preprocessFiles(List<PsiFile> files) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    String oldText = null;
    double oldFraction = 0;
    if (progress != null){
      oldText = progress.getText();
      oldFraction = progress.getFraction();
      progress.setText(myProgressText);
    }

    final Runnable[] runnables = new Runnable[files.size()];
    for(int i = 0; i < files.size(); i++) {
      PsiFile file = files.get(i);
      if (progress != null){
        if (progress.isCanceled()) return null;
        progress.setFraction((double)i / files.size());
      }
      if (file.isWritable()){
        try{
          runnables[i] = preprocessFile(file);
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
      }
      files.set(i, null);
    }

    if (progress != null){
      progress.setText(oldText);
      progress.setFraction(oldFraction);
    }

    return new Runnable() {
      public void run() {
        for(int i = 0; i < runnables.length; i++){
          Runnable runnable = runnables[i];
          if (runnable != null){
            runnable.run();
          }
        }
      }
    };
  }

  private void runProcessFiles(final PsiFile[] files) {
    // let's just ignore read-only files here

    final Runnable[] resultRunnable = new Runnable[1];
    runLayoutCodeProcess(
      new Runnable() {
        public void run() {
          resultRunnable[0] = preprocessFiles(new ArrayList<PsiFile>(Arrays.asList(files)));
        }
      },
      new Runnable() {
        public void run() {
          if (resultRunnable[0] != null){
            resultRunnable[0].run();
          }
        }
      }
    );
  }

  private void runProcessDirectory(final PsiDirectory directory, final boolean recursive) {
    final ArrayList<PsiFile> array = new ArrayList<PsiFile>();
    collectFilesToProcess(array, directory, recursive);
    final String where = "Directory '" + directory.getVirtualFile().getPresentableUrl() + "'";
    runProcessOnFiles(where, array);
  }

  private void runProcessOnProject(final Project project) {
    final ArrayList<PsiFile> array = new ArrayList<PsiFile>();
    collectFilesInProject(project, array);
    String where = "Project '" + project.getProjectFilePath() + "'";
    runProcessOnFiles(where, array);
  }

  private void runProcessOnModule(final Module module) {
    final ArrayList<PsiFile> array = new ArrayList<PsiFile>();
    collectFilesInModule(module, array);
    String where = "Module '" + module.getModuleFilePath() + "'";
    runProcessOnFiles(where, array);
  }

  private void collectFilesInProject(Project project, ArrayList<PsiFile> array) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      collectFilesInModule(module, array);
    }
  }

  private void collectFilesInModule(Module module, ArrayList<PsiFile> array) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (int i = 0; i < contentRoots.length; i++) {
      VirtualFile root = contentRoots[i];
      PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(root);
      if (dir != null) {
        collectFilesToProcess(array, dir, true);
      }
    }
  }

  private void runProcessOnFiles(final String where, final ArrayList<PsiFile> array) {
    boolean checkWritable = true;
    for(int i = 0; i < array.size(); i++){
      PsiFile file = array.get(i);
      if (checkWritable && !file.isWritable()){
        int res = Messages.showOkCancelDialog(
          myProject,
          where + " contains read-only file(s).\nProcess all other files?",
          "Cannot Modify Read-Only Files",
          Messages.getQuestionIcon()
        );
        if (res != 0){
          array.clear();
          break;
        }
        checkWritable = false;
      }
    }
    if (array.isEmpty()) return;

    final Runnable[] resultRunnable = new Runnable[1];
    runLayoutCodeProcess(
      new Runnable() {
        public void run() {
          resultRunnable[0] = preprocessFiles(array);
        }
      },
      new Runnable() {
        public void run() {
          if (resultRunnable[0] != null){
            resultRunnable[0].run();
          }
        }
      }
    );
  }

  private static boolean isFormatable(PsiFile file) {
    return file instanceof PsiJavaFile || file instanceof XmlFile;
  }

  private void collectFilesToProcess(ArrayList<PsiFile> array, PsiDirectory dir, boolean recursive) {
    PsiFile[] files = dir.getFiles();
    for(int i = 0; i < files.length; i++) {
      PsiFile file = files[i];
      if (isFormatable(file)){
        array.add(file);
      }
    }
    if (recursive){
      PsiDirectory[] subdirs = dir.getSubdirectories();
      for(int i = 0; i < subdirs.length; i++) {
        collectFilesToProcess(array, subdirs[i], recursive);
      }
    }
  }

  private void runLayoutCodeProcess(final Runnable readAction, final Runnable writeAction) {
    final ProgressWindow progressWindow = new ProgressWindow(true, myProject);
    progressWindow.setTitle(myCommandName);
    progressWindow.setText(myProgressText);

    final ModalityState modalityState = ModalityState.current();

    final Runnable process = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runReadAction(readAction);
      }
    };

    Runnable runnable = new Runnable() {
      public void run() {
        try {
          //DaemonCodeAnalyzer.getInstance(myProject).setUpdateByTimerEnabled(false);
          ProgressManager.getInstance().runProcess(process, progressWindow);
        }
        catch(ProcessCanceledException e) {
          return;
        }
        /*
        finally {
          DaemonCodeAnalyzer.getInstance(myProject).setUpdateByTimerEnabled(true);
        }
        */

        ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().executeCommand(
                      myProject,
                      new Runnable() {
                        public void run() {
                          CommandProcessor.getInstance().markCurrentCommandAsComplex(myProject);
                          ApplicationManager.getApplication().runWriteAction(writeAction);

                          if (myPostRunnable != null){
                            ApplicationManager.getApplication().invokeLater(myPostRunnable);
                          }
                        }
                      },
                      myCommandName,
                      null
                  );
                }
              }, modalityState);
      }
    };
    new Thread(runnable, myCommandName).start();
  }
}
