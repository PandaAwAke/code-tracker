/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 15, 2001
 * Time: 5:17:38 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;



public class RefPackage extends RefEntity {
  private final String myQualifiedName;

  public RefPackage(String name) {
    super(getPackageSuffix(name));
    myQualifiedName = name;
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  private static String getPackageSuffix(String fullName) {
    int dotIndex = fullName.lastIndexOf('.');
    return (dotIndex >= 0) ? fullName.substring(dotIndex + 1) : fullName;
  }
}
