package com.intellij.execution.junit2.states;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

public class StackTraceLine {
  private Project myProject;
  private String myLine;

  public StackTraceLine(Project project, final String line) {
    myProject = project;
    myLine = line;
  }

  public String getClassName() {
    int index = myLine.indexOf("at");
    if (index < 0) return null;
    index += "at ".length();
    final int lastDot = getLastDot();
    if (lastDot < 0) return null;
    if (lastDot <= index) return null;
    return myLine.substring(index, lastDot);
  }

  private int getLastDot() {
    return myLine.lastIndexOf('.', getOpenBracket());
  }

  private int getOpenBracket() {
    return myLine.indexOf('(');
  }

  private int getCloseBracket() {
    return myLine.indexOf(')');
  }

  public int getLineNumber() throws NumberFormatException {
    final int close = getCloseBracket();
    final int lineNumberStart = myLine.lastIndexOf(':') + 1;
    if (close < 0 || lineNumberStart < 1) throw new NumberFormatException(myLine);
    return Integer.parseInt(myLine.substring(lineNumberStart, close)) - 1;
  }

  public OpenFileDescriptor getOpenFileDescriptor(final VirtualFile file) {
    final int lineNumber;
    try {
      lineNumber = getLineNumber();
    } catch(NumberFormatException e) {
      return new OpenFileDescriptor(myProject, file);
    }
    return new OpenFileDescriptor(myProject, file, lineNumber, 0);
  }

  public OpenFileDescriptor getOpenFileDescriptor(final Project project) {
    final Location<PsiMethod> location = getMethodLocation(project);
    if (location == null) return null;
    return getOpenFileDescriptor(location.getPsiElement().getContainingFile().getVirtualFile());
  }

  public String getMethodName() {
    final int lastDot = getLastDot();
    if (lastDot == -1) return null;
    return myLine.substring(getLastDot() + 1, getOpenBracket());
  }

  public Location<PsiMethod> getMethodLocation(final Project project) {
    String className = getClassName();
    final String methodName = getMethodName();
    if (className == null || methodName == null) return null;
    final int lineNumber;
    try {
      lineNumber = getLineNumber();
    } catch(NumberFormatException e) {
      return null;
    }
    final int dollarIndex = className.indexOf('$');
    if (dollarIndex != -1) className = className.substring(0, dollarIndex);
    PsiClass psiClass = findClass(project, className, lineNumber);
    if (psiClass == null || (psiClass.getNavigationElement() instanceof PsiCompiledElement)) return null;
    psiClass = (PsiClass)psiClass.getNavigationElement();
    final PsiMethod psiMethod = getMethodAtLine(psiClass, methodName, lineNumber);
    if (psiMethod != null) {
      return new MethodLineLocation(project, psiMethod, PsiLocation.fromPsiElement(psiClass), lineNumber);
    }
    else {
      return null;
    }
  }

  private PsiClass findClass(final Project project, final String className, final int lineNumber) {
    if (project == null) return null;
    final PsiManager psiManager = PsiManager.getInstance(project);
    if (psiManager == null) return null;
    PsiClass psiClass = psiManager.findClass(className, GlobalSearchScope.allScope(project));
    if (psiClass == null || (psiClass.getNavigationElement() instanceof PsiCompiledElement)) return null;
    psiClass = (PsiClass)psiClass.getNavigationElement();
    final PsiFile psiFile = psiClass.getContainingFile();
    return PsiTreeUtil.getParentOfType(psiFile.findElementAt(offsetOfLine(psiFile, lineNumber)), PsiClass.class, false);
  }

  private static PsiMethod getMethodAtLine(final PsiClass psiClass, final String methodName, final int lineNumber) {
    final PsiMethod[] methods;
    if ("<init>".equals(methodName)) methods = psiClass.getConstructors();
    else methods = psiClass.findMethodsByName(methodName, true);
    if (methods.length == 0) return null;
    final PsiFile psiFile = methods[0].getContainingFile();
    final int offset = offsetOfLine(psiFile, lineNumber);
    for (int i = 0; i < methods.length; i++) {
      final PsiMethod method = methods[i];
      if (method.getTextRange().contains(offset)) return method;
    }
    //if (!methods.hasNext() || location == null) return null;
    //return location.getPsiElement();

    //if ("<init>".equals(methodName)) methods = psiClass.getConstructors();
    //else methods = psiClass.findMethodsByName(methodName, true);
    //if (methods.length == 0) return null;
    //for (int i = 0; i < methods.length; i++) {
    //  PsiMethod method = methods[i];
    //  if (method.getTextRange().contains(offset)) return method;
    //}
    return null;
  }

  private static int offsetOfLine(final PsiFile psiFile, final int lineNumber) {
    final LineTokenizer lineTokenizer = new LineTokenizer(psiFile.textToCharArray(), 0, psiFile.getTextLength());
    for (int i = 0; i < lineNumber; i++) lineTokenizer.advance();
    final int offset = lineTokenizer.getOffset();
    return offset;
  }
}
