/*
 * @author: Eugene Zhuravlev
 * Date: Jan 21, 2003
 * Time: 4:19:03 PM
 */
package com.intellij.compiler.impl;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.make.DependencyCache;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.OrderedSet;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;


public class CompileContextImpl extends UserDataHolderBase implements CompileContextEx {
  private final Project myProject;
  private final CompilerTask myTask;
  private final Map<CompilerMessageCategory, Collection<CompilerMessage>> myMessages = new HashMap<CompilerMessageCategory, Collection<CompilerMessage>>();
  private CompileScope myCompileScope;
  private final DependencyCache myDependencyCache;
  private final boolean myMake;
  private final boolean myIsRebuild;
  private boolean myRebuildRequested = false;
  private String myRebuildReason;
  private final Map<VirtualFile, Module> myRootToModuleMap = new HashMap<VirtualFile, Module>();
  private final Map<Module, Set<VirtualFile>> myModuleToRootsMap = new HashMap<Module, Set<VirtualFile>>();
  private final Set<VirtualFile> myGeneratedTestRoots = new java.util.HashSet<VirtualFile>();
  private VirtualFile[] myOutputDirectories;
  private Set<VirtualFile> myTestOutputDirectories;
  private final TIntHashSet myGeneratedSources = new TIntHashSet();
  private final ProjectFileIndex myProjectFileIndex; // cached for performance reasons
  private final ProjectCompileScope myProjectCompileScope;
  private final long myStartCompilationStamp;

  public CompileContextImpl(Project project,
                            CompilerTask indicator,
                            CompileScope compileScope,
                            DependencyCache dependencyCache, boolean isMake, boolean isRebuild) {
    myProject = project;
    myTask = indicator;
    myCompileScope = compileScope;
    myDependencyCache = dependencyCache;
    myMake = isMake;
    myIsRebuild = isRebuild;
    myStartCompilationStamp = System.currentTimeMillis();
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    myProjectCompileScope = new ProjectCompileScope(myProject);

    recalculateOutputDirs();
  }

  public void recalculateOutputDirs() {
    final Module[] allModules = ModuleManager.getInstance(myProject).getModules();

    final Set<VirtualFile> allDirs = new OrderedSet<VirtualFile>((TObjectHashingStrategy<VirtualFile>)TObjectHashingStrategy.CANONICAL);
    final Set<VirtualFile> testOutputDirs = new java.util.HashSet<VirtualFile>();
    final Set<VirtualFile> productionOutputDirs = new java.util.HashSet<VirtualFile>();

    for (Module module : allModules) {
      final CompilerModuleExtension manager = CompilerModuleExtension.getInstance(module);
      final VirtualFile output = manager.getCompilerOutputPath();
      if (output != null && output.isValid()) {
        allDirs.add(output);
        productionOutputDirs.add(output);
      }
      final VirtualFile testsOutput = manager.getCompilerOutputPathForTests();
      if (testsOutput != null && testsOutput.isValid()) {
        allDirs.add(testsOutput);
        testOutputDirs.add(testsOutput);
      }
    }
    myOutputDirectories = allDirs.toArray(new VirtualFile[allDirs.size()]);
    // need this to ensure that the sent contains only _dedicated_ test output dirs
    // Directories that are configured for both test and production classes must not be added in the resulting set
    testOutputDirs.removeAll(productionOutputDirs);
    myTestOutputDirectories = Collections.unmodifiableSet(testOutputDirs);
  }

  public void markGenerated(Collection<VirtualFile> files) {
    for (final VirtualFile file : files) {
      myGeneratedSources.add(FileBasedIndex.getFileId(file));
    }
  }

  public long getStartCompilationStamp() {
    return myStartCompilationStamp;
  }

  public boolean isGenerated(VirtualFile file) {
    return myGeneratedSources.contains(FileBasedIndex.getFileId(file));
  }

  public Project getProject() {
    return myProject;
  }

  public DependencyCache getDependencyCache() {
    return myDependencyCache;
  }

  public CompilerMessage[] getMessages(CompilerMessageCategory category) {
    Collection<CompilerMessage> collection = myMessages.get(category);
    if (collection == null) {
      return CompilerMessage.EMPTY_ARRAY;
    }
    return collection.toArray(new CompilerMessage[collection.size()]);
  }

