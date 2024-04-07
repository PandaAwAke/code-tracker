package com.intellij.debugger.engine.events;

import com.intellij.debugger.impl.DebuggerTaskImpl;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 5, 2004
 * Time: 6:04:07 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class DebuggerCommandImpl extends DebuggerTaskImpl {
  protected abstract void action() throws Exception;

  protected void commandCancelled() {
  }

  public Priority getPriority() {
    return Priority.LOW;
  }

  public final void notifyCancelled() {
    try {
      commandCancelled();
    }
    finally {
      release();
    }
  }

  public final void run() throws Exception{
    try {
      action();
    }
    finally {
      release();
    }
  }
}
