package com.intellij.ide.util.treeView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Iterator;
import java.util.LinkedList;

public class AbstractTreeUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeUpdater");

  private LinkedList<DefaultMutableTreeNode> myNodesToUpdate = new LinkedList<DefaultMutableTreeNode>();
  private final AbstractTreeBuilder myTreeBuilder;
  private Runnable myRunAfterUpdate;
  private Runnable myRunBeforeUpdate;
  private MergingUpdateQueue myUpdateQueue;
  private Disposable myDisposable;

  public AbstractTreeUpdater(AbstractTreeBuilder treeBuilder) {
    myTreeBuilder = treeBuilder;
    final JTree tree = myTreeBuilder.getTree();
    myUpdateQueue = new MergingUpdateQueue("UpdateQueue", 300, tree.isShowing(), tree);
    myDisposable = new UiNotifyConnector(tree, myUpdateQueue);
    //TODO
  }

  /**
   * @param delay update delay in milliseconds.
   */
  public void setDelay(int delay) {
    myUpdateQueue.setMergingTimeSpan(delay);
  }

  public void dispose() {
    myDisposable.dispose();
    myUpdateQueue.dispose();
  }

  public void addSubtreeToUpdate(DefaultMutableTreeNode rootNode) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdate:" + rootNode);
    }

    for (Iterator<DefaultMutableTreeNode> iterator = myNodesToUpdate.iterator(); iterator.hasNext();) {
      DefaultMutableTreeNode node = iterator.next();
      if (rootNode.isNodeAncestor(node)){
        return;
      }
      else if (node.isNodeAncestor(rootNode)){
        iterator.remove();
      }
    }
    myNodesToUpdate.add(rootNode);

    //noinspection HardCodedStringLiteral
    myUpdateQueue.queue(new Update("ViewUpdate") {
      public boolean isExpired() {
        return myTreeBuilder.isDisposed();
      }

      public void run() {
        if (myTreeBuilder.getTreeStructure().hasSomethingToCommit()) {
          myUpdateQueue.queue(this);
          return;
        }
        myTreeBuilder.getTreeStructure().commit();
        try {
          performUpdate();
        }
        catch(RuntimeException e) {
          LOG.error(myTreeBuilder.getClass().getName(), e);
        }
      }
    });
  }

  protected void updateSubtree(DefaultMutableTreeNode node) {
    myTreeBuilder.updateSubtree(node);
  }

  public void performUpdate() {
    if (myRunBeforeUpdate != null){
      myRunBeforeUpdate.run();
    }

    while(myNodesToUpdate.size() > 0){
      DefaultMutableTreeNode node = myNodesToUpdate.removeFirst();
      updateSubtree(node);
    }

    if (myRunAfterUpdate != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            synchronized (this) {
              if (myRunAfterUpdate != null) {
                myRunAfterUpdate.run();
                myRunAfterUpdate = null;
              }
            }
          }
        });
    }
  }

  public boolean addSubtreeToUpdateByElement(Object element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdateByElement:" + element);
    }

    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node != null){
      addSubtreeToUpdate(node);
      return true;
    }
    else{
      return false;
    }
  }

  public boolean hasRequestsForUpdate() {
    return myUpdateQueue.containsUpdateOf(Update.LOW_PRIORITY);
  }

  public void cancelAllRequests(){
    myNodesToUpdate.clear();
    myUpdateQueue.cancelAllUpdates();
  }

  public synchronized void runAfterUpdate(final Runnable runnable) {
    myRunAfterUpdate = runnable;
  }

  public synchronized void runBeforeUpdate(final Runnable runnable) {
    myRunBeforeUpdate = runnable;
  }
}
