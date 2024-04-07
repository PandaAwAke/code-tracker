/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 24, 2001
 * Time: 2:46:32 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.unusedParameters;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefParameter;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.codeInsight.daemon.GroupNames;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.text.MessageFormat;

public class UnusedParametersInspection extends FilteringInspectionTool {
  private UnusedParametersFilter myFilter;
  private UnusedParametersComposer myComposer;

  public UnusedParametersInspection() {

    myQuickFixActions = new QuickFixAction[] {new AcceptSuggested()};
  }

  private QuickFixAction[] myQuickFixActions;

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();

    // Do additional search of problem elements outside the scope.
    final Runnable action = new Runnable() {
      public void run() {
        if (getRefManager().getScope().getScopeType() != AnalysisScope.PROJECT) {
          ProgressManager.getInstance().runProcess(new Runnable() {
            public void run() {
              final UnusedParametersFilter filter = new UnusedParametersFilter();
              final PsiSearchHelper helper = PsiManager.getInstance(getManager().getProject()).getSearchHelper();

              getRefManager().iterate(new RefManager.RefIterator() {
                public void accept(RefElement refElement) {
                  if (filter.accepts(refElement)) {
                    RefMethod refMethod = (RefMethod) refElement;
                    PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
                    if (!refMethod.isStatic() && !refMethod.isConstructor() && refMethod.getAccessModifier() != PsiModifier.PRIVATE) {
                      PsiMethod[] derived = helper.findOverridingMethods(psiMethod, GlobalSearchScope.projectScope(getManager().getProject()), true);
                      ArrayList unusedParameters = filter.getUnusedParameters(refMethod);
                      for (Iterator paramIterator = unusedParameters.iterator(); paramIterator.hasNext();) {
                        final RefParameter refParameter = (RefParameter) paramIterator.next();
                        int idx = refParameter.getIndex();

                        if (refMethod.isAbstract() && derived.length == 0) {
                          refParameter.parameterReferenced(false);
                        } else {
                          final boolean[] found = new boolean[]{false};
                          for (int i = 0; i < derived.length && !found[0]; i++) {
                            if (!getRefManager().getScope().contains(derived[i])) {
                              PsiParameter psiParameter = derived[i].getParameterList().getParameters()[idx];
                              helper.processReferences(new PsiReferenceProcessor() {
                                public boolean execute(PsiReference element) {
                                  refParameter.parameterReferenced(false);
                                  found[0] = true;
                                  return false;
                                }
                              }, psiParameter, helper.getUseScope(psiParameter), false);
                            }
                          }
                        }
                      }
                    }
                  }
                }
              });
            }
          }, null);
        }
      }
    };
    ApplicationManager.getApplication().runReadAction(action);
  }

  public UnusedParametersFilter getFilter() {
    if (myFilter == null) {
      myFilter = new UnusedParametersFilter();
    }
    return myFilter;
  }

  protected void resetFilter() {
    myFilter = null;
  }

  public void exportResults(final Element parentNode) {
    final UnusedParametersFilter filter = new UnusedParametersFilter();
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (filter.accepts(refElement)) {
          ArrayList unusedParameters = filter.getUnusedParameters((RefMethod)refElement);
          for (int i = 0; i < unusedParameters.size(); i++) {
            Element element = XMLExportUtl.createElement(refElement, parentNode, -1);
            Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
            problemClassElement.addContent(InspectionsBundle.message("inspection.unused.parameter.export.results"));
            element.addContent(problemClassElement);

            RefParameter refParameter = (RefParameter) unusedParameters.get(i);
            Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
            descriptionElement.addContent(InspectionsBundle.message("inspection.unused.parameter.export.results.description", refParameter.getName()));
            element.addContent(descriptionElement);
          }
        }
      }
    });
  }

  public QuickFixAction[] getQuickFixes(final RefElement[] refElements) {
    return myQuickFixActions;
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  private class AcceptSuggested extends QuickFixAction {
    private AcceptSuggested() {
      super(InspectionsBundle.message("inspection.unused.parameter.delete.quickfix"),IconLoader.getIcon("/actions/cancel.png"), null, UnusedParametersInspection.this);
    }

    protected boolean applyFix(RefElement[] refElements) {
      for (int i = 0; i < refElements.length; i++) {
        RefMethod refMethod = (RefMethod) refElements[i];
        PsiMethod psiMethod = (PsiMethod) refMethod.getElement();

        if (psiMethod == null) continue;

        ArrayList psiParameters = new ArrayList();
        UnusedParametersFilter filter = (UnusedParametersFilter)getFilter();
        for (Iterator paramIterator = filter.getUnusedParameters(refMethod).iterator(); paramIterator.hasNext();) {
          RefParameter refParameter = (RefParameter) paramIterator.next();
          psiParameters.add(refParameter.getElement());
        }

        removeUnusedParameterViaChangeSignature(psiMethod, psiParameters);

        filter.ignore(refMethod);
      }

      return true;
    }
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.parameter.display.name");
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return "UnusedParameters";
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new UnusedParametersComposer(getFilter(), this);
    }
    return myComposer;
  }

  private void removeUnusedParameterViaChangeSignature(final PsiMethod psiMethod, final Collection parametersToDelete) {
    ArrayList newParameters = new ArrayList();
    PsiParameter[] oldParameters = psiMethod.getParameterList().getParameters();
    for (int i = 0; i < oldParameters.length; i++) {
      PsiParameter oldParameter = oldParameters[i];
      if (!parametersToDelete.contains(oldParameter)) {
        newParameters.add(new ParameterInfo(i, oldParameter.getName(), oldParameter.getType()));
      }
    }

    ParameterInfo[] parameterInfos = (ParameterInfo[]) newParameters.toArray(new ParameterInfo[newParameters.size()]);

    ChangeSignatureProcessor csp = new ChangeSignatureProcessor(getManager().getProject(),
                                                                psiMethod,
                                                                false, null, psiMethod.getName(),
                                                                psiMethod.getReturnType(),
                                                                parameterInfos);

    csp.run();
  }
}