  public void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
    CompilerMessageImpl msg = new CompilerMessageImpl(myProject, category, message, url, lineNum, columnNum, null);
    addMessage(msg);
  }

  public void addMessage(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum,
                         Navigatable navigatable) {
    CompilerMessageImpl msg = new CompilerMessageImpl(myProject, category, message, url, lineNum, columnNum, navigatable);
    addMessage(msg);
  }

  public void addMessage(CompilerMessage msg) {
    Collection<CompilerMessage> messages = myMessages.get(msg.getCategory());
    if (messages == null) {
      messages = new HashSet<CompilerMessage>();
      myMessages.put(msg.getCategory(), messages);
    }
    if (messages.add(msg)) {
      myTask.addMessage(this, msg);
    }
  }

  public int getMessageCount(CompilerMessageCategory category) {
    if (category != null) {
      Collection<CompilerMessage> collection = myMessages.get(category);
      return collection != null ? collection.size() : 0;
    }
    int count = 0;
    for (Collection<CompilerMessage> collection : myMessages.values()) {
      if (collection != null) {
        count += collection.size();
      }
    }
    return count;
  }

  public CompileScope getCompileScope() {
    return myCompileScope;
  }

  public CompileScope getProjectCompileScope() {
    return myProjectCompileScope;
  }

  public void requestRebuildNextTime(String message) {
    if (!myRebuildRequested) {
      myRebuildRequested = true;
      myRebuildReason = message;
      addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
    }
  }

  public boolean isRebuildRequested() {
    return myRebuildRequested;
  }

  public String getRebuildReason() {
    return myRebuildReason;
  }

  public ProgressIndicator getProgressIndicator() {
    return myTask.getIndicator();
  }

  public void assignModule(VirtualFile root, Module module, final boolean isTestSource) {
    try {
      myRootToModuleMap.put(root, module);
      Set<VirtualFile> set = myModuleToRootsMap.get(module);
      if (set == null) {
        set = new HashSet<VirtualFile>();
        myModuleToRootsMap.put(module, set);
      }
      set.add(root);
      if (isTestSource) {
        myGeneratedTestRoots.add(root);
      }
    }
    finally {
      myModuleToRootsCache.remove(module);
    }
  }

  @Nullable
  public VirtualFile getSourceFileByOutputFile(VirtualFile outputFile) {
    return TranslatingCompilerFilesMonitor.getSourceFileByOutput(outputFile);
  }

  public Module getModuleByFile(VirtualFile file) {
    final Module module = myProjectFileIndex.getModuleForFile(file);
    if (module != null) {
      return module;
    }
    for (final VirtualFile root : myRootToModuleMap.keySet()) {
      if (VfsUtil.isAncestor(root, file, false)) {
        return myRootToModuleMap.get(root);
      }
    }
    return null;
  }


  private final Map<Module, VirtualFile[]> myModuleToRootsCache = new HashMap<Module, VirtualFile[]>();

  public VirtualFile[] getSourceRoots(Module module) {
    VirtualFile[] cachedRoots = myModuleToRootsCache.get(module);
    if (cachedRoots != null) {
      if (areFilesValid(cachedRoots)) {
        return cachedRoots;
      }
      else {
        myModuleToRootsCache.remove(module); // clear cache for this module and rebuild list of roots
      }
    }

    Set<VirtualFile> additionalRoots = myModuleToRootsMap.get(module);
    VirtualFile[] moduleRoots = ModuleRootManager.getInstance(module).getSourceRoots();
    if (additionalRoots == null || additionalRoots.size() == 0) {
      myModuleToRootsCache.put(module, moduleRoots);
      return moduleRoots;
    }

    final VirtualFile[] allRoots = new VirtualFile[additionalRoots.size() + moduleRoots.length];
    System.arraycopy(moduleRoots, 0, allRoots, 0, moduleRoots.length);
    int index = moduleRoots.length;
    for (final VirtualFile additionalRoot : additionalRoots) {
      allRoots[index++] = additionalRoot;
    }
    myModuleToRootsCache.put(module, allRoots);
    return allRoots;
  }

  private boolean areFilesValid(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!file.isValid()) {
        return false;
      }
    }
    return true;
  }

  public VirtualFile[] getAllOutputDirectories() {
    return myOutputDirectories;
  }

  @NotNull
  public Set<VirtualFile> getTestOutputDirectories() {
    return myTestOutputDirectories;
  }

  public VirtualFile getModuleOutputDirectory(Module module) {
    // todo: caching?
    return CompilerPaths.getModuleOutputDirectory(module, false);
  }

  public VirtualFile getModuleOutputDirectoryForTests(Module module) {
    // todo: caching?
    return CompilerPaths.getModuleOutputDirectory(module, true);
  }

  public boolean isMake() {
    return myMake;
  }

  public boolean isRebuild() {
    return myIsRebuild;
  }

  public void addScope(final CompileScope additionalScope) {
    myCompileScope = new CompositeScope(myCompileScope, additionalScope);
  }

  public boolean isInTestSourceContent(@NotNull final VirtualFile fileOrDir) {
    if (myProjectFileIndex.isInTestSourceContent(fileOrDir)) {
      return true;
    }
    for (final VirtualFile root : myGeneratedTestRoots) {
      if (VfsUtil.isAncestor(root, fileOrDir, false)) {
        return true;
      }
    }
    return false;
  }

  public boolean isInSourceContent(@NotNull final VirtualFile fileOrDir) {
    if (myProjectFileIndex.isInSourceContent(fileOrDir)) {
      return true;
    }
    for (final VirtualFile root : myRootToModuleMap.keySet()) {
      if (VfsUtil.isAncestor(root, fileOrDir, false)) {
        return true;
      }
    }
    return false;
  }
}
