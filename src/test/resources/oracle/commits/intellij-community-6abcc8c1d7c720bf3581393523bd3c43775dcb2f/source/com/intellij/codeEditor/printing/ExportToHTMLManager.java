package com.intellij.codeEditor.printing;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.TreeMap;

class ExportToHTMLManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeEditor.printing.ExportToHTMLManager");

  /**
   * Should be invoked in event dispatch thread
   */
  public static void executeExport(final DataContext dataContext) {
    PsiDirectory psiDirectory = null;
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if(psiElement instanceof PsiDirectory) {
      psiDirectory = (PsiDirectory)psiElement;
    }
    final PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    String shortFileName = null;
    String directoryName = null;
    if(psiFile != null || psiDirectory != null) {
      if(psiFile != null) {
        shortFileName = psiFile.getVirtualFile().getName();
        if(psiDirectory == null) {
          psiDirectory = psiFile.getContainingDirectory();
        }
      }
      if(psiDirectory != null) {
        directoryName = psiDirectory.getVirtualFile().getPresentableUrl();
      }
    }

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    boolean isSelectedTextEnabled = false;
    if(editor != null && editor.getSelectionModel().hasSelection()) {
      isSelectedTextEnabled = true;
    }
    ExportToHTMLDialog exportToHTMLDialog = new ExportToHTMLDialog(shortFileName, directoryName, isSelectedTextEnabled, project);

    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(project);
    if(exportToHTMLSettings.OUTPUT_DIRECTORY == null) {
      final VirtualFile projectFile = project.getProjectFile();
      if (projectFile != null) {
        //noinspection HardCodedStringLiteral
        exportToHTMLSettings.OUTPUT_DIRECTORY = projectFile.getParent().getPresentableUrl() + File.separator + "exportToHTML";
      }
      else {
        exportToHTMLSettings.OUTPUT_DIRECTORY = "";
      }
    }
    exportToHTMLDialog.reset();
    exportToHTMLDialog.show();
    if(!exportToHTMLDialog.isOK()) {
      return;
    }
    exportToHTMLDialog.apply();

    final String outputDirectoryName = exportToHTMLSettings.OUTPUT_DIRECTORY;
    if(exportToHTMLSettings.getPrintScope() != PrintSettings.PRINT_DIRECTORY) {
      if(psiFile == null || psiFile.getText() == null) {
        return;
      }
      final String dirName = constructOutputDirectory(psiFile, outputDirectoryName);
      HTMLTextPainter textPainter = new HTMLTextPainter(psiFile, project, dirName, exportToHTMLSettings.PRINT_LINE_NUMBERS);
      if(exportToHTMLSettings.getPrintScope() == PrintSettings.PRINT_SELECTED_TEXT && editor != null && editor.getSelectionModel().hasSelection()) {
        int firstLine = editor.getDocument().getLineNumber(editor.getSelectionModel().getSelectionStart());
        textPainter.setSegment(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd(), firstLine);
      }
      textPainter.paint(null, psiFile.getFileType());
      if (exportToHTMLSettings.OPEN_IN_BROWSER) {
        BrowserUtil.launchBrowser(textPainter.getHTMLFileName());
      }
    }
    else {
      ExportRunnable exportRunnable = new ExportRunnable(exportToHTMLSettings, psiDirectory, outputDirectoryName, project);
      ProgressManager.getInstance().runProcessWithProgressSynchronously(exportRunnable, CodeEditorBundle.message("export.to.html.title"), true, project);
    }
  }

  private static void findClassReferences(PsiElement psiElement, TreeMap refMap, com.intellij.util.containers.HashMap filesMap, PsiFile psiFile) {
    PsiReference ref = psiElement.getReference();
    if(ref instanceof PsiJavaCodeReferenceElement) {
      PsiElement refElement = ref.resolve();
      if(refElement instanceof PsiClass) {
        PsiFile containingFile = refElement.getContainingFile();
        if(!containingFile.equals(psiFile) && filesMap.get(containingFile) != null) {
          TextRange textRange = psiElement.getTextRange();
          refMap.put(new Integer(textRange.getStartOffset()), ref);
        }
        return;
      }
    }

    PsiElement[] children = psiElement.getChildren();
    for(int i=0; i<children.length; i++) {
      findClassReferences(children[i], refMap, filesMap, psiFile);
    }
  }

  private static void exportPsiFile(final PsiFile psiFile, String outputDirectoryName, Project project, com.intellij.util.containers.HashMap filesMap) {
    ExportToHTMLSettings exportToHTMLSettings = ExportToHTMLSettings.getInstance(project);

    if (psiFile instanceof PsiBinaryFile) {
      return;
    }

    TreeMap refMap = null;
    if(exportToHTMLSettings.isGenerateHyperlinksToClasses()) {
      FileType fileType = psiFile.getFileType();
      if(StdFileTypes.JAVA == fileType || StdFileTypes.JSP == fileType) {
        refMap = new TreeMap(
          new Comparator(){
            public int compare(Object o1, Object o2) {
              if(o1 instanceof Integer && o2 instanceof Integer) {
                return ((Integer)o1).intValue() - ((Integer)o2).intValue();
              }
              return 0;
            }
          }
        );
        findClassReferences(psiFile, refMap, filesMap, psiFile);
      }
    }

    String dirName = constructOutputDirectory(psiFile, outputDirectoryName);
    HTMLTextPainter textPainter = new HTMLTextPainter(psiFile, project, dirName, exportToHTMLSettings.PRINT_LINE_NUMBERS);
    textPainter.paint(refMap, psiFile.getFileType());
  }

  private static String constructOutputDirectory(PsiFile psiFile, String outputDirectoryName) {
    return constructOutputDirectory(psiFile.getContainingDirectory(), outputDirectoryName);
  }

  private static String constructOutputDirectory(final PsiDirectory directory, final String outputDirectoryName) {
    PsiPackage psiPackage = directory.getPackage();
    String dirName = outputDirectoryName;
    if(psiPackage != null) {
      String packageName = psiPackage.getQualifiedName();
      if (packageName.length() > 0) {
        dirName += File.separator + packageName.replace('.', File.separatorChar);
      }
    }
    File dir = new File(dirName);
    dir.mkdirs();
    return dirName;
  }

  private static void addToPsiFileList(PsiDirectory psiDirectory,
                                       ArrayList filesList,
                                       boolean isRecursive,
                                       final String outputDirectoryName) {
    PsiFile[] files = psiDirectory.getFiles();
    for(int i = 0; i < files.length; i++) {
      Object obj = files[i];
      filesList.add(obj);
    }
    generateIndexHtml(psiDirectory, isRecursive, outputDirectoryName);
    if(isRecursive) {
      PsiDirectory[] directories = psiDirectory.getSubdirectories();
      for(int i = 0; i < directories.length; i++) {
        addToPsiFileList(directories[i], filesList, isRecursive, outputDirectoryName);
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void generateIndexHtml(final PsiDirectory psiDirectory, final boolean recursive, final String outputDirectoryName) {
    OutputStreamWriter writer;
    try {
      String indexHtmlName = constructOutputDirectory(psiDirectory, outputDirectoryName) + File.separator + "index.html";
      writer = new OutputStreamWriter(new FileOutputStream(indexHtmlName), "UTF-8");
      writer.write("<html><head><title>" + psiDirectory.getPackage().getQualifiedName() + "</title></head><body>");
      if (recursive) {
        PsiDirectory[] directories = psiDirectory.getSubdirectories();
        for(PsiDirectory directory: directories) {
          writer.write("<a href=\"" + directory.getName() + "/index.html\"><b>" + directory.getName() + "</b></a><br />");
        }
      }
      PsiFile[] files = psiDirectory.getFiles();
      for(PsiFile file: files) {
        if (!(file instanceof PsiBinaryFile)) {
          writer.write("<a href=\"" + getHTMLFileName(file) + "\">" + file.getVirtualFile().getName() + "</a><br />");
        }
      }
      writer.write("</body></html>");
      writer.close();
    }
    catch(IOException e) {
      LOG.error(e.getMessage(), e);
      return;
    }
  }

  private static class ExportRunnable implements Runnable {
    private ExportToHTMLSettings myExportToHTMLSettings;
    private PsiDirectory myPsiDirectory;
    private String myOutputDirectoryName;
    private Project myProject;

    public ExportRunnable(ExportToHTMLSettings exportToHTMLSettings,
                          PsiDirectory psiDirectory,
                          String outputDirectoryName,
                          Project project) {
      myExportToHTMLSettings = exportToHTMLSettings;
      myPsiDirectory = psiDirectory;
      myOutputDirectoryName = outputDirectoryName;
      myProject = project;
    }

    public void run() {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

      final ArrayList filesList = new ArrayList();
      final boolean isRecursive = myExportToHTMLSettings.isIncludeSubdirectories();

      addToPsiFileList(myPsiDirectory, filesList, isRecursive, myOutputDirectoryName);
      com.intellij.util.containers.HashMap filesMap = new com.intellij.util.containers.HashMap();
      for(int i = 0; i < filesList.size(); i++) {
        PsiFile psiFile = (PsiFile)filesList.get(i);
        filesMap.put(psiFile, psiFile);
      }
      for(int i = 0; i < filesList.size(); i++) {
        PsiFile psiFile = (PsiFile)filesList.get(i);
        if(progressIndicator.isCanceled()) {
          return;
        }
        progressIndicator.setText(CodeEditorBundle.message("export.to.html.generating.file.progress", getHTMLFileName(psiFile)));
        progressIndicator.setFraction(((double)i)/filesList.size());
        exportPsiFile(psiFile, myOutputDirectoryName, myProject, filesMap);
      }
      if (myExportToHTMLSettings.OPEN_IN_BROWSER) {
        String dirToShow = myExportToHTMLSettings.OUTPUT_DIRECTORY;
        if (!dirToShow.endsWith(File.separator)) {
          dirToShow += File.separatorChar;
        }
        PsiPackage aPackage = myPsiDirectory.getPackage();
        if (aPackage != null) {
          dirToShow += aPackage.getQualifiedName().replace('.', File.separatorChar);
        }
        BrowserUtil.launchBrowser(dirToShow);
      }
    }
  }

  static String getHTMLFileName(PsiFile psiFile) {
    //noinspection HardCodedStringLiteral
    return psiFile.getVirtualFile().getName() + ".html";
  }
}