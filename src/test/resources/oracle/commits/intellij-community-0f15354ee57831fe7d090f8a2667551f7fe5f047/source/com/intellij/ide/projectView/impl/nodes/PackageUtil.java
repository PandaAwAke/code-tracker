/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.*;

public class PackageUtil {
  protected static final Icon TREE_CLOSED_ICON = Icons.DIRECTORY_CLOSED_ICON;
  protected static final Icon TREE_OPEN_ICON = Icons.DIRECTORY_OPEN_ICON;
  static private final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode");

  public static PsiPackage[] getSubpackages(PsiPackage aPackage,
                                            Module module,
                                            final Project project,
                                            final boolean searchInLibraries) {
    final GlobalSearchScope scopeToShow = getScopeToShow(project, module, searchInLibraries);
    final PsiDirectory[] dirs = aPackage.getDirectories(scopeToShow);
    final Set<PsiPackage> subpackages = new HashSet<PsiPackage>();
    for (int idx = 0; idx < dirs.length; idx++) {
      PsiDirectory dir = dirs[idx];
      final PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (int i = 0; i < subdirectories.length; i++) {
        PsiDirectory subdirectory = subdirectories[i];
        final PsiPackage psiPackage = subdirectory.getPackage();
        if (psiPackage != null) {
          final String name = psiPackage.getName();
          // skip "default" subpackages as they should be attributed to other modules
          // this is the case when contents of one module is nested into contents of another
          if (name != null && !"".equals(name)) {
            subpackages.add(psiPackage);
          }
        }
      }
    }
    return subpackages.toArray(new PsiPackage[subpackages.size()]);
  }

  public static void addPackageAsChild(final Collection<AbstractTreeNode> children,
                                       final PsiPackage aPackage,
                                       Module module,
                                       ViewSettings settings,
                                       final boolean inLibrary) {
    final boolean shouldSkipPackage = settings.isHideEmptyMiddlePackages() && isPackageEmpty(aPackage, module, !settings.isFlattenPackages(), inLibrary);
    final Project project = aPackage.getManager().getProject();
    if (!shouldSkipPackage) {
      children.add(new PackageElementNode(project,
                                          new PackageElement(module, aPackage, inLibrary), settings));
    }
    if (settings.isFlattenPackages() || shouldSkipPackage) {
      final PsiPackage[] subpackages = PackageUtil.getSubpackages(aPackage, module, project, inLibrary);
      for (int idx = 0; idx < subpackages.length; idx++) {
        addPackageAsChild(children, subpackages[idx], module, settings, inLibrary);
      }
    }
  }

  public static boolean isPackageEmpty(PsiPackage aPackage,
                                       Module module,
                                       boolean strictlyEmpty,
                                       final boolean inLibrary) {
    final Project project = aPackage.getManager().getProject();
    final GlobalSearchScope scopeToShow = getScopeToShow(project, module, inLibrary);
    final PsiDirectory[] dirs = aPackage.getDirectories(scopeToShow);
    for (int idx = 0; idx < dirs.length; idx++) {
      final PsiDirectory dir = dirs[idx];
      if (!TreeViewUtil.isEmptyMiddlePackage(dir, strictlyEmpty)) {
        return false;
      }
    }
    return true;
  }

  public static GlobalSearchScope getScopeToShow(final Project project, final Module module, boolean forLibraries) {
    if (module != null) {
      if (forLibraries) {
        return new ModuleLibrariesSearchScope(module);
      }
      else {
        return GlobalSearchScope.moduleScope(module);
      }
    }
    else {
      if (forLibraries) {
        return new ProjectLibrariesSearchScope(project);
      }
      else {
        return GlobalSearchScope.projectScope(project);
      }
    }
  }


  public static boolean isPackageDefault(PsiPackage directoryPackage) {
    final String qName = directoryPackage.getQualifiedName();
    return qName == null || qName.length() == 0;
  }

  public static Collection<AbstractTreeNode> createPackageViewChildrenOnFiles(final List<VirtualFile> sourceRoots,
                                                                              final Project project,
                                                                              final ViewSettings settings,
                                                                              final Module module,
                                                                              final boolean inLibrary) {
    final PsiManager psiManager = PsiManager.getInstance(project);

    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Set<PsiPackage> topLevelPackages = new HashSet<PsiPackage>();

    for (Iterator<VirtualFile> it = sourceRoots.iterator(); it.hasNext();) {
      final VirtualFile root = it.next();
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory == null) {
      continue;
      }
      final PsiPackage directoryPackage = directory.getPackage();
      if (directoryPackage == null || isPackageDefault(directoryPackage)) {
        // add subpackages
        final PsiDirectory[] subdirectories = directory.getSubdirectories();
        for (int i = 0; i < subdirectories.length; i++) {
          final PsiPackage aPackage = subdirectories[i].getPackage();
          if (aPackage != null && !isPackageDefault(aPackage)) {
            topLevelPackages.add(aPackage);
          }
        }
        // add non-dir items
        children.addAll(getDirectoryChildren(directory, settings, false));
      }
      else {
        topLevelPackages.add(directoryPackage);
      }
    }

