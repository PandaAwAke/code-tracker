package com.intellij.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.rename.RenameProcessor;

/**
 * @author ven
 */
public class UIDesignerRelatedTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/renameUIRelated/";
  }

  protected void setupProject(VirtualFile rootDir) {
    myPsiManager.setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
    super.setupProject(rootDir);
  }

  public void testRenameBoundField() throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("UIClass", ProjectScope.getAllScope(myProject));
        assertNotNull(aClass);
        final PsiField field = aClass.findFieldByName("UIField", false);
        assertNotNull(field);

        new RenameProcessor(myProject, field, "OtherName", true, true).run();
      }
    });
  }

  public void testRenamePackage() throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiPackage aPackage = myPsiManager.findPackage("gov");
        assertNotNull(aPackage);


        new RenameProcessor(myProject, aPackage, "org", true, true).run();
      }
    });
  }

  public void testRenameEnumConstant() throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiClass aClass = myJavaFacade.findClass("PropEnum", ProjectScope.getAllScope(myProject));
        assertNotNull(aClass);
        PsiField enumConstant = aClass.findFieldByName("valueB", false);
        assertNotNull(enumConstant);

        new RenameProcessor(myProject, enumConstant, "newValueB", true, true).run();
      }
    });
  }

  public void testRenameResourceBundle() throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiFile file = myPsiManager.findFile(rootDir.findChild("F1.properties"));
        assertNotNull(file);


        new RenameProcessor(myProject, file, "F2.properties", true, true).run();
      }
    });
  }

  public void testRenameNestedForm() throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        PsiFile file = myPsiManager.findFile(rootDir.findChild("p1").findChild("Form1.form"));
        assertNotNull(file);


        new RenameProcessor(myProject, file, "Form2.form", true, true).run();
      }
    });
  }
}
