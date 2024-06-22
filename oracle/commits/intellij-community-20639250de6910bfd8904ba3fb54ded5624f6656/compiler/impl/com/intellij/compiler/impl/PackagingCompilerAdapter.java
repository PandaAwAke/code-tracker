package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.PackagingCompiler;
import com.intellij.openapi.compiler.ValidityState;

public class PackagingCompilerAdapter extends FileProcessingCompilerAdapter{
  private final PackagingCompiler myCompiler;

  public PackagingCompilerAdapter(CompileContext compileContext, PackagingCompiler compiler) {
    super(compileContext, compiler);
    myCompiler = compiler;
  }

  public void processOutdatedItem(CompileContext context, String url, ValidityState state) {
    myCompiler.processOutdatedItem(context, url, state);
  }
}
