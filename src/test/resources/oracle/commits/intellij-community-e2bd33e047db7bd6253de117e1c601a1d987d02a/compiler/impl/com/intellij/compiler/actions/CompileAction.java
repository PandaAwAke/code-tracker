package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;

import java.util.ArrayList;
import java.util.List;

public class CompileAction extends CompileActionBase {
  
  protected void doAction(DataContext dataContext, Project project) {
    final Module module = (Module)dataContext.getData(DataConstants.MODULE_CONTEXT);
    final boolean trackDependencies = CompilerWorkspaceConfiguration.getInstance(project).COMPILE_DEPENDENT_FILES;
    if (module != null) {
      CompilerManager.getInstance(project).compile(module, null, trackDependencies);
    }
    else {
      VirtualFile[] files = getCompilableFiles(project, (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY));
      if (files.length > 0) {
        CompilerManager.getInstance(project).compile(files, null, trackDependencies);
      }
    }

  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    DataContext dataContext = event.getDataContext();

    presentation.setText(ActionsBundle.actionText(IdeActions.ACTION_COMPILE));
    presentation.setVisible(true);

    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final Module module = (Module)dataContext.getData(DataConstants.MODULE_CONTEXT);

    final VirtualFile[] files = getCompilableFiles(project, (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY));
    if (module == null && files.length == 0) {
      presentation.setEnabled(false);
      return;
    }

    String elementDescription = null;
    if (module != null) {
      elementDescription = CompilerBundle.message("action.compile.description.module", module.getName());
    }
    else {
      PsiPackage aPackage = null;
      if (files.length == 1) {
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(files[0]);
        if (directory != null) {
          aPackage = directory.getPackage();
        }
      }
      else {
        PsiElement element = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
        if (element instanceof PsiPackage) {
          aPackage = (PsiPackage)element;
        }
      }

      if (aPackage != null) {
        String name = aPackage.getQualifiedName();
        if(name.length() == 0) {
          //noinspection HardCodedStringLiteral
          name = "<default>";
        }
        elementDescription = "'" + name + "'";
      }
      else if (files.length == 1) {
        final VirtualFile file = files[0];
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        if(CompilerManager.getInstance(project).isCompilableFileType(fileType) || compilerConfiguration.isResourceFile(file.getName())) {
          elementDescription = "'" + file.getName() + "'";
        }
        else  {
          if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
            // the action should be invisible in popups for non-java files
            presentation.setEnabled(false);
            presentation.setVisible(false);
            return;
          }
        }
      }
      else {
        elementDescription = CompilerBundle.message("action.compile.description.selected.files");
      }
    }

    if (elementDescription == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setText(createPresentationText(elementDescription), true);
    presentation.setEnabled(true);
  }

  private static String createPresentationText(String elementDescription) {
    StringBuffer buffer = new StringBuffer(40);
    buffer.append(ActionsBundle.actionText(IdeActions.ACTION_COMPILE)).append(" ");
    int length = elementDescription.length();
    if (length > 23) {
      if (StringUtil.startsWithChar(elementDescription, '\'')) {
        buffer.append("'");
      }
      buffer.append("...");
      buffer.append(elementDescription.substring(length - 20, length));
    }
    else {
      buffer.append(elementDescription);
    }
    return buffer.toString();
  }

  private static VirtualFile[] getCompilableFiles(Project project, VirtualFile[] files) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final PsiManager psiManager = PsiManager.getInstance(project);
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final FileTypeManager typeManager = FileTypeManager.getInstance();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    final List<VirtualFile> filesToCompile = new ArrayList<VirtualFile>();
    for (final VirtualFile file : files) {
      if (!fileIndex.isInSourceContent(file)) {
        continue;
      }
      if (!(file.getFileSystem() instanceof LocalFileSystem)) {
        continue;
      }
      if (file.isDirectory()) {
        final PsiDirectory directory = psiManager.findDirectory(file);
        if (directory == null || directory.getPackage() == null) {
          continue;
        }
      }
      else {
        FileType fileType = typeManager.getFileTypeByFile(file);
        if (!(compilerManager.isCompilableFileType(fileType) || compilerConfiguration.isResourceFile(file.getName()))) {
          continue;
        }
      }
      filesToCompile.add(file);
    }
    return filesToCompile.size() > 0? filesToCompile.toArray(new VirtualFile[filesToCompile.size()]) : VirtualFile.EMPTY_ARRAY;
  }
}