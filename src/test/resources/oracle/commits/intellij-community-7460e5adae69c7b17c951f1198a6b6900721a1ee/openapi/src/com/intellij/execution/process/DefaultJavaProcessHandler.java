/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.diagnostic.Logger;

import java.nio.charset.Charset;

public class DefaultJavaProcessHandler extends OSProcessHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.DefaultJavaProcessHandler");
  private final Charset myCharset;

  public DefaultJavaProcessHandler(final JavaParameters javaParameters) throws ExecutionException {
    this(GeneralCommandLine.createFromJavaParameters(javaParameters));
  }

  public DefaultJavaProcessHandler(final GeneralCommandLine commandLine) throws ExecutionException {
    this(commandLine.createProcess(), commandLine.getCommandLineString(), commandLine.getCharset());
  }

  public DefaultJavaProcessHandler(final Process process, final String commandLine, final Charset charset) {
    super(process, commandLine);
    LOG.assertTrue(charset != null);
    myCharset = charset;
  }

  public Charset getCharset() {
    return myCharset;
  }
}
