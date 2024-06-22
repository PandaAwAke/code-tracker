package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveMembers.MockMoveMembersOptions;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class MoveMembersTest extends MultiFileTestCase {
  public void testJavadocRefs() throws Exception {
    doTest("Class1", "Class2", 0);
  }

  public void testWeirdDeclaration() throws Exception {
    doTest("A", "B", 0);
  }

  public void testInnerClass() throws Exception {
    doTest("A", "B", 0);
  }

  public void testScr11871() throws Exception {
    doTest("pack1.A", "pack1.B", 0);
  }

  public void testOuterClassTypeParameters() throws Exception {
    doTest("pack1.A", "pack2.B", 0);
  }

  public void testscr40064() throws Exception {
    doTest("Test", "Test1", 0);
  }

  public void testscr40947() throws Exception {
    doTest("A", "Test", 0, 1);
  }

  public void testIDEADEV11416() throws Exception {
    doTest("Y", "X", 0);
  }

  public void testTwoMethods() throws Exception {
    doTest("pack1.A", "pack1.C", 0, 1, 2);
  }

  public void testIDEADEV12448() throws Exception {
    doTest("B", "A", 0);
  }

  public void testFieldForwardRef() throws Exception {
    doTest("A", "Constants", 0);
  }

  public void testStaticImport() throws Exception {
    doTest("C", "B", 0);
  }

  public void testOtherPackageImport() throws Exception {
    doTest("pack1.ClassWithStaticMethod", "pack2.OtherClass", 1);
  }

  protected String getTestRoot() {
    return "/refactoring/moveMembers/";
  }

  private void doTest(final String sourceClassName, final String targetClassName, final int... memberIndices)
    throws Exception {
    doTest(new PerformAction() {
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        MoveMembersTest.this.performAction(sourceClassName, targetClassName, memberIndices);
      }
    });
  }

  private void performAction(String sourceClassName, String targetClassName, int[] memberIndices) throws Exception {
    PsiClass sourceClass = myJavaFacade.findClass(sourceClassName, ProjectScope.getProjectScope(myProject));
    assertNotNull("Class " + sourceClassName + " not found", sourceClass);
    PsiClass targetClass = myJavaFacade.findClass(targetClassName, ProjectScope.getProjectScope(myProject));
    assertNotNull("Class " + targetClassName + " not found", targetClass);

    PsiElement[] children = sourceClass.getChildren();
    ArrayList<PsiMember> members = new ArrayList<PsiMember>();
    for (PsiElement child : children) {
      if (child instanceof PsiMember) {
        members.add(((PsiMember) child));
      }
    }

    LinkedHashSet<PsiMember> memberSet = new LinkedHashSet<PsiMember>();
    for (int index : memberIndices) {
      PsiMember member = members.get(index);
      assertTrue(member.hasModifierProperty(PsiModifier.STATIC));
      memberSet.add(member);
    }

    MockMoveMembersOptions options = new MockMoveMembersOptions(targetClass.getQualifiedName(), memberSet);
    options.setMemberVisibility(null);
    new MoveMembersProcessor(myProject, null, options).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
