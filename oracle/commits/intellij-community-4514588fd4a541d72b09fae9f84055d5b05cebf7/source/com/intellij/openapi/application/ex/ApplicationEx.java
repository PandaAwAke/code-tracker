package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;

import java.io.IOException;

/**
 * @author max
 */
public interface ApplicationEx extends Application {
  /**
   * Loads the application configuration from the specified path
   *
   * @param optionsPath Path to /config folder
   * @throws IOException
   * @throws InvalidDataException
   */
  void load(String optionsPath) throws IOException, InvalidDataException;

  boolean isInternal();

  boolean isAspectJSupportEnabled();

  String getComponentsDescriptor();

  String getName();

  void dispose();

  void assertReadAccessToDocumentsAllowed();

  void doNotSave();

  boolean runProcessWithProgressSynchronously(Runnable process,
                                                     String progressTitle,
                                                     boolean canBeCanceled,
                                                     Project project,
                                                     boolean smoothProgress);

  //used in Fabrique 
  boolean isExceptionalThreadWithReadAccess(Thread thread);

  //force exit
  void exit(boolean force);
}
