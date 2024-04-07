package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.RootProvider;
import com.intellij.util.EventDispatcher;

/**
 *  @author dsl
 */
public abstract class RootProviderBaseImpl implements RootProvider {
  private EventDispatcher<RootSetChangedListener> myDispatcher = EventDispatcher.create(RootSetChangedListener.class);
  public void addRootSetChangedListener(RootSetChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeRootSetChangedListener(RootSetChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  protected void fireRootSetChanged() {
    myDispatcher.getMulticaster().rootSetChanged(this);
  }

}
