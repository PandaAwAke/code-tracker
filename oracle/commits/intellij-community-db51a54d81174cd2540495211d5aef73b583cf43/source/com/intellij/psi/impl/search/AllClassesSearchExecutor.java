/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

public class AllClassesSearchExecutor implements QueryExecutor<PsiClass, AllClassesSearch.SearchParameters> {
  public boolean execute(final AllClassesSearch.SearchParameters queryParameters, final Processor<PsiClass> consumer) {
    SearchScope scope = queryParameters.getScope();

    if (scope instanceof GlobalSearchScope) {
      return processAllClassesInGlobalScope((GlobalSearchScope)scope, consumer, queryParameters.getProject());
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)scope).getScope();
    for (final PsiElement scopeRoot : scopeRoots) {
      if (!processScopeRootForAllClasses(scopeRoot, consumer)) return false;
    }
    return true;
  }

  private static boolean processAllClassesInGlobalScope(final GlobalSearchScope searchScope, final Processor<PsiClass> processor, final Project project) {
    final PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(project);

    psiManager.getRepositoryManager().updateAll();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(psiManager.getProject()).getFileIndex();
    return fileIndex.iterateContent(new ContentIterator() {
      public boolean processFile(final VirtualFile fileOrDir) {
        return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          public Boolean compute() {
            if (!fileOrDir.isDirectory() && searchScope.contains(fileOrDir)) {
              final PsiFile psiFile = psiManager.findFile(fileOrDir);
              if (psiFile instanceof PsiJavaFile) {
                long fileId = psiManager.getRepositoryManager().getFileId(fileOrDir);
                if (fileId >= 0) {
                  long[] allClasses = psiManager.getRepositoryManager().getFileView().getAllClasses(fileId);
                  for (long allClass : allClasses) {
                    PsiClass psiClass = (PsiClass)psiManager.getRepositoryElementsManager().findOrCreatePsiElementById(allClass);
                    if (!processor.process(psiClass)) return false;
                  }
                }
                else {
                  if (!processScopeRootForAllClasses(psiFile, processor)) return false;
                }
              }
            }
            return true;
          }
        }).booleanValue();
      }
    });
  }

  private static boolean processScopeRootForAllClasses(PsiElement scopeRoot, final Processor<PsiClass> processor) {
    if (scopeRoot == null) return true;
    final boolean[] stopped = new boolean[]{false};

    scopeRoot.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!stopped[0]) {
          visitElement(expression);
        }
      }

      @Override public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.process(aClass);
        super.visitClass(aClass);
      }
    });

    return !stopped[0];
  }
}