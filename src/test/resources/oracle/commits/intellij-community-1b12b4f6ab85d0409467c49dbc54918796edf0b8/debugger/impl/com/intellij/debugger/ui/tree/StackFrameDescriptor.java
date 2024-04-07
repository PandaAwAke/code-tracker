package com.intellij.debugger.ui.tree;

import com.intellij.debugger.engine.jdi.StackFrameProxy;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public interface StackFrameDescriptor extends NodeDescriptor{
  StackFrameProxy getStackFrame();
}
