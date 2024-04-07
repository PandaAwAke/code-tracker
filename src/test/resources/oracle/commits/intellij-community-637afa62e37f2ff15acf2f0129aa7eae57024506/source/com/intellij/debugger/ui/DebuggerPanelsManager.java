package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.ui.impl.MainWatchPanel;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RemoteState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentListener;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.HashMap;

import org.jetbrains.annotations.Nullable;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerPanelsManager implements ProjectComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.DebuggerPanelsManager");

  private final Project myProject;

  private PositionHighlighter myEditorManager;
  private HashMap<ProcessHandler, DebuggerSessionTab> mySessionTabs = new HashMap<ProcessHandler, DebuggerSessionTab>();

  public DebuggerPanelsManager(Project project) {
    myProject = project;
  }

  private DebuggerStateManager getContextManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContextManager();
  }

  private final RunContentListener myContentListener = new RunContentListener() {
    public void contentSelected(RunContentDescriptor descriptor) {
      DebuggerSessionTab sessionTab = descriptor != null ? getSessionTab(descriptor.getProcessHandler()) : null;

      if (sessionTab != null) {
        getContextManager().setState(sessionTab.getContextManager().getContext(), sessionTab.getSession().getState(), DebuggerSession.EVENT_CONTEXT, null);
      }
      else {
        getContextManager().setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_CONTEXT, null);
      }
    }

    public void contentRemoved(RunContentDescriptor descriptor) {
      DebuggerSessionTab sessionTab = getSessionTab(descriptor.getProcessHandler());
      if (sessionTab != null) {
        mySessionTabs.remove(descriptor.getProcessHandler());
        sessionTab.dispose();
      }
    }
  };

  public @Nullable RunContentDescriptor attachVirtualMachine(RunProfile runProfile,
                                                   JavaProgramRunner runner,
                                                   RunProfileState state,
                                                   RunContentDescriptor reuseContent,
                                                   RemoteConnection remoteConnection,
                                                   boolean pollConnection) throws ExecutionException {

    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(runProfile.getName(), state, remoteConnection, pollConnection);
    if (debuggerSession == null) {
      return null;
    }

    if (state instanceof RemoteState) {
      // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
      // which is an expensive oparation when executed first time
      debuggerSession.getProcess().putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
    }

    final DebuggerSessionTab sessionTab = new DebuggerSessionTab(myProject);
    RunContentDescriptor runContentDescriptor = sessionTab.attachToSession(
        debuggerSession,
        runner,
        runProfile,
        state.getRunnerSettings(),
        state.getConfigurationSettings()
      );
    if (reuseContent != null) {
      final ProcessHandler prevHandler = reuseContent.getProcessHandler();
      if (prevHandler != null) {
        final DebuggerSessionTab prevSession = mySessionTabs.get(prevHandler);
        if (prevSession != null) {
          sessionTab.reuse(prevSession);
        }
      }
    }
    mySessionTabs.put(runContentDescriptor.getProcessHandler(), sessionTab);
    return runContentDescriptor;
  }


  public void projectOpened() {
    myEditorManager = new PositionHighlighter(myProject, getContextManager());
    getContextManager().addListener(new DebuggerContextListener() {
      public void changeEvent(final DebuggerContextImpl newContext, int event) {
        if(event == DebuggerSession.EVENT_PAUSE) {
          DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
            public void run() {
              toFront(newContext.getDebuggerSession());
            }
          });
        }
      }
    });

    RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
    LOG.assertTrue(contentManager != null, "Content manager is null");
    contentManager.addRunContentListener(myContentListener, GenericDebuggerRunner.getRunnerInfo());
  }

  public void projectClosed() {
    ExecutionManager.getInstance(myProject).getContentManager().removeRunContentListener(myContentListener);
  }

  public String getComponentName() {
    return "DebuggerPanelsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static DebuggerPanelsManager getInstance(Project project) {
    return project.getComponent(DebuggerPanelsManager.class);
  }

  public MainWatchPanel getWatchPanel() {
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
    DebuggerSessionTab sessionTab = getSessionTab(context.getDebuggerSession());
    return sessionTab != null ? sessionTab.getWatchPanel() : null;
  }

  public void showFramePanel() {
    DebuggerContextImpl context = DebuggerManagerEx.getInstanceEx(myProject).getContext();
    DebuggerSessionTab sessionTab = getSessionTab(context.getDebuggerSession());
    if(sessionTab != null) {
      sessionTab.showFramePanel();
    }
  }

  public void toFront(DebuggerSession session) {
    DebuggerSessionTab sessionTab = getSessionTab(session);
    if(sessionTab != null) {
      sessionTab.toFront();
    }
  }

  private DebuggerSessionTab getSessionTab(ProcessHandler processHandler) {
    return mySessionTabs.get(processHandler);
  }

  private DebuggerSessionTab getSessionTab(DebuggerSession session) {
    return session != null ? getSessionTab(session.getProcess().getExecutionResult().getProcessHandler()) : null;
  }

  public void updateContextPointDescription() {
    myEditorManager.updateContextPointDescription();
  }
}
