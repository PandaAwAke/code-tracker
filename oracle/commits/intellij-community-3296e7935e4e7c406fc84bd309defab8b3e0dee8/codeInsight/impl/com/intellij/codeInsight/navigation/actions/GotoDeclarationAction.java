package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ListPopup;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.*;
import java.util.List;

public class GotoDeclarationAction extends BaseCodeInsightAction implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.navigation.actions.GotoDeclarationAction");

  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = getOffset(editor);
    PsiElement element = findTargetElement(project, editor, offset);
    if (element == null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration");
      chooseAmbiguousTarget(project, editor, offset);
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.declaration");
    PsiElement navElement = element.getNavigationElement();

    //TODO: move this logic to ClsMethodImpl.getNavigationElement
    if (navElement == element && element instanceof PsiCompiledElement && element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.isConstructor() && method.getParameterList().getParameters().length == 0) {
        PsiClass aClass = method.getContainingClass();
        PsiElement navClass = aClass.getNavigationElement();
        if (aClass != navClass) navElement = navClass;
      }
    }

    if (navElement instanceof Navigatable && ((Navigatable)navElement).canNavigate()) {
        ((Navigatable)navElement).navigate(true);
    }
  }

  private static void chooseAmbiguousTarget(final Project project, final Editor editor, int offset) {
    final Collection<PsiElement> candidates = suggestCandidates(project, editor, offset);
    if (candidates.size() == 1) {
      Navigatable navigatable = EditSourceUtil.getDescriptor(candidates.iterator().next());
      if (navigatable != null && navigatable.canNavigate()) {
        navigatable.navigate(true);
      }
    }
    else if (candidates.size() > 1) {
      PsiElement[] elements = candidates.toArray(new PsiElement[candidates.size()]);
      String title = CodeInsightBundle.message("declaration.navigation.title", ((PsiNamedElement)elements[0]).getName());
      ListPopup listPopup = NavigationUtil.getPsiElementPopup(elements, title, project);
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      Point caretLocation = editor.logicalPositionToXY(caretPosition);
      int x = caretLocation.x;
      int y = caretLocation.y;
      Point location = editor.getContentComponent().getLocationOnScreen();
      x += location.x;
      y += location.y;
      listPopup.show(x, y);
    }
  }

  private static Collection<PsiElement> suggestCandidates(Project project, Editor editor, int offset) {
    PsiReference reference = TargetElementUtil.findReference(editor, offset);
    if (reference == null) {
      return Collections.emptyList();
    }
    return resolveElements(reference, project);
  }

  @NotNull private static Collection<PsiElement> resolveElements(final PsiReference reference, final Project project) {
    PsiElement parent = reference.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpr = (PsiMethodCallExpression) parent;
      boolean allowStatics = false;
      PsiExpression qualifier = callExpr.getMethodExpression().getQualifierExpression();
      if (qualifier == null) {
        allowStatics = true;
      } else if (qualifier instanceof PsiJavaCodeReferenceElement) {
        PsiElement referee = ((PsiJavaCodeReferenceElement) qualifier).advancedResolve(true).getElement();
        if (referee instanceof PsiClass) allowStatics = true;
      }
      PsiManager manager = PsiManager.getInstance(project);
      PsiResolveHelper helper = manager.getResolveHelper();
      PsiElement[] candidates = PsiUtil.mapElements(helper.getReferencedMethodCandidates(callExpr, false));
      ArrayList<PsiElement> methods = new ArrayList<PsiElement>();
      for (PsiElement candidate1 : candidates) {
        PsiMethod candidate = (PsiMethod)candidate1;
        if (candidate.hasModifierProperty(PsiModifier.STATIC) && !allowStatics) continue;
        List<PsiMethod> supers = Arrays.asList(candidate.findSuperMethods());
        if (supers.isEmpty()) {
          methods.add(candidate);
        }
        else {
          methods.addAll(supers);
        }
      }
      return methods;
    }
    if (reference instanceof PsiPolyVariantReference) {
      return Arrays.asList(PsiUtil.mapElements(((PsiPolyVariantReference)reference).multiResolve(false)));
    }
    PsiElement resolved = reference.resolve();
    if (resolved != null) {
      return Collections.singleton(resolved);
    }
    return Collections.emptyList();
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected static int getOffset(Editor editor) {
    return editor.getCaretModel().getOffset();
  }

  public static PsiElement findTargetElement(Project project, Editor editor, int offset) {
    int flags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                | TargetElementUtil.NEW_AS_CONSTRUCTOR
                | TargetElementUtil.LOOKUP_ITEM_ACCEPTED
                | TargetElementUtil.THIS_ACCEPTED
                | TargetElementUtil.SUPER_ACCEPTED;
    PsiElement element = TargetElementUtil.findTargetElement(editor, flags, offset);

    if (element != null) return element;

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      return null;
    }
    PsiElement elementAt = file.findElementAt(offset);
    if (elementAt instanceof PsiKeyword) {
      IElementType type = ((PsiKeyword)elementAt).getTokenType();
      if (type == JavaTokenType.CONTINUE_KEYWORD) {
        if (elementAt.getParent() instanceof PsiContinueStatement) {
          return ((PsiContinueStatement)elementAt.getParent()).findContinuedStatement();
        }
      }
      else if (type == JavaTokenType.BREAK_KEYWORD) {
        if (elementAt.getParent() instanceof PsiBreakStatement) {
          PsiStatement statement = ((PsiBreakStatement)elementAt.getParent()).findExitedStatement();
          if (statement == null) return null;
          if (statement.getParent() instanceof PsiLabeledStatement) {
            statement = (PsiStatement)statement.getParent();
          }
          PsiElement nextSibling = statement.getNextSibling();
          while (!(nextSibling instanceof PsiStatement) && nextSibling != null) nextSibling = nextSibling.getNextSibling();
          return nextSibling != null ? nextSibling : statement.getNextSibling();
        }
      }
    }
    else if (elementAt instanceof PsiIdentifier) {
      PsiElement parent = elementAt.getParent();
      PsiStatement statement = null;
      if (parent instanceof PsiContinueStatement) {
        statement = ((PsiContinueStatement)parent).findContinuedStatement();
      }
      else if (parent instanceof PsiBreakStatement) {
        statement = ((PsiBreakStatement)parent).findExitedStatement();
      }
      if (statement == null) return null;

      LOG.assertTrue(statement.getParent() instanceof PsiLabeledStatement);
      return ((PsiLabeledStatement)statement.getParent()).getLabelIdentifier();
    }

    return null;
  }
}
