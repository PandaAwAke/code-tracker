package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 6, 2003
 * Time: 4:05:51 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DropAnErrorAction extends AnAction {
  public DropAnErrorAction() {
    super ("Drop an error");
  }

  public void actionPerformed(AnActionEvent e) {
    Logger.getInstance("test").error("Test");
  }
}
