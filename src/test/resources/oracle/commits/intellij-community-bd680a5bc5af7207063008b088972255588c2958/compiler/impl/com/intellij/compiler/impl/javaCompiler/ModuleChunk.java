/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.compiler.ex.CompilerPathsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 29, 2004
 */
public class ModuleChunk extends Chunk<Module> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.ModuleChunk");
  private final CompileContextEx myContext;
  private final Map<Module, VirtualFile[]> myModuleToFilesMap = new HashMap<Module, VirtualFile[]>();
  private final Map<VirtualFile, VirtualFile> myTransformedToOriginalMap = new HashMap<VirtualFile, VirtualFile>();
  private int mySourcesFilter = ALL_SOURCES;

  public ModuleChunk(CompileContextEx context, Chunk<Module> chunk, Map<Module, Set<VirtualFile>> moduleToFilesMap) {
    super(chunk.getNodes());
    myContext = context;
    for (final Module module : chunk.getNodes()) {
      final Set<VirtualFile> set = moduleToFilesMap.get(module);
      if (set != null && !set.isEmpty()) {
        myModuleToFilesMap.put(module, set.toArray(new VirtualFile[set.size()]));
      }
      else {
        myModuleToFilesMap.put(module, VirtualFile.EMPTY_ARRAY);
      }
    }
  }

  public static final int SOURCES = 0x1;
  public static final int TEST_SOURCES = 0x2;
  public static final int ALL_SOURCES = SOURCES | TEST_SOURCES;

  public void setSourcesFilter(int filter) {
    mySourcesFilter = filter;
  }

  public void substituteWithTransformedVersion(Module module, int fileIndex, VirtualFile transformedFile) {
    final VirtualFile[] moduleFiles = getFilesToCompile(module);
    final VirtualFile currentFile = moduleFiles[fileIndex];
    moduleFiles[fileIndex] = transformedFile;
    VirtualFile originalFile = myTransformedToOriginalMap.remove(currentFile);
    if (originalFile == null) {
      originalFile = currentFile;
    }
    myTransformedToOriginalMap.put(transformedFile, originalFile);
  }

  public VirtualFile[] getFilesToCompile(Module forModule) {
    return myModuleToFilesMap.get(forModule);
  }

  public VirtualFile[] getFilesToCompile() {
    if (getModuleCount() == 0) {
      return VirtualFile.EMPTY_ARRAY; // optimization
    }
    final Set<Module> modules = getNodes();

    final List<VirtualFile> filesToCompile = new ArrayList<VirtualFile>();
    for (final Module module : modules) {
      final VirtualFile[] moduleCompilableFiles = getFilesToCompile(module);
      if (mySourcesFilter == ALL_SOURCES) {
        filesToCompile.addAll(Arrays.asList(moduleCompilableFiles));
      }
      else {
        for (final VirtualFile file : moduleCompilableFiles) {
          VirtualFile originalFile = myTransformedToOriginalMap.get(file);
          if (originalFile == null) {
            originalFile = file;
          }
          if (mySourcesFilter == TEST_SOURCES) {
            if (myContext.isInTestSourceContent(originalFile)) {
              filesToCompile.add(file);
            }
          }
          else {
            if (!myContext.isInTestSourceContent(originalFile)) {
              filesToCompile.add(file);
            }
          }
        }
      }
    }
    return filesToCompile.toArray(new VirtualFile[filesToCompile.size()]);
  }

  /**
   * @return the jdk. Assumes that the jdk is the same for all modules
   */
  public Sdk getJdk() {
    final Module module = getNodes().iterator().next();
    return ModuleRootManager.getInstance(module).getSdk();
  }

  public VirtualFile[] getSourceRoots() {
    final List<VirtualFile> filteredRoots = new ArrayList<VirtualFile>();
    final Project project = getNodes().iterator().next().getProject();
    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final VirtualFile[] roots = getAllSourceRoots();
        for (final VirtualFile root : roots) {
          if (mySourcesFilter != ALL_SOURCES) {
            if (myContext.isInTestSourceContent(root)) {
              if ((mySourcesFilter & TEST_SOURCES) == 0) {
                continue;
              }
            }
            else {
              if ((mySourcesFilter & SOURCES) == 0) {
                continue;
              }
            }
          }
          if (compilerConfiguration.isExcludedFromCompilation(root)) {
            continue;
          }
          filteredRoots.add(root);
        }
      }
    });
    return filteredRoots.toArray(new VirtualFile[filteredRoots.size()]);
  }

  private VirtualFile[] getAllSourceRoots() {
    final Set<Module> modules = getNodes();
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    for (final Module module : modules) {
      roots.addAll(Arrays.asList(myContext.getSourceRoots(module)));
    }
    return roots.toArray(new VirtualFile[roots.size()]);
  }

  public String getCompilationClasspath() {
    final Set<Module> modules = getNodes();

    final OrderedSet<VirtualFile> cpFiles = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    for (final Module module : modules) {
      final OrderEntry[] orderEntries = CompilerPathsEx.getOrderEntries(module);
      boolean skip = true;
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          skip = false;
          continue;
        }
        if (skip) {
          continue;
        }
        cpFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
      }
    }
    if ((mySourcesFilter & TEST_SOURCES) == 0) {
      // should remove test output dirs (if any) if compiling production classes only
      cpFiles.removeAll(myContext.getTestOutputDirectories());
    }
    return convertToStringPath(cpFiles);

  }

  public String getCompilationBootClasspath() {
    final Set<Module> modules = getNodes();
    final OrderedSet<VirtualFile> cpFiles = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    final OrderedSet<VirtualFile> jdkFiles = new OrderedSet<VirtualFile>(TObjectHashingStrategy.CANONICAL);
    for (final Module module : modules) {
      final OrderEntry[] orderEntries = CompilerPathsEx.getOrderEntries(module);
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          jdkFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.CLASSES)));
          break;
        }
        else {
          cpFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
        }
      }
    }
    if ((mySourcesFilter & TEST_SOURCES) == 0) {
      // should remove test output dirs (if any) if compiling production classes only
      cpFiles.removeAll(myContext.getTestOutputDirectories());
    }
    cpFiles.addAll(jdkFiles);
    return convertToStringPath(cpFiles);
  }

  private String convertToStringPath(final OrderedSet<VirtualFile> cpFiles) {
    final StringBuilder classpathBuffer = StringBuilderSpinAllocator.alloc();
    try {
      for (final VirtualFile file : cpFiles) {
        final String path = PathUtil.getLocalPath(file);
        //if (file.getFileSystem() instanceof LocalFileSystem && file.isDirectory()) {
        //  path = tryZipFor(file.getPath());
        //}
        //else {
        //  path = PathUtil.getLocalPath(file);
        //}

        if (path == null) {
          continue;
        }
        if (classpathBuffer.length() > 0) {
          classpathBuffer.append(File.pathSeparatorChar);
        }
        classpathBuffer.append(path);
      }

      return classpathBuffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(classpathBuffer);
    }
  }

  //private String tryZipFor(String outputDir) {
  //  final File zip = CompilerPathsEx.getZippedOutputPath(myContext.getProject(), outputDir);
  //  if (zip.exists()) {
  //    try {
  //      myContext.commitZip(outputDir); // flush unsaved data if any
  //    }
  //    catch (IOException e) {
  //      LOG.info(e);
  //    }
  //    return zip.getPath();
  //  }
  //  return outputDir;
  //}

  public int getModuleCount() {
    return getNodes().size();
  }

  public Module[] getModules() {
    final Set<Module> nodes = getNodes();
    return nodes.toArray(new Module[nodes.size()]);
  }

  public String getSourcePath() {
    if (getModuleCount() == 0) {
      return "";
    }
    final VirtualFile[] filteredRoots = getSourceRoots();
    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (VirtualFile root : filteredRoots) {
            if (buffer.length() > 0) {
              buffer.append(File.pathSeparatorChar);
            }
            buffer.append(root.getPath().replace('/', File.separatorChar));
          }
        }
      });
      return buffer.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  //the check for equal language levels is done elsewhere
  public LanguageLevel getLanguageLevel() {
    return LanguageLevelUtil.getEffectiveLanguageLevel(getModules()[0]);
  }
}
