/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 16, 2002
 * Time: 5:17:44 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationInfo;

public abstract class ApplicationInfoEx extends ApplicationInfo {

  public static final String COMPONENT_NAME = "ApplicationInfo";

  public static final boolean IS_EAP = false;

  public abstract String getLogoUrl();

  public abstract String getAboutLogoUrl();

  public abstract String getPackageCode();

  public abstract String getFullApplicationName();

  public abstract boolean showLicenseeInfo();
}
