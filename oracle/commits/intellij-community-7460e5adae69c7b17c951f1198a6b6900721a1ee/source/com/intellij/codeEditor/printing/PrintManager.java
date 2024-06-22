package com.intellij.codeEditor.printing;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.Highlighter;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

import java.awt.print.*;
import java.util.ArrayList;

class PrintManager {
  public static void executePrint(DataContext dataContext) {
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);

    final PrinterJob printerJob = PrinterJob.getPrinterJob();

    final PsiDirectory[] psiDirectory = new PsiDirectory[1];
    PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
    if(psiElement instanceof PsiDirectory) {
      psiDirectory[0] = (PsiDirectory)psiElement;
    }

    final PsiFile psiFile = (PsiFile)dataContext.getData(DataConstants.PSI_FILE);
    final String[] shortFileName = new String[1];
    final String[] directoryName = new String[1];
    if(psiFile != null || psiDirectory[0] != null) {
      if(psiFile != null) {
        shortFileName[0] = psiFile.getVirtualFile().getName();
        if(psiDirectory[0] == null) {
          psiDirectory[0] = psiFile.getContainingDirectory();
        }
      }
      if(psiDirectory[0] != null) {
        directoryName[0] = psiDirectory[0].getVirtualFile().getPresentableUrl();
      }
    }

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    boolean isSelectedTextEnabled = false;
    if(editor != null && editor.getSelectionModel().hasSelection()) {
      isSelectedTextEnabled = true;
    }
    PrintDialog printDialog = new PrintDialog(shortFileName[0], directoryName[0], isSelectedTextEnabled, project);
    printDialog.reset();
    printDialog.show();
    if(!printDialog.isOK()) {
      return;
    }
    printDialog.apply();

    final PageFormat pageFormat = createPageFormat();
    PrintSettings printSettings = PrintSettings.getInstance();
    Printable painter;

    if(printSettings.getPrintScope() != PrintSettings.PRINT_DIRECTORY) {
      if(psiFile == null) {
        return;
      }
      TextPainter textPainter = initTextPainter(psiFile, project);
      if (textPainter == null) return;

      if(printSettings.getPrintScope() == PrintSettings.PRINT_SELECTED_TEXT && editor != null && editor.getSelectionModel().hasSelection()) {
        int firstLine = editor.getDocument().getLineNumber(editor.getSelectionModel().getSelectionStart());
        textPainter.setSegment(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd(), firstLine+1);
      }
      painter = textPainter;
    }
    else {
      ArrayList filesList = new ArrayList();
      boolean isRecursive = printSettings.isIncludeSubdirectories();
      addToPsiFileList(psiDirectory[0], filesList, isRecursive);

      painter = new MultiFilePainter(filesList, project);
    }
    final Printable painter0 = painter;
    Pageable document = new Pageable(){
      public int getNumberOfPages() {
        return Pageable.UNKNOWN_NUMBER_OF_PAGES;
      }

      public PageFormat getPageFormat(int pageIndex)
        throws IndexOutOfBoundsException {
        return pageFormat;
      }

      public Printable getPrintable(int pageIndex)
        throws IndexOutOfBoundsException {
        return painter0;
      }
    };

    printerJob.setPageable(document);
    printerJob.setPrintable(painter, pageFormat);

    try {
      if(!printerJob.printDialog()) {
        return;
      }
    } catch (Exception e) {
      // In case print dialog is not supported on some platform. Strange thing but there was a checking
      // for Windows only...
    }


    Runnable runnable = new Runnable() {
      public void run() {
        try {
          ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
          if (painter0 instanceof MultiFilePainter) {
            ((MultiFilePainter)painter0).setProgress(progress);
          }
          else {
            ((TextPainter)painter0).setProgress(progress);
          }

          printerJob.print();
        }
        catch(PrinterException e) {
          e.printStackTrace();
        }
        catch(ProcessCanceledException e) {
          printerJob.cancel();
        }
      }
    };
    
    ((ApplicationEx)ApplicationManager.getApplication()).runProcessWithProgressSynchronously(runnable, "Printing...", true, project, false);
  }

  private static void addToPsiFileList(PsiDirectory psiDirectory, ArrayList filesList, boolean isRecursive) {
    PsiFile[] files = psiDirectory.getFiles();
    for(int i = 0; i < files.length; i++) {
      Object obj = files[i];
      filesList.add(obj);
    }
    if(isRecursive) {
      PsiDirectory[] directories = psiDirectory.getSubdirectories();
      for(int i = 0; i < directories.length; i++) {
        addToPsiFileList(directories[i], filesList, isRecursive);
      }
    }
  }


  private static PageFormat createPageFormat() {
    PrintSettings printSettings = PrintSettings.getInstance();
    PageFormat pageFormat = new PageFormat();
    Paper paper = new Paper();
    String paperSize = printSettings.PAPER_SIZE;
    double paperWidth = PageSizes.getWidth(paperSize)*72;
    double paperHeight = PageSizes.getHeight(paperSize)*72;
    double leftMargin = printSettings.LEFT_MARGIN*72;
    double rightMargin = printSettings.RIGHT_MARGIN*72;
    double topMargin = printSettings.TOP_MARGIN*72;
    double bottomMargin = printSettings.BOTTOM_MARGIN*72;

    paper.setSize(paperWidth, paperHeight);
    if(printSettings.PORTRAIT_LAYOUT) {
      pageFormat.setOrientation(PageFormat.PORTRAIT);
      paperWidth -= leftMargin + rightMargin;
      paperHeight -= topMargin + bottomMargin;
      paper.setImageableArea(leftMargin, topMargin, paperWidth, paperHeight);
    }
    else{
      pageFormat.setOrientation(PageFormat.LANDSCAPE);
      paperWidth -= topMargin + bottomMargin;
      paperHeight -= leftMargin + rightMargin;
      paper.setImageableArea(topMargin, rightMargin, paperWidth, paperHeight);
    }
    pageFormat.setPaper(paper);
    return pageFormat;
  }

  public static TextPainter initTextPainter(final PsiFile psiFile, final Project project) {
    final TextPainter[] res = new TextPainter[1];
    ApplicationManager.getApplication().runReadAction(
        new Runnable() {
          public void run() {
            res[0] = doInitTextPainter(psiFile, project);
          }
        }
    );
    return res[0];
  }

  private static TextPainter doInitTextPainter(final PsiFile psiFile, Project project) {
    final String fileName = psiFile.getVirtualFile().getPresentableUrl();
    DocumentEx doc = (DocumentEx)PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) return null;
    Highlighter highlighter = HighlighterFactory.createHighlighter(project, psiFile.getVirtualFile());
    highlighter.setText(doc.getCharsSequence());
    return new TextPainter(doc, highlighter, fileName, psiFile, project);
  }
}