    for (Iterator<PsiPackage> it = topLevelPackages.iterator(); it.hasNext();) {
      addPackageAsChild(children, it.next(), module, settings, inLibrary);
    }

    return children;
  }

  public static boolean moduleContainsFile(final Module module, VirtualFile file, ViewSettings settings, boolean isLibraryElement) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    if (isLibraryElement) {
      OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (int i = 0; i < orderEntries.length; i++) {
        OrderEntry orderEntry = orderEntries[i];
        if (NamedLibraryElementNode.orderEntryContainsFile(orderEntry, file)) {
          return orderEntry instanceof ModuleJdkOrderEntry
                 || orderEntry instanceof JdkOrderEntry
                 || orderEntry instanceof LibraryOrderEntry;
        }
      }
      return false;
    }
    else {
      return moduleRootManager.getFileIndex().isInContent(file);
    }
  }

  public static boolean projectContainsFile(final Project project, VirtualFile file, ViewSettings settings, boolean isLibraryElement) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];
      if (moduleContainsFile(module, file, settings, isLibraryElement)) return true;
    }
    return false;
  }

  public static boolean isSourceRoot(final PsiDirectory psiDirectory) {
    return psiDirectory.getVirtualFile().equals(
      ProjectRootManager.getInstance(psiDirectory.getManager().getProject()).getFileIndex()
        .getSourceRootForFile(psiDirectory.getVirtualFile()));
  }

  public static boolean isPackage(final PsiDirectory psiDirectory) {
    return psiDirectory.getPackage() != null;
  }

  public static boolean isFQNameShown(final PsiDirectory value, final Object parentValue, final ViewSettings settings) {
    if (value == null) return false;

    if (parentValue instanceof Project) {
      return false;
    }
    else {
      if (settings.isFlattenPackages()) {
        return !isSourceRoot(value) && isPackage(value) && value.getPackage().getQualifiedName().length() > 0;
      }
      else {
        return false;
      }
    }
  }

  public static void updatePsiDirectoryData(final PresentationData data,
                                            final Project project,
                                            final PsiDirectory psiDirectory,
                                            final ViewSettings settings,
                                            final Object parentValue,
                                            final AbstractTreeNode node) {
    final VirtualFile directoryFile = psiDirectory.getVirtualFile();
    boolean withComment = isModuleContentRoot(directoryFile, project) || isLibraryRoot(directoryFile, project);
    updateDefault(data, psiDirectory, settings, parentValue, node);
    if (withComment) {
      data.setLocationString(directoryFile.getPresentableUrl());
    }
  }

  private static void updateDefault(PresentationData data,
                                    final PsiDirectory psiDirectory,
                                    final ViewSettings settings,
                                    final Object parentValue,
                                    final AbstractTreeNode node) {
    PsiPackage aPackage = psiDirectory.getPackage();
    final VirtualFile virtualFile = psiDirectory.getVirtualFile();

    if (isPackage(psiDirectory) && !isSourceRoot(psiDirectory) && !settings.isFlattenPackages() && settings.isHideEmptyMiddlePackages()) {
      if (!(node.getParent() instanceof LibraryGroupNode) && TreeViewUtil.isEmptyMiddlePackage(psiDirectory, true)) {
        node.setValue(null);
        return;
      }
    }

    final boolean isWritable = virtualFile.isWritable();

    final String name;
    if (parentValue instanceof Project) {
      name = psiDirectory.getVirtualFile().getPresentableUrl();
    }
    else {

      if (isFQNameShown(psiDirectory, parentValue, settings)) {
        name = (settings.isAbbreviatePackageNames()
                ? TreeViewUtil.calcAbbreviatedPackageFQName(aPackage)
                : aPackage.getQualifiedName());
      }
      else {
        if (!isSourceRoot(psiDirectory) && aPackage != null && aPackage.getQualifiedName().length() > 0 &&
          parentValue instanceof PsiDirectory) {
          final PsiPackage parentPackageInTree = ((PsiDirectory)parentValue).getPackage();
          PsiPackage parentPackage = aPackage.getParentPackage();
          final StringBuffer buf = new StringBuffer();
          buf.append(aPackage.getName());
          while (parentPackage != null && !parentPackage.equals(parentPackageInTree)) {
            final String parentPackageName = parentPackage.getName();
            if (parentPackageName == null || "".equals(parentPackageName)) {
            break; // reached default package
            }
            buf.insert(0, ".");
            buf.insert(0, parentPackageName);
            parentPackage = parentPackage.getParentPackage();
          }
          name = buf.toString();
        }
        else {
          name = psiDirectory.getName();
        }
      }
    }

    final String packagePrefix = isSourceRoot(psiDirectory) && aPackage != null ? aPackage.getQualifiedName() : "";

    data.setPresentableText(name);
    data.setLocationString(packagePrefix);

    if (isPackage(psiDirectory)) {
      data.setOpenIcon(addReadMark(Icons.PACKAGE_OPEN_ICON, isWritable));
      data.setClosedIcon(addReadMark(Icons.PACKAGE_ICON, isWritable));
    }
    else {
      data.setOpenIcon(addReadMark(TREE_OPEN_ICON, isWritable));
      data.setClosedIcon(addReadMark(TREE_CLOSED_ICON, isWritable));
    }
  }

  public static boolean isModuleContentRoot(VirtualFile directoryFile, Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile contentRootForFile = projectFileIndex.getContentRootForFile(directoryFile);
    return directoryFile.equals(contentRootForFile);
  }

  public static boolean isLibraryRoot(VirtualFile directoryFile, Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (projectFileIndex.isInLibraryClasses(directoryFile)) {
      final VirtualFile parent = directoryFile.getParent();
      return parent == null || !projectFileIndex.isInLibraryClasses(parent);
    }
    return false;
  }

  protected static Icon addReadMark(Icon originalIcon, boolean isWritable) {
    if (isWritable) {
      return originalIcon;
    }
    else {
      return IconUtilEx.createLayeredIcon(originalIcon, Icons.LOCKED_ICON);
    }
  }

  public static Collection<AbstractTreeNode> getDirectoryChildren(final PsiDirectory psiDirectory,
                                                                  final ViewSettings settings,
                                                                  boolean withSubDirectories) {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Project project = psiDirectory.getManager().getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(psiDirectory.getVirtualFile());
    final ModuleFileIndex moduleFileIndex = module == null ? null : ModuleRootManager.getInstance(module).getFileIndex();
    if (!settings.isFlattenPackages() || psiDirectory.getPackage() == null) {
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getChildren(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories);
    }
    else { // source directory in "flatten packages" mode
      final PsiDirectory parentDir = psiDirectory.getParentDirectory();
      if (parentDir == null || parentDir.getPackage() == null /*|| !rootDirectoryFound(parentDir)*/ && withSubDirectories) {
        addAllSubpackages(children, psiDirectory, moduleFileIndex, settings);
      }
      PsiDirectory[] subdirs = psiDirectory.getSubdirectories();
      for (PsiDirectory subdir : subdirs) {
        if (subdir.getPackage() != null) {
          continue;
        }
        if (moduleFileIndex != null) {
          if (!moduleFileIndex.isInContent(subdir.getVirtualFile())) {
            continue;
          }
        }
        if (withSubDirectories) {
          children.add(new PsiDirectoryNode(project, subdir, settings));
        }
      }
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getFiles(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories);
    }
    return children;
  }

  // used only for non-flatten packages mode
  private static void processPsiDirectoryChildren(final PsiDirectory psiDir,
                                                  PsiElement[] children,
                                                  List<AbstractTreeNode> container,
                                                  ProjectFileIndex projectFileIndex,
                                                  ModuleFileIndex moduleFileIndex,
                                                  ViewSettings viewSettings,
                                                  boolean withSubDirectories) {
    for (PsiElement child : children) {
      LOG.assertTrue(child.isValid());

      final VirtualFile vFile;
      if (child instanceof PsiFile) {
        vFile = ((PsiFile)child).getVirtualFile();
        addNode(moduleFileIndex, projectFileIndex, psiDir, vFile, container, PsiFileNode.class, child, viewSettings);
      }
      else if (child instanceof PsiDirectory) {
        if (withSubDirectories) {
          PsiDirectory dir = (PsiDirectory)child;
          vFile = dir.getVirtualFile();
          if (!vFile.equals(projectFileIndex.getSourceRootForFile(vFile))) { // if is not a source root
            if (viewSettings.isHideEmptyMiddlePackages() && dir.getPackage() != null && TreeViewUtil.isEmptyMiddlePackage(dir, true)) {
              processPsiDirectoryChildren(dir, dir.getChildren(), container, projectFileIndex, moduleFileIndex, viewSettings,
                                          withSubDirectories); // expand it recursively
              continue;
            }
          }
          addNode(moduleFileIndex, projectFileIndex, psiDir, vFile, container, PsiDirectoryNode.class, child, viewSettings);
        }
      }
      else {
        LOG.assertTrue(false, "Either PsiFile or PsiDirectory expected as a child of " + child.getParent() + ", but was " + child);
      }
    }
  }

  private static void addNode(ModuleFileIndex moduleFileIndex,
                              ProjectFileIndex projectFileIndex,
                              PsiDirectory psiDir,
                              VirtualFile vFile,
                              List<AbstractTreeNode> container,
                              Class<? extends AbstractTreeNode> nodeClass,
                              PsiElement element,
                              final ViewSettings settings) {
    if (vFile == null) {
      return;
    }
    // this check makes sense for classes not in library content only
    if (moduleFileIndex != null && !moduleFileIndex.isInContent(vFile)) {
      return;
    }
    final boolean childInLibraryClasses = projectFileIndex.isInLibraryClasses(vFile);
    if (!projectFileIndex.isInSourceContent(vFile)) {
      if (childInLibraryClasses) {
        final VirtualFile psiDirVFile = psiDir.getVirtualFile();
        final boolean parentInLibraryContent =
          projectFileIndex.isInLibraryClasses(psiDirVFile) || projectFileIndex.isInLibrarySource(psiDirVFile);
        if (!parentInLibraryContent) {
          return;
        }
      }
    }
    if (childInLibraryClasses && !projectFileIndex.isInContent(vFile) && projectFileIndex.isJavaSourceFile(vFile)) {
      return; // skip java sources in classpath
    }

    try {
      container.add(ProjectViewNode.createTreeNode(nodeClass, element.getManager().getProject(), element, settings));
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  // used only in flatten packages mode
  private static void addAllSubpackages(List<AbstractTreeNode> container,
                                        PsiDirectory dir,
                                        ModuleFileIndex moduleFileIndex,
                                        ViewSettings viewSettings) {
    final Project project = dir.getManager().getProject();
    PsiDirectory[] subdirs = dir.getSubdirectories();
    for (int i = 0; i < subdirs.length; i++) {
      PsiDirectory subdir = subdirs[i];
      if (subdir.getPackage() == null) {
      continue;
      }
      if (moduleFileIndex != null) {
        if (!moduleFileIndex.isInContent(subdir.getVirtualFile())) {
        continue;
        }
      }
      if (viewSettings.isHideEmptyMiddlePackages()) {
        if (!TreeViewUtil.isEmptyMiddlePackage(subdir, false)) {

          container.add(new PsiDirectoryNode(project, subdir, viewSettings));
        }
      }
      else {
        container.add(new PsiDirectoryNode(project, subdir, viewSettings));
      }
      addAllSubpackages(container, subdir, moduleFileIndex, viewSettings);
    }
  }

  private static class ModuleLibrariesSearchScope extends GlobalSearchScope {
    private final Module myModule;

    public ModuleLibrariesSearchScope(final Module module) {
      myModule = module;
    }

    public boolean contains(VirtualFile file) {
      final OrderEntry orderEntry = ModuleRootManager.getInstance(myModule).getFileIndex().getOrderEntryForFile(file);
      return orderEntry instanceof JdkOrderEntry || orderEntry instanceof LibraryOrderEntry;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(myModule).getFileIndex();
      return fileIndex.getOrderEntryForFile(file2).compareTo(fileIndex.getOrderEntryForFile(file1));
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }

  private static class ProjectLibrariesSearchScope extends GlobalSearchScope {
    private final Project myProject;

    public ProjectLibrariesSearchScope(final Project project) {
      myProject = project;
    }

    private Module[] getModules(final Project project) {
      return ModuleManager.getInstance(project).getModules();
    }

    public boolean contains(VirtualFile file) {
      final Module[] modules = getModules(myProject);
      for (int i = 0; i < modules.length; i++) {
        Module module1 = modules[i];
        final OrderEntry orderEntryForFile = ModuleRootManager.getInstance(module1).getFileIndex().getOrderEntryForFile(file);
        if (orderEntryForFile instanceof JdkOrderEntry || orderEntryForFile instanceof LibraryOrderEntry) return true;
      }
      return false;
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      final Module[] modules = getModules(myProject);
      for (int i = 0; i < modules.length; i++) {
        Module module1 = modules[i];
        final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module1).getFileIndex();
        final OrderEntry orderEntry1 = fileIndex.getOrderEntryForFile(file1);
        if (orderEntry1 != null) {
          final OrderEntry orderEntry2 = fileIndex.getOrderEntryForFile(file2);
          if (orderEntry2 != null) {
            return orderEntry2.compareTo(orderEntry1);
          }
          else {
            return 0;
          }
        }
      }
      return 0;
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }
}
