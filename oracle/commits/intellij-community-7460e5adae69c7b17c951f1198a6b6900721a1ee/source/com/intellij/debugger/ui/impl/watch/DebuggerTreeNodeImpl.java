/*
 * Class DebuggerTreeNodeImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import javax.swing.tree.MutableTreeNode;
import java.util.Map;

public class DebuggerTreeNodeImpl extends TreeBuilderNode implements DebuggerTreeNode{
  private Icon myIcon;
  private SimpleColoredText myText;
  private DebuggerTree myTree;
  private Map myProperties = new HashMap();
  protected boolean myIsStateRestored = false;

  private DebuggerTreeNodeImpl(DebuggerTree tree, NodeDescriptor descriptor) {
    super(descriptor);
    myTree = tree;
  }

  public DebuggerTreeNodeImpl getParent() {
    return (DebuggerTreeNodeImpl) super.getParent();
  }

  protected TreeBuilder getTreeBuilder() {
    return myTree.getMutableModel();
  }

  public DebuggerTree getTree() {
    return (DebuggerTree) getTreeBuilder().getUserObject();
  }

  public String toString() {
    NodeDescriptorImpl descriptor = getDescriptor();
    return descriptor != null? descriptor.toString() : "";
  }

  public NodeDescriptorImpl getDescriptor() {
    return (NodeDescriptorImpl)getUserObject();
  }

  public Project getProject() {
    return getTree().getProject();
  }

  public void setRenderer(NodeRenderer renderer) {
    ((ValueDescriptorImpl) getDescriptor()).setRenderer(renderer);
    calcRepresentation();
  }

  private void updateCaches() {
    final NodeDescriptorImpl descriptor = getDescriptor();
    myIcon = DebuggerTreeRenderer.getDescriptorIcon(descriptor);
    myText = DebuggerTreeRenderer.getDescriptorText(descriptor, false);
  }

  public Icon getIcon() {
    return myIcon;
  }

  public SimpleColoredText getText() {
    return myText;
  }

  public void clear() {
    super.clear();
    myIcon = null;
    myText = null;
  }

  private void update(final DebuggerContextImpl context, final Runnable runnable, boolean labelOnly) {
    if(!labelOnly) clear();

    if(context != null && context.getDebugProcess() != null) {
      getTree().saveState(this);

      myIcon = DebuggerTreeRenderer.getDescriptorIcon(MessageDescriptor.EVALUATING);
      myText = DebuggerTreeRenderer.getDescriptorText(MessageDescriptor.EVALUATING, false);

      context.getDebugProcess().getManagerThread().invoke(new DebuggerContextCommandImpl(context) {
        public void threadAction() {
          runnable.run();
        }

        protected void commandCancelled() {
          clear();
          getDescriptor().clear();
          updateCaches();

          labelChanged();
          childrenChanged();
        }
      });
    }

    labelChanged();
    if(!labelOnly) childrenChanged();
  }

  public void calcLabel() {
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    update(
      context,
      new Runnable() {
        public void run() {
          getDescriptor().updateRepresentation(context.createEvaluationContext(), new DescriptorLabelListener() {
            public void labelChanged() {
              updateCaches();
              DebuggerTreeNodeImpl.this.labelChanged();
            }
          });
        }
      }, true);
  }

  public void calcRepresentation() {
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    update(
      context,
      new Runnable() {
        public void run() {
          getDescriptor().updateRepresentation(context.createEvaluationContext(), new DescriptorLabelListener() {
            public void labelChanged() {
              updateCaches();
              DebuggerTreeNodeImpl.this.labelChanged();
            }
          });
        }
      }, false);
  }

  public void calcValue() {
    final DebuggerContextImpl context = getTree().getDebuggerContext();
    update(
      context,
      new Runnable() {
        public void run() {
          EvaluationContextImpl evaluationContext = context.createEvaluationContext();
          getDescriptor().setContext(evaluationContext);
          getDescriptor().updateRepresentation(evaluationContext, new DescriptorLabelListener() {
            public void labelChanged() {
              updateCaches();
              DebuggerTreeNodeImpl.this.labelChanged();
            }
          });
          DebuggerTreeNodeImpl.this.childrenChanged();
        }
      }, false);
  }

  private void invoke(Runnable r) {
    if(SwingUtilities.isEventDispatchThread()) {
      r.run();
    }
    else {
      SwingUtilities.invokeLater(r);
    }
  }

  public void labelChanged() {
    invoke(new Runnable() {
      public void run() {
        updateCaches();
        getTree().getMutableModel().nodeChanged(DebuggerTreeNodeImpl.this);
      }
    });
  }

  public void childrenChanged() {
    invoke(new Runnable() {
      public void run() {
        getTree().getMutableModel().nodeStructureChanged(DebuggerTreeNodeImpl.this);
        getTree().restoreState(DebuggerTreeNodeImpl.this);
      }
    });
  }

  public void add(MutableTreeNode newChild) {
    super.add(newChild);    //To change body of overriden methods use Options | File Templates.
  }

  public DebuggerTreeNodeImpl add(MessageDescriptor message) {
    DebuggerTreeNodeImpl node = getNodeFactory().createMessageNode(message);
    add(node);
    return node;
  }

  public NodeManagerImpl getNodeFactory() {
    return myTree.getNodeFactory();
  }

  public Object getProperty(Key key) {
    return myProperties.get(key);
  }

  public void putProperty(Key key, Object data) {
    myProperties.put(key, data);
  }

  public static DebuggerTreeNodeImpl createNodeNoUpdate(DebuggerTree tree, NodeDescriptor descriptor) {
    DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(tree, descriptor);
    node.updateCaches();
    return node;
  }

  protected static DebuggerTreeNodeImpl createNode(DebuggerTree tree, NodeDescriptorImpl descriptor, EvaluationContextImpl evaluationContext) {
    final DebuggerTreeNodeImpl node = new DebuggerTreeNodeImpl(tree, descriptor);
    descriptor.updateRepresentationNoNotify(evaluationContext, new DescriptorLabelListener() {
      public void labelChanged() {
        node.updateCaches();
        node.labelChanged();
      }
    });
    node.updateCaches();
    return node;
  }
}