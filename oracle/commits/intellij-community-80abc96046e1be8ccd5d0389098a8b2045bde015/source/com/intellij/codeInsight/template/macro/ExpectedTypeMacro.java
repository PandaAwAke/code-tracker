package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.IncorrectOperationException;

import java.util.LinkedHashSet;
import java.util.Set;

public class ExpectedTypeMacro implements Macro{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.macro.ExpectedTypeMacro");

  public String getName() {
    return "expectedType";
  }

  public String getDescription() {
    return "expectedType()";
  }

  public String getDefaultValue() {
    return "A";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    PsiType[] types = getExpectedTypes(params, context);
    if (types == null || types.length == 0) return null;
    return new PsiTypeResult(types[0], PsiManager.getInstance(context.getProject()));
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    PsiType[] types = getExpectedTypes(params, context);
    if (types == null || types.length < 2) return null;
    Set<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (PsiType type : types) {
      LookupItemUtil.addLookupItem(set, type, "");
    }
    return set.toArray(new LookupItem[set.size()]);
  }

  private PsiType[] getExpectedTypes(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiType[] types = null;

    int offset = context.getTemplateStartOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());

    PsiElement element = file.findElementAt(offset);
    if (!(element instanceof PsiIdentifier)) {
      PsiFile fileCopy = (PsiFile)file.copy();
      BlockSupport blockSupport = project.getComponent(BlockSupport.class);
      try{
        blockSupport.reparseRange(fileCopy, offset, offset, "xxx)");
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
      }
      element = fileCopy.findElementAt(offset);
    }

    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiExpression) {
      ExpectedTypeInfo[] infos = ExpectedTypesProvider.getInstance(project).getExpectedTypes((PsiExpression)element.getParent(), false);
      if (infos.length > 0){
        types = new PsiType[infos.length];
        for(int i = 0; i < infos.length; i++) {
          ExpectedTypeInfo info = infos[i];
          types[i] = info.getType();
        }
      }
    }

    return types;
  }
}
