package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.todo.HighlightedRegionProvider;
import com.intellij.ide.todo.TodoFileDirAndModuleComparator;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.todo.TodoTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.HighlightedRegion;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public final class TodoDirNode extends PsiDirectoryNode implements HighlightedRegionProvider {
  private final ArrayList myHighlightedRegions;
  private final TodoTreeBuilder myBuilder;


  public TodoDirNode(Project project,
                     PsiDirectory directory,
                     TodoTreeBuilder builder) {
    super(project, directory, ViewSettings.DEFAULT);
    myBuilder = builder;
    myHighlightedRegions = new ArrayList(2);
  }

  public ArrayList getHighlightedRegions() {
    return myHighlightedRegions;
  }

  public void updateImpl(PresentationData data) {
    super.updateImpl(data);
    int fileCount = getStructure().getFileCount(getValue());
    if (getValue() == null || !getValue().isValid() || fileCount == 0) {
      setValue(null);
      return;
    }

    VirtualFile directory = getValue().getVirtualFile();
    boolean isProjectRoot = !ProjectRootManager.getInstance(getProject()).getFileIndex().isInContent(directory);
    String newName = isProjectRoot || getStructure().getIsFlattenPackages() ? getValue().getVirtualFile().getPresentableUrl() : getValue().getName();

    StringBuffer sb = new StringBuffer(newName);
    int nameEndOffset = newName.length();
    int todoItemCount = getStructure().getTodoItemCount(getValue());
    sb.append(" ( ").append(todoItemCount).append(" item");
    if (todoItemCount > 1) {
      sb.append('s');
    }
    sb.append(" in " + fileCount).append(" file");
    if (fileCount > 1) {
      sb.append('s');
    }
    sb.append(" )");
    newName = sb.toString();

    myHighlightedRegions.clear();

    TextAttributes textAttributes = new TextAttributes();
    Color newColor = FileStatusManager.getInstance(getProject()).getStatus(getValue().getVirtualFile()).getColor();

    if (CopyPasteManager.getInstance().isCutElement(getValue())) {
      newColor = CopyPasteManager.CUT_COLOR;
    }
    textAttributes.setForegroundColor(newColor);
    myHighlightedRegions.add(new HighlightedRegion(0, nameEndOffset, textAttributes));

    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myHighlightedRegions.add(
      new HighlightedRegion(nameEndOffset, newName.length(), colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES)));

    data.setPresentableText(newName);
  }

  private TodoTreeStructure getStructure() {
    return myBuilder.getTodoTreeStructure();
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    ArrayList<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final PsiDirectory psiDirectory = getValue();
    if (!getStructure().getIsFlattenPackages() || psiDirectory.getPackage() == null) {
      final Iterator<PsiFile> iterator = myBuilder.getFiles(psiDirectory);
      while (iterator.hasNext()) {
        final PsiFile psiFile = iterator.next();
        // Add files
        final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
        TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
        if (psiDirectory.equals(containingDirectory) && !children.contains(todoFileNode)) {
          children.add(todoFileNode);
          continue;
        }
        // Add directories (find first ancestor directory that is in our psiDirectory)
        PsiDirectory _dir = psiFile.getContainingDirectory();
        while (_dir != null) {
          if (_dir.getPackage() != null){
            break;
          }
          final PsiDirectory parentDirectory = _dir.getParentDirectory();
          TodoDirNode todoDirNode = new TodoDirNode(getProject(), _dir, myBuilder);
          if (parentDirectory != null && psiDirectory.equals(parentDirectory) && !children.contains(todoDirNode)) {
            children.add(todoDirNode);
            break;
          }
          _dir = parentDirectory;
        }
      }
      Collections.sort(children, TodoFileDirAndModuleComparator.ourInstance);
    }
    else { // flatten packages
      final PsiDirectory parentDirectory = psiDirectory.getParentDirectory();
      if (
        parentDirectory == null ||
        parentDirectory.getPackage() == null ||
        !ProjectRootManager.getInstance(getProject()).getFileIndex().isInContent(parentDirectory.getVirtualFile())
      ) {
        final Iterator<PsiFile> iterator = myBuilder.getFiles(psiDirectory);
        while (iterator.hasNext()) {
          final PsiFile psiFile = iterator.next();
          // Add files
          TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
          if (psiDirectory.equals(psiFile.getContainingDirectory()) && !children.contains(todoFileNode)) {
            children.add(todoFileNode);
            continue;
          }
          // Add directories
          final PsiDirectory _dir = psiFile.getContainingDirectory();
          if (_dir.getPackage() != null){
            continue;
          }
          TodoDirNode todoDirNode = new TodoDirNode(getProject(), _dir, myBuilder);
          if (PsiTreeUtil.isAncestor(psiDirectory, _dir, true) && !children.contains(todoDirNode) && !myBuilder.isDirectoryEmpty(_dir)) {
            children.add(todoDirNode);
            continue;
          }
        }
      }
      else {
        final Iterator<PsiFile> iterator = myBuilder.getFiles(psiDirectory);
        while (iterator.hasNext()) {
          final PsiFile psiFile = iterator.next();
          final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
          TodoFileNode todoFileNode = new TodoFileNode(getProject(), psiFile, myBuilder, false);
          if (psiDirectory.equals(containingDirectory) && !children.contains(todoFileNode)) {
            children.add(todoFileNode);
          }
        }
      }
      Collections.sort(children, TodoFileDirAndModuleComparator.ourInstance);
    }
    return children;
  }
}
