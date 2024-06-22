package com.intellij.ide.util;

import com.intellij.ide.IdeView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiRootPackageType;
import com.intellij.util.ActionRunner;
import com.intellij.util.Degenerator;
import com.intellij.util.IncorrectOperationException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PackageUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.util.PackageUtil");

  /**
   * @deprecated
   */
  public static PsiDirectory findOrCreateDirectoryForPackage(Project project,
                                                             String packageName,
                                                             PsiDirectory baseDir,
                                                             boolean askUserToCreate) throws IncorrectOperationException {

    PsiDirectory psiDirectory = null;

    if (!"".equals(packageName)) {
      PsiPackage rootPackage = findLongestExistingPackage(project, packageName);
      if (rootPackage != null) {
        int beginIndex = rootPackage.getQualifiedName().length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (packageName.length() > 0) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        psiDirectory = selectDirectory(project, rootPackage.getDirectories(), baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      PsiDirectory[] sourceDirectories = PsiManager.getInstance(project).getRootDirectories(PsiRootPackageType.SOURCE_PATH);
      psiDirectory = selectDirectory(project, sourceDirectories, baseDir,
                                     File.separatorChar + packageName.replace('.', File.separatorChar));
      if (psiDirectory == null) return null;
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (restOfName.length() > 0) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          int toCreate = Messages.showYesNoDialog(project,
                                                  "Package " + packageName + " does not exist.\nDo you want to create it?",
                                                  "Package Not Found",
                                                  Messages.getQuestionIcon());
          if (toCreate != 0) {
            return null;
          }
          askedToCreate = true;
        }
        psiDirectory = createSubdirectory(psiDirectory, name, project);
      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  private static PsiDirectory createSubdirectory(final PsiDirectory oldDirectory,
                                                 final String name, Project project) throws IncorrectOperationException {
    final PsiDirectory[] psiDirectory = new PsiDirectory[1];
    final IncorrectOperationException[] exception = new IncorrectOperationException[1];

    CommandProcessor.getInstance().executeCommand(project, new Runnable(){
      public void run() {
        psiDirectory[0] = ApplicationManager.getApplication().runWriteAction(new Computable<PsiDirectory>() {
          public PsiDirectory compute() {
            try {
              return oldDirectory.createSubdirectory(name);
            }
            catch (IncorrectOperationException e) {
              exception[0] = e;
              return null;
            }
          }
        });
      }
    }, "Create New Subdirectory", null);

    if (exception[0] != null) throw exception[0];

    return psiDirectory[0];
  }

  public static PsiDirectory findOrCreateDirectoryForPackage(Module module,
                                                             String packageName,
                                                             PsiDirectory baseDir,
                                                             boolean askUserToCreate) throws IncorrectOperationException {
    final Project project = module.getProject();
    PsiDirectory psiDirectory = null;
    if (!"".equals(packageName)) {
      PsiPackage rootPackage = findLongestExistingPackage(module, packageName);
      if (rootPackage != null) {
        int beginIndex = rootPackage.getQualifiedName().length() + 1;
        packageName = beginIndex < packageName.length() ? packageName.substring(beginIndex) : "";
        String postfixToShow = packageName.replace('.', File.separatorChar);
        if (packageName.length() > 0) {
          postfixToShow = File.separatorChar + postfixToShow;
        }
        PsiDirectory[] moduleDirectories = getPackageDirectoriesInModule(rootPackage, module);
        psiDirectory = selectDirectory(project, moduleDirectories, baseDir, postfixToShow);
        if (psiDirectory == null) return null;
      }
    }

    if (psiDirectory == null) {
      if (!ModuleUtil.checkSourceRootsConfigured(module)) return null;
      final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
      List<PsiDirectory> directoryList = new ArrayList<PsiDirectory>();
      for (int i = 0; i < sourceRoots.length; i++) {
        VirtualFile sourceRoot = sourceRoots[i];
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
        if (directory != null) {
          directoryList.add(directory);
        }
      }
      PsiDirectory[] sourceDirectories = directoryList.toArray(new PsiDirectory[directoryList.size()]);
      psiDirectory = selectDirectory(project, sourceDirectories, baseDir,
                                     File.separatorChar + packageName.replace('.', File.separatorChar));
      if (psiDirectory == null) return null;
    }

    String restOfName = packageName;
    boolean askedToCreate = false;
    while (restOfName.length() > 0) {
      final String name = getLeftPart(restOfName);
      PsiDirectory foundExistingDirectory = psiDirectory.findSubdirectory(name);
      if (foundExistingDirectory == null) {
        if (!askedToCreate && askUserToCreate) {
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            askedToCreate = true;
          }
          else {
            int toCreate = Messages.showYesNoDialog(project,
                                                    "Package " + packageName + " does not exist.\nDo you want to create it?",
                                                    "Package not found",
                                                    Messages.getQuestionIcon());
            if (toCreate != 0) {
              return null;
            }
          }
          askedToCreate = true;
        }

        final PsiDirectory psiDirectory1 = psiDirectory;
        try {
          psiDirectory = ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnableWithResult<PsiDirectory>() {
            public PsiDirectory run() throws Exception {
              return psiDirectory1.createSubdirectory(name);
            }
          });
        }
        catch (Exception e) {
          if (e instanceof IncorrectOperationException) {
            throw (IncorrectOperationException)e;
          }
          if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
          }
          LOG.error(e);
          Degenerator.unableToDegenerateMarker();
        }

      }
      else {
        psiDirectory = foundExistingDirectory;
      }
      restOfName = cutLeftPart(restOfName);
    }
    return psiDirectory;
  }

  private static PsiDirectory[] getPackageDirectoriesInModule(PsiPackage rootPackage, Module module) {
    PsiManager manager = PsiManager.getInstance(module.getProject());
    final String packageName = rootPackage.getQualifiedName();
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final ModuleFileIndex moduleFileIndex = moduleRootManager.getFileIndex();
    final VirtualFile[] directories = moduleFileIndex.getDirectoriesByPackageName(packageName, false);
    List<PsiDirectory> moduleDirectoryList = new ArrayList<PsiDirectory>();
    for (int i = 0; i < directories.length; i++) {
      VirtualFile directory = directories[i];
      moduleDirectoryList.add(manager.findDirectory(directory));
    }

    return moduleDirectoryList.toArray(new PsiDirectory[moduleDirectoryList.size()]);
  }

  private static PsiPackage findLongestExistingPackage(Project project, String packageName) {
    PsiManager manager = PsiManager.getInstance(project);
    String nameToMatch = packageName;
    while (true) {
      PsiPackage aPackage = manager.findPackage(nameToMatch);
      if (aPackage != null && isWritablePackage(aPackage)) return aPackage;
      int lastDotIndex = nameToMatch.lastIndexOf(".");
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      } else {
        return null;
      }
    }
  }

  private static boolean isWritablePackage(PsiPackage aPackage) {
    PsiDirectory[] directories = aPackage.getDirectories();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (directory.isValid() && directory.isWritable()) {
        return true;
      }
    }
    return false;
  }

  private static PsiDirectory getWritableDirectory(VirtualFile[] vFiles, PsiManager manager) {
    for (int i = 0; i < vFiles.length; i++) {
      PsiDirectory directory = manager.findDirectory(vFiles[i]);
      if (directory != null && directory.isValid() && directory.isWritable()) {
        return directory;
      }
    }
    return null;
  }

  private static PsiPackage findLongestExistingPackage(Module module, String packageName) {
    final ModuleFileIndex moduleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    final PsiManager manager = PsiManager.getInstance(module.getProject());

    String nameToMatch = packageName;
    while (true) {
      VirtualFile[] vFiles = moduleFileIndex.getDirectoriesByPackageName(nameToMatch, false);
      PsiDirectory directory = getWritableDirectory(vFiles, manager);
      if (directory != null) return directory.getPackage();

      int lastDotIndex = nameToMatch.lastIndexOf(".");
      if (lastDotIndex >= 0) {
        nameToMatch = nameToMatch.substring(0, lastDotIndex);
      } else {
        return null;
      }
    }
  }

  private static String getLeftPart(String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(0, index) : packageName;
  }

  private static String cutLeftPart(String packageName) {
    int index = packageName.indexOf('.');
    return index > -1 ? packageName.substring(index + 1) : "";
  }

  public static PsiDirectory selectDirectory(Project project,
                                             PsiDirectory[] packageDirectories,
                                             PsiDirectory defaultDirectory,
                                             String postfixToShow) {
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    ArrayList<PsiDirectory> possibleDirs = new ArrayList<PsiDirectory>();
    for (int i = 0; i < packageDirectories.length; i++) {
      PsiDirectory dir = packageDirectories[i];
      if (!dir.isValid()) continue;
      if (!dir.isWritable()) continue;
      if (possibleDirs.contains(dir)) continue;
      if (!projectFileIndex.isInContent(dir.getVirtualFile())) continue;
      possibleDirs.add(dir);
    }

    if (possibleDirs.size() == 0) return null;
    if (possibleDirs.size() == 1) return possibleDirs.get(0);

    if (ApplicationManager.getApplication().isUnitTestMode()) return possibleDirs.get(0);

    DirectoryChooser chooser = new DirectoryChooser(project);
    chooser.setTitle("Choose Destination Directory");
    chooser.fillList(possibleDirs.toArray(new PsiDirectory[possibleDirs.size()]), defaultDirectory, project, postfixToShow);
    chooser.show();
    return chooser.isOK() ? chooser.getSelectedDirectory() : null;
  }

  public static PsiDirectory getOrChooseDirectory(IdeView view) {
    PsiDirectory[] dirs = view.getDirectories();
    if (dirs.length == 0) return null;
    if (dirs.length == 1) {
      return dirs[0];
    }
    else {
      Project project = dirs[0].getProject();
      return selectDirectory(project, dirs, null, "");
    }
  }
}