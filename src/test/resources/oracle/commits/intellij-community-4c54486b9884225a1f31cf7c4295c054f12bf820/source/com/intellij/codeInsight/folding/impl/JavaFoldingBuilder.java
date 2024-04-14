package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.lang.xml.XmlFoldingBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class JavaFoldingBuilder implements FoldingBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.JavaFoldingBuilder");
  private static final String[] SMILEYS = {"<:->", "<:-)>", "<:P>", "<:-P>", "<~>", "</>", "<->"};
  private static final String SMILEY = SMILEYS[new Random().nextInt(SMILEYS.length)];

  public FoldingDescriptor[] buildFoldRegions(final ASTNode node, final Document document) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (!(element instanceof PsiJavaFile)) {
      return FoldingDescriptor.EMPTY;
    }
    PsiJavaFile file = (PsiJavaFile) element;

    List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    PsiImportList importList = file.getImportList();
    if (importList != null) {
      PsiImportStatementBase[] statements = importList.getAllImportStatements();
      if (statements.length > 1) {
        final TextRange rangeToFold = getRangeToFold(importList);
        if (rangeToFold != null) {
          result.add(new FoldingDescriptor(importList.getNode(), rangeToFold));
        }
      }
    }

    PsiClass[] classes = file.getClasses();
    for (PsiClass aClass : classes) {
      ProgressManager.getInstance().checkCanceled();
      addElementsToFold(result, aClass, document, true);
    }

    TextRange range = getFileHeader(file);
    if (range != null && document.getLineNumber(range.getEndOffset()) > document.getLineNumber(range.getStartOffset())) {
      result.add(new FoldingDescriptor(file.getNode(), range));
    }

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  private void addElementsToFold(List<FoldingDescriptor> list, PsiClass aClass, Document document, boolean foldJavaDocs) {
    if (!(aClass.getParent() instanceof PsiJavaFile) || ((PsiJavaFile)aClass.getParent()).getClasses().length > 1) {
      addToFold(list, aClass, document, true);
    }

    PsiDocComment docComment;
    if (foldJavaDocs) {
      docComment = aClass.getDocComment();
      if (docComment != null) {
        addToFold(list, docComment, document, true);
      }
    }
    addAnnotationsToFold(aClass.getModifierList(), list, document);

    PsiElement[] children = aClass.getChildren();
    for (PsiElement child : children) {
      ProgressManager.getInstance().checkCanceled();

      if (child instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)child;
        addToFold(list, method, document, true);
        addAnnotationsToFold(method.getModifierList(), list, document);

        if (foldJavaDocs) {
          docComment = method.getDocComment();
          if (docComment != null) {
            addToFold(list, docComment, document, true);
          }
        }

        PsiCodeBlock body = method.getBody();
        if (body != null) {
          addCodeBlockFolds(body, list, document);
        }
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField)child;
        if (foldJavaDocs) {
          docComment = field.getDocComment();
          if (docComment != null) {
            addToFold(list, docComment, document, true);
          }
        }
        addAnnotationsToFold(field.getModifierList(), list, document);
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          addCodeBlockFolds(initializer, list, document);
        } else if (field instanceof PsiEnumConstant) {
          addCodeBlockFolds(field, list, document);
        }
      }
      else if (child instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer)child;
        addToFold(list, initializer, document, true);
        addCodeBlockFolds(initializer, list, document);
      }
      else if (child instanceof PsiClass) {
        addElementsToFold(list, (PsiClass)child, document, true);
      }
    }
  }

  public String getPlaceholderText(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    if (element instanceof PsiImportList) {
      return "...";
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiClass) {
      return "{...}";
    }
    else if (element instanceof PsiDocComment) {
      return "/**...*/";
    }
    else if (element instanceof PsiFile) {
      return "/.../";
    }
    else if (element instanceof PsiAnnotation) {
      return "@{...}";
    }
    if (element instanceof PsiReferenceParameterList) {
      return SMILEY;
    }
    return "...";
  }

  public boolean isCollapsedByDefault(final ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();
    if (element instanceof PsiNewExpression || element instanceof PsiJavaToken) {
      return settings.isCollapseLambdas();
    }
    if (element instanceof PsiReferenceParameterList) {
      return settings.isCollapseConstructorGenericParameters();
    }

    if (element instanceof PsiImportList) {
      return settings.isCollapseImports();
    }
    else if (element instanceof PsiMethod || element instanceof PsiClassInitializer || element instanceof PsiCodeBlock) {
      if (!settings.isCollapseAccessors() && !settings.isCollapseMethods()) {
        return false;
      }
      if (element instanceof PsiMethod && isSimplePropertyAccessor((PsiMethod)element)) {
        return settings.isCollapseAccessors();
      }
      return settings.isCollapseMethods();
    }
    else if (element instanceof PsiAnonymousClass) {
      return settings.isCollapseAnonymousClasses();
    }
    else if (element instanceof PsiClass) {
      return !(element.getParent() instanceof PsiFile) && settings.isCollapseInnerClasses();
    }
    else if (element instanceof PsiDocComment) {
      return settings.isCollapseJavadocs();
    }
    else if (element instanceof PsiJavaFile) {
      return settings.isCollapseFileHeader();
    }
    else if (element instanceof PsiAnnotation) {
      return settings.isCollapseAnnotations();
    }
    else {
      LOG.error("Unknown element:" + element);
      return false;
    }
  }

  private static boolean isSimplePropertyAccessor(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) return false;
    PsiStatement statement = statements[0];
    if (PropertyUtil.isSimplePropertyGetter(method)) {
      if (statement instanceof PsiReturnStatement) {
        PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
        if (returnValue instanceof PsiReferenceExpression) {
          return ((PsiReferenceExpression)returnValue).resolve() instanceof PsiField;
        }
      }
    }
    else if (PropertyUtil.isSimplePropertySetter(method)) {
      if (statement instanceof PsiExpressionStatement) {
        PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
        if (expr instanceof PsiAssignmentExpression) {
          PsiExpression lhs = ((PsiAssignmentExpression)expr).getLExpression();
          PsiExpression rhs = ((PsiAssignmentExpression)expr).getRExpression();
          if (lhs instanceof PsiReferenceExpression && rhs instanceof PsiReferenceExpression) {
            return ((PsiReferenceExpression)lhs).resolve() instanceof PsiField &&
                   ((PsiReferenceExpression)rhs).resolve() instanceof PsiParameter;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  public static TextRange getRangeToFold(PsiElement element) {
    if (element instanceof PsiMethod) {
      if (element instanceof JspHolderMethod) return null;
      PsiCodeBlock body = ((PsiMethod)element).getBody();
      if (body == null) return null;
      return body.getTextRange();
    }
    if (element instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)element).getBody().getTextRange();
    }
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass)element;
      PsiJavaToken lBrace = aClass.getLBrace();
      if (lBrace == null) return null;
      PsiJavaToken rBrace = aClass.getRBrace();
      if (rBrace == null) return null;
      return new TextRange(lBrace.getTextOffset(), rBrace.getTextOffset() + 1);
    }
    if (element instanceof PsiJavaFile) {
      return getFileHeader((PsiJavaFile)element);
    }
    if (element instanceof PsiImportList) {
      PsiImportList list = (PsiImportList)element;
      PsiImportStatementBase[] statements = list.getAllImportStatements();
      if (statements.length == 0) return null;
      final PsiElement importKeyword = statements[0].getFirstChild();
      if (importKeyword == null) return null;
      int startOffset = importKeyword.getTextRange().getEndOffset() + 1;
      int endOffset = statements[statements.length - 1].getTextRange().getEndOffset();
      return new TextRange(startOffset, endOffset);
    }
    if (element instanceof PsiDocComment) {
      return element.getTextRange();
    }
    if (element instanceof XmlTag) {
      final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(element.getLanguage());

      if (foldingBuilder instanceof XmlFoldingBuilder) {
        return ((XmlFoldingBuilder)foldingBuilder).getRangeToFold(element);
      }
    }
    else if (element instanceof PsiAnnotation) {
      int startOffset = element.getTextRange().getStartOffset();
      PsiElement last = element;
      while (element instanceof PsiAnnotation) {
        last = element;
        element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiComment.class);
      }

      return new TextRange(startOffset, last.getTextRange().getEndOffset());
    }


    return null;
  }

  @Nullable
  private static TextRange getFileHeader(PsiJavaFile file) {
    PsiElement first = file.getFirstChild();
    if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
    PsiElement element = first;
    while (element instanceof PsiComment) {
      element = element.getNextSibling();
      if (element instanceof PsiWhiteSpace) {
        element = element.getNextSibling();
      }
      else {
        break;
      }
    }
    if (element == null) return null;
    if (element.getPrevSibling() instanceof PsiWhiteSpace) element = element.getPrevSibling();
    if (element == null || element.equals(first)) return null;
    return new TextRange(first.getTextOffset(), element.getTextOffset());
  }

  private void addAnnotationsToFold(PsiModifierList modifierList, List<FoldingDescriptor> foldElements, Document document) {
    if (modifierList == null) return;
    PsiElement[] children = modifierList.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child instanceof PsiAnnotation) {
        addToFold(foldElements, child, document, false);
        int j;
        for (j = i + 1; j < children.length; j++) {
          PsiElement nextChild = children[j];
          if (nextChild instanceof PsiModifier) break;
        }

        //noinspection AssignmentToForLoopParameter
        i = j;
      }
    }
  }

  private void addCodeBlockFolds(PsiElement scope, final List<FoldingDescriptor> foldElements, final Document document) {
    scope.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitClass(PsiClass aClass) {
        if (!addClosureFolding(aClass, document, foldElements)) {
          addToFold(foldElements, aClass, document, true);
          addElementsToFold(foldElements, aClass, document, false);
        }
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        addGenericParametersFolding(expression, foldElements, document);

        super.visitNewExpression(expression);
      }
    });
  }

  private void addGenericParametersFolding(PsiNewExpression expression, List<FoldingDescriptor> foldElements, Document document) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiVariable)) {
      return;
    }

    final PsiType declType = ((PsiVariable)parent).getType();
    if (!(declType instanceof PsiClassType)) {
      return;
    }

    final PsiType[] parameters = ((PsiClassType)declType).getParameters();
    if (parameters.length == 0) {
      return;
    }



    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    if (classReference == null) {
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        classReference = anonymousClass.getBaseClassReference();

        if (seemsLikeLambda(anonymousClass.getSuperClass())) {
          return;
        }
      }
    }

    if (classReference != null) {
      final PsiReferenceParameterList list = classReference.getParameterList();
      if (list != null) {
        if (!Arrays.equals(list.getTypeArguments(), parameters)) {
          return;
        }

        final String text = list.getText();
        if (text.startsWith("<") && text.endsWith(">") && text.length() > 5) { //5 seems reasonable
          final TextRange range = list.getTextRange();
          addFoldRegion(foldElements, list, document, true, new TextRange(range.getStartOffset(), range.getEndOffset()));
        }
      }
    }
  }

  private boolean addClosureFolding(final PsiClass aClass, final Document document, final List<FoldingDescriptor> foldElements) {
    if (!JavaCodeFoldingSettings.getInstance().isCollapseLambdas()) {
      return false;
    }

    boolean isClosure = false;
    if (aClass instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
      final PsiElement element = anonymousClass.getParent();
      if (element instanceof PsiNewExpression) {
        final PsiNewExpression expression = (PsiNewExpression)element;
        final PsiExpressionList argumentList = expression.getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 0) {
          final PsiClass baseClass = anonymousClass.getBaseClassType().resolve();
          final PsiMethod[] methods = anonymousClass.getMethods();
          if (baseClass != null && methods.length == 1 && anonymousClass.getFields().length == 0 && seemsLikeLambda(baseClass)) {
            final PsiMethod method = methods[0];
            final PsiCodeBlock body = method.getBody();
            if (body != null) {
              isClosure = true;
              int rangeStart = body.getTextRange().getStartOffset();
              int rangeEnd = body.getTextRange().getEndOffset();
              final PsiJavaToken lbrace = body.getLBrace();
              if (lbrace != null) rangeStart = lbrace.getTextRange().getEndOffset();
              final PsiJavaToken rbrace = body.getRBrace();
              if (rbrace != null) rangeEnd = rbrace.getTextRange().getStartOffset();

              final CharSequence seq = document.getCharsSequence();
              final PsiJavaToken classRBrace = anonymousClass.getRBrace();
              if (classRBrace != null && rbrace != null) {
                final int methodEndLine = document.getLineNumber(rangeEnd);
                final int methodEndLineStart = document.getLineStartOffset(methodEndLine);
                if ("}".equals(seq.subSequence(methodEndLineStart, document.getLineEndOffset(methodEndLine)).toString().trim())) {
                  int classEndStart = classRBrace.getTextRange().getStartOffset();
                  int classEndCol = classEndStart - document.getLineStartOffset(document.getLineNumber(classEndStart));
                  rangeEnd = classEndCol + methodEndLineStart;
                }
              }

              final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(aClass.getProject());
              int firstLineStart = CharArrayUtil.shiftForward(seq, rangeStart, " \t");
              if (firstLineStart < seq.length() - 1 && seq.charAt(firstLineStart) == '\n') firstLineStart++;

              int lastLineEnd = CharArrayUtil.shiftBackward(seq, rangeEnd - 1, " \t");
              if (lastLineEnd > 0 && seq.charAt(lastLineEnd) == '\n') lastLineEnd--;
              if (lastLineEnd < firstLineStart) return false;

              if (lastLineEnd >= seq.length() || firstLineStart >= seq.length() || firstLineStart < 0) {
                LOG.assertTrue(false, "llE=" +
                                      lastLineEnd +
                                      "; fLS=" +
                                      firstLineStart +
                                      "; len=" +
                                      seq.length() +
                                      "rE=" +
                                      rangeEnd +
                                      "; class=" +
                                      baseClass.getName());
              }

              boolean oneLine = false;
              String contents = seq.subSequence(firstLineStart, lastLineEnd).toString();
              if (contents.indexOf('\n') < 0) {
                final int beforeLength = rangeStart - document.getLineStartOffset(document.getLineNumber(rangeStart));
                final int afterLength = document.getLineEndOffset(document.getLineNumber(rangeEnd)) - rangeEnd;
                final int resultLineLength = beforeLength + contents.length() + afterLength;

                if (resultLineLength <= settings.RIGHT_MARGIN) {
                  rangeStart = CharArrayUtil.shiftForward(seq, rangeStart, " \n\t");
                  rangeEnd = CharArrayUtil.shiftBackward(seq, rangeEnd - 1, " \n\t") + 1;
                  oneLine = true;
                }
              }
              else {
                //foldElements.add(new FoldingDescriptor(body.getNode(), new TextRange(rangeStart, rangeEnd))); //...
              }

              if (rangeStart >= rangeEnd) return false;

              FoldingGroup group = FoldingGroup.newGroup("lambda");
              final String params = StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
                public String fun(final PsiParameter psiParameter) {
                  return psiParameter.getType().getPresentableText() + " " + psiParameter.getName();
                }
              }, ", ");
              final String prettySpace = oneLine ? " " : "";
              @NonNls final String lambdas = baseClass.getName() + "(" + params + ") {" + prettySpace;
              foldElements.add(
                new FoldingDescriptor(expression.getNode(), new TextRange(expression.getTextRange().getStartOffset(), rangeStart), group) {
                  @Override
                  public String getPlaceholderText() {
                    return lambdas;
                  }
                });

              if (rbrace != null) {
                foldElements
                  .add(new FoldingDescriptor(rbrace.getNode(), new TextRange(rangeEnd, expression.getTextRange().getEndOffset()), group) {
                    @Override
                    public String getPlaceholderText() {
                      return prettySpace + "}";
                    }
                  });
              }
              addCodeBlockFolds(body, foldElements, document);
            }
          }
        }
      }
    }
    return isClosure;
  }

  private static boolean seemsLikeLambda(@Nullable final PsiClass baseClass) {
    if (baseClass == null) return false;

    if (!baseClass.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    for (final PsiMethod method : baseClass.getConstructors()) {
      if (method.getParameterList().getParametersCount() > 0) return false;
    }

    boolean hasAbstract = false;
    for (final PsiMethod method : baseClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        hasAbstract = true;
        break;
      }
    }

    if (!hasAbstract) return false;

    return true;
  }

  private boolean addToFold(List<FoldingDescriptor> list, PsiElement elementToFold, Document document, boolean allowOneLiners) {
    LOG.assertTrue(elementToFold.isValid());
    TextRange range = getRangeToFold(elementToFold);
    if (range == null) return false;
    return addFoldRegion(list, elementToFold, document, allowOneLiners, range);
  }

  private boolean addFoldRegion(final List<FoldingDescriptor> list, final PsiElement elementToFold, final Document document,
                                final boolean allowOneLiners,
                                final TextRange range) {
    final TextRange fileRange = elementToFold.getContainingFile().getTextRange();
    if (range.equals(fileRange)) return false;

    final ASTNode node = elementToFold.getNode();
    LOG.assertTrue(range.getStartOffset() >= 0 && range.getEndOffset() <= fileRange.getEndOffset());
    // PSI element text ranges may be invalid because of reparse exception (see, for example, IDEA-10617)
    if (range.getStartOffset() < 0 || range.getEndOffset() > fileRange.getEndOffset()) {
      return false;
    }
    if (!allowOneLiners) {
      int startLine = document.getLineNumber(range.getStartOffset());
      int endLine = document.getLineNumber(range.getEndOffset() - 1);
      if (startLine < endLine) {
        list.add(new FoldingDescriptor(node, range));
        return true;
      }
      else {
        return false;
      }
    }
    else {
      if (range.getEndOffset() - range.getStartOffset() > getPlaceholderText(node).length()) {
        list.add(new FoldingDescriptor(node, range));
        return true;
      }
      else {
        return false;
      }
    }
  }
}

