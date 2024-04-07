package com.intellij.openapi.command.impl;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomModel;
import com.intellij.util.ArrayUtil;
import org.picocontainer.PicoContainer;

/**
 * @author max
 */
public class DummyProject extends UserDataHolderBase implements Project {
  private static DummyProject ourInstance;

  public static Project getInstance() {
    if (ourInstance == null) {
      ourInstance = new DummyProject();
    }
    return ourInstance;
  }

  private DummyProject() {
  }

  public VirtualFile getProjectFile() {
    return null;
  }

  public String getName() {
    return null;
  }

  public String getProjectFilePath() {
    return null;
  }

  public VirtualFile getWorkspaceFile() {
    return null;
  }

  public void save() {
  }

  public BaseComponent getComponent(String name) {
    return null;
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    return null;
  }

  public Class[] getComponentInterfaces() {
    return new Class[0];
  }

  public boolean hasComponent(Class interfaceClass) {
    return false;
  }

  public <T> T[] getComponents(Class<T> baseInterfaceClass) {
    return ArrayUtil.<T>emptyArray();
  }

  public PicoContainer getPicoContainer() {
    throw new UnsupportedOperationException("getPicoContainer is not implement in : " + getClass());
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    return null;
  }

  public boolean isDisposed() {
    return false;
  }

  public boolean isOpen() {
    return false;
  }

  public boolean isInitialized() {
    return false;
  }

  public boolean isDefault() {
    return false;
  }

  public PomModel getModel() {
    return null;
  }
}
