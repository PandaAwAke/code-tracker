package com.intellij.compiler;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.StringTokenizer;

public class JikesSettings implements JDOMExternalizable, ProjectComponent {
  public String JIKES_PATH = "";
  public boolean DEBUGGING_INFO = true;
  public boolean DEPRECATION = true;
  public boolean GENERATE_NO_WARNINGS = false;
  public boolean IS_EMACS_ERRORS_MODE = true;

  public String ADDITIONAL_OPTIONS_STRING = "";

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public @NonNls String getOptionsString() {
    StringBuffer options = new StringBuffer();
    if(DEBUGGING_INFO) {
      options.append("-g ");
    }
    if(DEPRECATION) {
      options.append("-deprecation ");
    }
    if(GENERATE_NO_WARNINGS) {
      options.append("-nowarn ");
    }
    /*
    if(IS_INCREMENTAL_MODE) {
      options.append("++ ");
    }
    */
    if(IS_EMACS_ERRORS_MODE) {
      options.append("+E ");
    }

    StringTokenizer tokenizer = new StringTokenizer(ADDITIONAL_OPTIONS_STRING, " \t\r\n");
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if("-g".equals(token)) {
        continue;
      }
      if("-deprecation".equals(token)) {
        continue;
      }
      if("-nowarn".equals(token)) {
        continue;
      }
      if("++".equals(token)) {
        continue;
      }
      if("+M".equals(token)) {
        continue;
      }
      if("+F".equals(token)) {
        continue;
      }
      if("+E".equals(token)) {
        continue;
      }
      options.append(token);
      options.append(" ");
    }
    return options.toString();
  }

  public static JikesSettings getInstance(Project project) {
    return project.getComponent(JikesSettings.class);
  }

  public String getComponentName() {
    return "JikesSettings";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}