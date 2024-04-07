package com.intellij.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.debugger.engine.evaluation.EvaluateException;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerInvocationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.DebuggerInvocationUtil");

  public static void invokeLater(final Project project, final Runnable runnable) {
    LOG.assertTrue(runnable != null);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if(project == null || project.isDisposed()) return;

        runnable.run();
      }
    });
  }

  public static void invokeLater(final Project project, final Runnable runnable, ModalityState state) {
    LOG.assertTrue(runnable != null);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if(project == null || project.isDisposed()) return;

        runnable.run();
      }
    }, state);
  }

  public static void invokeAndWait(final Project project, final Runnable runnable, ModalityState state) {
    LOG.assertTrue(runnable != null);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        if(project == null || project.isDisposed()) return;

        runnable.run();
      }
    }, state);
  }

  public static  <T> T commitAndRunReadAction(Project project, final com.intellij.debugger.EvaluatingComputable<T> computable) throws EvaluateException {
    final Throwable[] ex = new Throwable[] { null };
    T result = PsiDocumentManager.getInstance(project).commitAndRunReadAction(new Computable<T>() {
          public T compute() {
            try {
              return computable.compute();
            }
            catch (RuntimeException e) {
              ex[0] = e;
            }
            catch (Exception th) {
              ex[0] = th;
            }

            return null;
          }
        });

    if(ex[0] != null) {
      if(ex[0] instanceof RuntimeException) {
        throw (RuntimeException)ex[0];
      }
      else {
        throw (EvaluateException) ex[0];
      }
    }

    return result;
  }
}
