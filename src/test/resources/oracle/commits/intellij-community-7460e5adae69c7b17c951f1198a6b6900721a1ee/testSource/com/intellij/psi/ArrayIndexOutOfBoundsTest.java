package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class ArrayIndexOutOfBoundsTest extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.ArrayIndexOutOfBoundsTest");
  private VirtualFile myProjectRoot;

  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = PathManagerEx.getTestDataPath() + "/psi/arrayIndexOutOfBounds/src";
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
            myProjectRoot = PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testSCR10930() throws Exception {
    renamePackage();
    deleteNewPackage();
    restoreSources();
    renamePackage();
  }

  public void testSimplerCase() throws Exception {
    renamePackage();
    restoreSources();

    PsiFile psiFile = myPsiManager.findFile(myProjectRoot.findFileByRelativePath("bla/Bla.java"));
    assertNotNull(psiFile);

    assertEquals(4, psiFile.getChildren().length);
  }

  public void testLongLivingClassAfterRename() throws Exception {
    PsiClass psiClass = myPsiManager.findClass("bla.Bla");
    TreeElement treeElement = SourceTreeToPsiMap.psiElementToTree(psiClass);
    renamePackage();
    //assertTrue(psiClass.isValid());
    SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  private void restoreSources() {
    Runnable runnable = new Runnable() {
      public void run() {
        try {
          FileUtil.copyDir(new File(PathManagerEx.getTestDataPath() + "/psi/arrayIndexOutOfBounds/src"),
                           VfsUtil.virtualToIoFile(myProjectRoot));
        }
        catch (IOException e) {
          LOG.error(e);
        }
        VirtualFileManager.getInstance().refresh(false);
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable,  "", null);
  }

  private void deleteNewPackage() {
    Runnable runnable = new Runnable() {
      public void run() {
        PsiPackage aPackage = myPsiManager.findPackage("anotherBla");
        assertNotNull("Package anotherBla not found", aPackage);
        try {
          aPackage.getDirectories()[0].delete();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        VirtualFileManager.getInstance().refresh(false);
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable,  "", null);
  }

  private void renamePackage() {
    Runnable runnable = new Runnable() {
      public void run() {
        PsiPackage aPackage = myPsiManager.findPackage("bla");
        assertNotNull("Package bla not found", aPackage);

        PsiDirectory dir = aPackage.getDirectories()[0];
        new RenameProcessor(myProject, dir, "anotherBla", true, true, false).testRun();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    };
    CommandProcessor.getInstance().executeCommand(myProject, runnable,  "", null);
  }
}
