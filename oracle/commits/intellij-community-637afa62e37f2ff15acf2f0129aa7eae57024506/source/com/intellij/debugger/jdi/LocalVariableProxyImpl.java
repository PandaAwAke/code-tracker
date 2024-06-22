package com.intellij.debugger.jdi;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.openapi.util.Comparing;
import com.sun.jdi.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class LocalVariableProxyImpl extends JdiProxy implements LocalVariableProxy {
  private final StackFrameProxyImpl myFrame;
  private final String              myVariableName;

  private LocalVariable myVariable;
  private Type myVariableType;

  public LocalVariableProxyImpl(StackFrameProxyImpl frame, LocalVariable variable) {
    super(frame.myTimer);
    myFrame = frame;
    myVariableName = variable.name();

    myVariable = variable;
    try {
      myVariableType = variable.type();
    }
    catch (ClassNotLoadedException e) {
      myVariableType = null;
    }
  }

  protected void clearCaches() {
    myVariable = null;
    myVariableType = null;
  }

  public LocalVariable getVariable() throws EvaluateException {
    checkValid();
    if(myVariable == null) {
      myVariable = myFrame.visibleVariableByNameInt(myVariableName);

      if(myVariable == null) {
        //myFrame is not this variable's frame
        throw EvaluateExceptionUtil.createEvaluateException(new IncompatibleThreadStateException());
      }
    }

    return myVariable;
  }

  public Type getType() throws EvaluateException, ClassNotLoadedException {
    if (myVariableType == null) {
      myVariableType = getVariable().type();
    }
    return myVariableType;
  }
  
  public StackFrameProxyImpl getFrame() {
    return myFrame;
  }

  public int hashCode() {
    return myFrame.hashCode();
  }

  public boolean equals(Object o) {
    if(o instanceof LocalVariableProxyImpl) {
      LocalVariableProxyImpl proxy = (LocalVariableProxyImpl)o;
      return Comparing.equal(proxy.myFrame, myFrame) && myVariableName.equals(proxy.myVariableName);
    }
    return false;
  }

  public String name() {
    return myVariableName;
  }
}
