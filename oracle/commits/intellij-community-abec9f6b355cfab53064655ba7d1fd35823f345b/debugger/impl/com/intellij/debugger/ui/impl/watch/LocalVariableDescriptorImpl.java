package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.LocalVariableDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

public class LocalVariableDescriptorImpl extends ValueDescriptorImpl implements LocalVariableDescriptor {
  private final StackFrameProxyImpl myFrameProxy;
  private final LocalVariableProxyImpl myLocalVariable;

  private String myTypeName = DebuggerBundle.message("label.unknown.value");
  private boolean myIsPrimitive;

  private boolean myIsNewLocal = true;
  private boolean myIsVisible = true;

  public LocalVariableDescriptorImpl(Project project,
                                     @NotNull LocalVariableProxyImpl local) {
    super(project);
    setLvalue(true);
    myFrameProxy = local.getFrame();
    myLocalVariable = local;
  }

  public LocalVariableProxyImpl getLocalVariable() {
    return myLocalVariable;
  }

  public SourcePosition getSourcePosition(final Project project, final DebuggerContextImpl context) {
    StackFrameProxyImpl frame = context.getFrameProxy();
    if (frame == null) return null;

    PsiElement place = PositionUtil.getContextElement(context);

    if (place == null) {
      return null;
    }

    PsiVariable psiVariable = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedVariable(getName(), place);
    if (psiVariable == null) {
      return null;
    }

    PsiFile containingFile = psiVariable.getContainingFile();
    if(containingFile == null) return null;

    return SourcePosition.createFromOffset(containingFile, psiVariable.getTextOffset());
  }

  public boolean isNewLocal() {
    return myIsNewLocal;
  }

  public boolean isPrimitive() {
    return myIsPrimitive;
  }

  public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    myIsVisible = myFrameProxy.isLocalVariableVisible(getLocalVariable());
    if (myIsVisible) {
      final String typeName = getLocalVariable().typeName();
      myTypeName = typeName;
      myIsPrimitive = DebuggerUtils.isPrimitiveType(typeName);
      return myFrameProxy.getValue(getLocalVariable());
    }

    return null;
  }

  public void setNewLocal(boolean aNew) {
    myIsNewLocal = aNew;
  }

  public void displayAs(NodeDescriptor descriptor) {
    super.displayAs(descriptor);
    if(descriptor instanceof LocalVariableDescriptorImpl) {
      myIsNewLocal = ((LocalVariableDescriptorImpl)descriptor).myIsNewLocal;
    }
  }

  public String getName() {
    return myLocalVariable.name();
  }

  public String calcValueName() {
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    StringBuilder buf = StringBuilderSpinAllocator.alloc();
    try {
      buf.append(getName());
      if (classRenderer.SHOW_DECLARED_TYPE) {
        buf.append(": ");
        buf.append(myTypeName);
      }
      return buf.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buf);
    }
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    try {
      return elementFactory.createExpressionFromText(getName(), PositionUtil.getContextElement(context));
    }
    catch (IncorrectOperationException e) {
      throw new EvaluateException(DebuggerBundle.message("error.invalid.local.variable.name", getName()), e);
    }
  }
}