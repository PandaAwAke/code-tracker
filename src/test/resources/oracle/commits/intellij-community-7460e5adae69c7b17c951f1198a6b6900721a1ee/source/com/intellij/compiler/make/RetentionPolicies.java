package com.intellij.compiler.make;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 7, 2004
 */
public interface RetentionPolicies {
  /**
   * Annotations are to be discarded by the compiler.
   */
  int SOURCE = 0x1;

  /**
   * Annotations are to be recorded in the class file by the compiler
   * but need not be retained by the VM at run time.  This is the default
   * behavior.
   */
  int CLASS = 0x2;

  /**
   * Annotations are to be recorded in the class file by the compiler and
   * retained by the VM at run time, so they may be read reflectively.
   */
  int RUNTIME = 0x4;

  int ALL = SOURCE | CLASS | RUNTIME;
}
