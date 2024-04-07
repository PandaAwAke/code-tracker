package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.FileCopyPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class FileCopyPackagingElement extends PackagingElement<FileCopyPackagingElement> {
  private String myFilePath;

  public FileCopyPackagingElement() {
    super(PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE);
  }

  public FileCopyPackagingElement(String filePath) {
    super(PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE);
    myFilePath = filePath;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new FileCopyPresentation(myFilePath);
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                           @NotNull AntCopyInstructionCreator creator,
                                                           @NotNull ArtifactAntGenerationContext generationContext) {
    File file = new File(FileUtil.toSystemDependentName(myFilePath));
    final String path = generationContext.getSubstitutedPath(myFilePath);
    Generator generator;
    if (file.isDirectory()) {
      generator = creator.createDirectoryContentCopyInstruction(path);
    }
    else {
      generator = creator.createFileCopyInstruction(path, StringUtil.getShortName(myFilePath, '/'));
    }
    return Collections.singletonList(generator);
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myFilePath);
    if (file == null || !file.isValid()) {
      return;
    }
    if (file.isDirectory()) {
      creator.addDirectoryCopyInstructions(file);
    }
    else {
      creator.addFileCopyInstruction(file);
    }
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof FileCopyPackagingElement && myFilePath != null
           && myFilePath.equals(((FileCopyPackagingElement)element).getFilePath());
  }

  public FileCopyPackagingElement getState() {
    return this;
  }

  public void loadState(FileCopyPackagingElement state) {
    myFilePath = state.getFilePath();
  }

  @Attribute("path")
  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(String filePath) {
    myFilePath = filePath;
  }
}
