/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 13:36:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class IntroduceParameterHandler extends IntroduceHandlerBase implements RefactoringActionHandler, IntroduceParameterDialog.Callback {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.IntroduceParameterHandler");
  private static final String REFACTORING_NAME = "Introduce Parameter";
  private PsiExpression myParameterInitializer;
  private PsiExpression myExpressionToSearchFor;
  private PsiLocalVariable myLocalVar;
  private PsiMethod myMethod;
  private PsiMethod myMethodToSearchFor;
  private Project myProject;

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ElementToWorkOn elementToWorkOn =
            ElementToWorkOn.getElementToWorkOn(editor, file, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER, project);
    if(elementToWorkOn == null) return;

    final PsiExpression expr = elementToWorkOn.getExpression();
    final PsiLocalVariable localVar = elementToWorkOn.getLocalVariable();
    final boolean isInvokedOnDeclaration = elementToWorkOn.isInvokedOnDeclaration();

    if (invoke(editor, project, expr, localVar, isInvokedOnDeclaration)) {
      editor.getSelectionModel().removeSelection();
    }
  }

  protected boolean invokeImpl(Project project, PsiExpression tempExpr, Editor editor) {
    return invoke(editor, project, tempExpr, null, false);
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    return invoke(editor, project, null, localVariable, true);
  }

  private boolean invoke(Editor editor, Project project, final PsiExpression expr,
                         PsiLocalVariable localVar, boolean invokedOnDeclaration) {

    final PsiMethod method;
    if (expr != null) {
      method = Util.getContainingMethod(expr);
    } else {
      method = Util.getContainingMethod(localVar);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    myLocalVar = localVar;
    myProject = project;
    if (expr == null && myLocalVar == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Selected block should represent an expression.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, myProject);
      return false;
    }


    myMethod = method;
    if (myMethod == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              REFACTORING_NAME + " is not supported in the current context.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, myProject);
      return false;
    }

    if (!myMethod.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(myProject, myMethod);
      return false;
    }

    final PsiType typeByExpression = !invokedOnDeclaration ? RefactoringUtil.getTypeByExpressionWithExpectedType(expr) : null;
    if (!invokedOnDeclaration && typeByExpression == null) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Type of the selected expression cannot be determined.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, myProject);
      return false;
    }

    if (!invokedOnDeclaration && typeByExpression == PsiType.VOID) {
      String message =
              "Cannot perform the refactoring.\n" +
              "Selected expression has void type.";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_PARAMETER, project);
      return false;
    }

    final List<PsiMethod> validEnclosingMethods = getEnclosingMethods(myMethod);
    if (validEnclosingMethods.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final EnclosingMethodSelectionDialog dialog = new EnclosingMethodSelectionDialog(project, validEnclosingMethods);
      dialog.show();
      if (!dialog.isOK()) return false;
      myMethod = dialog.getSelectedMethod();
    }

    myMethodToSearchFor = SuperMethodWarningUtil.checkSuperMethod(myMethod, "refactor");

    if (myMethodToSearchFor == null) {
      return false;
    }
    if (!myMethodToSearchFor.isWritable()) {
      RefactoringMessageUtil.showReadOnlyElementRefactoringMessage(myProject, myMethodToSearchFor);
      return false;
    }

    PsiExpression[] occurences;
    if (expr != null) {
      occurences = CodeInsightUtil.findExpressionOccurrences(myMethod, expr);
    } else { // local variable
      occurences = CodeInsightUtil.findReferenceExpressions(myMethod, myLocalVar);
    }
    if (editor != null) {
      RefactoringUtil.highlightOccurences(myProject, occurences, editor);
    }

    ArrayList localVars = new ArrayList();
    ArrayList classMemberRefs = new ArrayList();
    ArrayList params = new ArrayList();


    if (expr != null) {
      Util.analyzeExpression(expr, localVars, classMemberRefs, params);
    }

    boolean previewUsages = false;
    String parameterName = "anObject";
    boolean replaceAllOccurences = true;
    boolean isDeleteLocalVariable = true;
    myParameterInitializer = expr;
    myExpressionToSearchFor = expr;

    if (expr instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
      if (resolved instanceof PsiLocalVariable) {
        myLocalVar = (PsiLocalVariable) resolved;
      }
    }


    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final String propName = myLocalVar != null ? CodeStyleManager.getInstance(myProject).variableNameToPropertyName(myLocalVar.getName(), VariableKind.LOCAL_VARIABLE) : null;
      final PsiType initializerType = IntroduceParameterProcessor.getInitializerType(null, expr, myLocalVar);

      TypeSelectorManager typeSelectorManager =
              (expr != null ?
              new TypeSelectorManagerImpl(project, initializerType, expr, occurences) :
              new TypeSelectorManagerImpl(project, initializerType, occurences));

      final IntroduceParameterDialog dialog =
              new IntroduceParameterDialog(
                      myProject, localVars, params, classMemberRefs,
                      occurences.length,
                      expr == null, myLocalVar != null,
                      (myLocalVar != null) && (myLocalVar.getInitializer() != null),
                      this,
                      new NameSuggestionsGenerator() {
                        public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
                          return CodeStyleManager.getInstance(myProject).suggestVariableName(VariableKind.PARAMETER, propName, expr, initializerType);
                        }

                        public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix,
                                                                                                      PsiType type) {
                          LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
                          LookupItemPreferencePolicy policy = CompletionUtil.completeVariableName(myProject, set, prefix, type, VariableKind.PARAMETER);
                          return new Pair<LookupItemPreferencePolicy, Set<LookupItem>> (policy, set);
                        }
                      },
                      typeSelectorManager);
      dialog.show();

      if (!dialog.isOK()) {
        return true;
      }

      return true;
    }

    new IntroduceParameterProcessor(
            myProject, myMethod, myMethodToSearchFor,
            myParameterInitializer, myExpressionToSearchFor,
            myLocalVar, isDeleteLocalVariable,
            parameterName, previewUsages, replaceAllOccurences,
            IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, false, null, null).run(null);
    return true;
  }


  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
    // Never called
    /* do nothing */
  }

  public void run(final IntroduceParameterDialog dialog) {
    boolean isDeleteLocalVariable = false;

    if (myLocalVar != null) {
      if (dialog.isUseInitializer()) {
        PsiExpression varInitializer = myLocalVar.getInitializer();
        if (varInitializer != null) {
          myParameterInitializer = varInitializer;
        }
      }
      isDeleteLocalVariable = dialog.isDeleteLocalVariable();
    }

    new IntroduceParameterProcessor(
            myProject, myMethod, myMethodToSearchFor,
            myParameterInitializer, myExpressionToSearchFor,
            myLocalVar, isDeleteLocalVariable,
            dialog.getParameterName(), dialog.isPreviewUsages(), dialog.isReplaceAllOccurences(),
            dialog.getReplaceFieldsWithGetters(), dialog.isDeclareFinal(),
            dialog.getSelectedType(), new Runnable() {
              public void run() {
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
              }
            }).run(null);
  }


  private static List<PsiMethod> getEnclosingMethods(PsiMethod nearest) {
    List<PsiMethod> enclosingMethods = new ArrayList<PsiMethod>();
    enclosingMethods.add(nearest);
    PsiMethod method = nearest;
    while(true) {
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
      if (method == null) break;
      enclosingMethods.add(method);
    }
    return enclosingMethods;
  }
}
