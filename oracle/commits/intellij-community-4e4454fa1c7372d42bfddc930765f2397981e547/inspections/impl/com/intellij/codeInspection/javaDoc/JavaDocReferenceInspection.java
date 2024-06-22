/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.javaDoc;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class JavaDocReferenceInspection extends BaseLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "JavadocReference";
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.javadoc.ref.display.name");


  private static ProblemDescriptor createDescriptor(@NotNull PsiElement element, String template, InspectionManager manager) {
    return manager.createProblemDescriptor(element, template, (LocalQuickFix [])null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
  }

  @Nullable
  public ProblemDescriptor[] checkMethod(PsiMethod psiMethod, InspectionManager manager, boolean isOnTheFly) {
    return checkMember(psiMethod, manager);
  }

  @Nullable
  public ProblemDescriptor[] checkField(PsiField field, InspectionManager manager, boolean isOnTheFly) {
    return checkMember(field, manager);
  }

  @Nullable
  private ProblemDescriptor[] checkMember(final PsiDocCommentOwner docCommentOwner, final InspectionManager manager) {
    ArrayList<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    final PsiDocComment docComment = docCommentOwner.getDocComment();
    if (docComment == null) return null;

    final Set<PsiJavaCodeReferenceElement> references = new HashSet<PsiJavaCodeReferenceElement>();
    docComment.accept(getVisitor(references, docCommentOwner, problems, manager));
    for (PsiJavaCodeReferenceElement reference : references) {
      problems.add(createDescriptor(reference, InspectionsBundle.message("inspection.javadoc.problem.cannot.resolve", "<code>" + reference.getText() + "</code>"), manager));
    }

    return problems.isEmpty()
           ? null
           : problems.toArray(new ProblemDescriptorImpl[problems.size()]);
  }

  @Nullable
  public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager manager, boolean isOnTheFly) {
    return checkMember(aClass, manager);
  }


  private PsiElementVisitor getVisitor(final Set<PsiJavaCodeReferenceElement> references,
                                       final PsiElement context,
                                       final ArrayList<ProblemDescriptor> problems,
                                       final InspectionManager manager) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        JavaResolveResult result = reference.advancedResolve(false);
        if (result.getElement() == null && !result.isPackagePrefixPackageReference()) {
          references.add(reference);
        }
      }

      public void visitDocTag(PsiDocTag tag) {
        super.visitDocTag(tag);
        final JavadocManager javadocManager = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager();
        final JavadocTagInfo info = javadocManager.getTagInfo(tag.getName());
        if (info == null || !info.isInline()) {
          visitRefInDocTag(tag, javadocManager, context, problems, manager);
        }
      }

      public void visitInlineDocTag(PsiInlineDocTag tag) {
        super.visitInlineDocTag(tag);
        final JavadocManager javadocManager = JavaPsiFacade.getInstance(tag.getProject()).getJavadocManager();
        visitRefInDocTag(tag, javadocManager, context, problems, manager);
      }

      public void visitElement(PsiElement element) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          //do not visit method javadoc twice
          if (!(child instanceof PsiDocCommentOwner)) {
            child.accept(this);
          }
        }
      }
    };
  }

  public static void visitRefInDocTag(final PsiDocTag tag,
                                  final JavadocManager manager,
                                  final PsiElement context,
                                  ArrayList<ProblemDescriptor> problems,
                                  InspectionManager inspectionManager) {
    String tagName = tag.getName();
    PsiDocTagValue value = tag.getValueElement();
    if (value == null) return;
    final JavadocTagInfo info = manager.getTagInfo(tagName);
    if (info != null && !info.isValidInContext(context)) return;
    String message = info == null || !info.isInline() ? null : info.checkTagValue(value);
    if (message != null){
      problems.add(createDescriptor(value, message, inspectionManager));
    }
    final PsiReference reference = value.getReference();
    if (reference != null) {
      PsiElement element = reference.resolve();
      if (element == null) {
        final int textOffset = value.getTextOffset();

        if (textOffset != value.getTextRange().getEndOffset()) {
          final PsiDocTagValue valueElement = tag.getValueElement();
          if (valueElement != null) {
            @NonNls String params = "<code>" + value.getContainingFile().getViewProvider().getContents().subSequence(textOffset, value.getTextRange().getEndOffset()) + "</code>";
            problems.add(createDescriptor(valueElement, InspectionsBundle.message("inspection.javadoc.problem.cannot.resolve", params), inspectionManager));
          }
        }
      }
    }
  }


  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
