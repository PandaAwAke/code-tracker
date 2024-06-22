/**
 * @author Yura Cangea
 */
package com.intellij.openapi.fileChooser.impl;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.vfs.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

public class FileTreeBuilder extends AbstractTreeBuilder {
  private final FileChooserDescriptor myChooserDescriptor;

  private VirtualFileAdapter myVirtualFileListener;


  public FileTreeBuilder(JTree tree, DefaultTreeModel treeModel,
                         AbstractTreeStructure treeStructure,
                         Comparator<NodeDescriptor> comparator,
                         FileChooserDescriptor chooserDescriptor) {
    super(tree, treeModel, treeStructure, comparator);
    myChooserDescriptor = chooserDescriptor;
    initRootNode();

    installVirtualFileListener();
  }

  private void installVirtualFileListener() {
    myVirtualFileListener = new VirtualFileAdapter() {
      public void propertyChanged(VirtualFilePropertyEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }

      public void fileCreated(VirtualFileEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }

      public void fileDeleted(VirtualFileEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }

      public void fileMoved(VirtualFileMoveEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }
    };

    VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener);
  }

  public final void dispose() {
    VirtualFileManager.getInstance().removeVirtualFileListener(myVirtualFileListener);
    super.dispose();
  }

  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    final Object element = nodeDescriptor.getElement();
    if (element != null){
      FileElement descriptor = (FileElement)element;
      VirtualFile file = descriptor.getFile();
      if (file != null){
        if (myChooserDescriptor.isChooseJarContents() && FileElement.isArchive(file)) {
          return true;
        }
        if (file.isDirectory()) {
          return true;
        }
        return false;
      }
    }
    return true;
  }

  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected final void expandNodeChildren(DefaultMutableTreeNode node) {
    Object element = ((NodeDescriptor)node.getUserObject()).getElement();
    if (element instanceof FileElement){
      final VirtualFile file = ((FileElement)element).getFile();
      if (file != null && file.isValid()){
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              file.refresh(false, false);
            }
          }
        );
      }
    }
    super.expandNodeChildren(node);
  }
}
