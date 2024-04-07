package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.openapi.diagnostic.Logger;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class CompoundReferenceRenderer extends CompoundNodeRenderer{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer");

  public CompoundReferenceRenderer(final NodeRendererSettings rendererSettings, String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer) {
    super(rendererSettings, name, labelRenderer, childrenRenderer);
    myProperties.setClassName("java.lang.Object");
    LOG.assertTrue(labelRenderer == null || labelRenderer instanceof ReferenceRenderer);
    LOG.assertTrue(childrenRenderer == null || childrenRenderer instanceof ReferenceRenderer);
  }

  public void setLabelRenderer(ValueLabelRenderer labelRenderer) {
    final ValueLabelRenderer prevRenderer = getLabelRenderer();
    super.setLabelRenderer(myRendererSettings.isBase(labelRenderer) ? null : labelRenderer);
    final ValueLabelRenderer currentRenderer = getLabelRenderer();
    if (prevRenderer != currentRenderer) {
      if (currentRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)currentRenderer).setClassName(getClassName());
      }
    }
  }

  public void setChildrenRenderer(ChildrenRenderer childrenRenderer) {
    final ChildrenRenderer prevRenderer = getChildrenRenderer();
    super.setChildrenRenderer(myRendererSettings.isBase(childrenRenderer) ? null : childrenRenderer);
    final ChildrenRenderer currentRenderer = getChildrenRenderer();
    if (prevRenderer != currentRenderer) {
      if (currentRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)currentRenderer).setClassName(getClassName());
      }
    }
  }

  public ChildrenRenderer getChildrenRenderer() {
    final ChildrenRenderer childrenRenderer = super.getChildrenRenderer();
    return childrenRenderer != null ? childrenRenderer : getDefaultRenderer();
  }

  private NodeRenderer getDefaultRenderer() {
    return  getClassName().endsWith("]") ? (NodeRenderer)myRendererSettings.getArrayRenderer() : (NodeRenderer)myRendererSettings.getClassRenderer();
  }

  public ValueLabelRenderer getLabelRenderer() {
    final ValueLabelRenderer labelRenderer = super.getLabelRenderer();
    return labelRenderer != null ? labelRenderer : getDefaultRenderer();
  }

  private ChildrenRenderer getRawChildrenRenderer() {
    NodeRenderer classRenderer = getDefaultRenderer();
    return myChildrenRenderer == classRenderer ? null : myChildrenRenderer;
  }

  private ValueLabelRenderer getRawLabelRenderer() {
    NodeRenderer classRenderer = getDefaultRenderer();
    return myLabelRenderer == classRenderer ? null : myLabelRenderer;
  }

  public void setClassName(String name) {
    LOG.assertTrue(name != null);
    myProperties.setClassName(name);
    if(getRawLabelRenderer() != null) {
      if (myLabelRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)myLabelRenderer).setClassName(name);
      }
    }

    if(getRawChildrenRenderer() != null) {
      if (myChildrenRenderer instanceof ReferenceRenderer) {
        ((ReferenceRenderer)myChildrenRenderer).setClassName(name);
      }
    }
  }

  public String getClassName() {
    return myProperties.getClassName();
  }
}
