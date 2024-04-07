package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.apiAdapters.TransportServiceWrapper;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.GetJPDADialog;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.jar.Attributes;

public class DebuggerManagerImpl extends DebuggerManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerManagerImpl");
  private final Project myProject;
  private HashMap<ProcessHandler, DebuggerSession> mySessions = new HashMap<ProcessHandler, DebuggerSession>();
  private BreakpointManager myBreakpointManager;

  private final EventDispatcher<DebuggerManagerListener> myDispatcher = EventDispatcher.create(DebuggerManagerListener.class, false);
  private final MyDebuggerStateManager myDebuggerStateManager = new MyDebuggerStateManager();

  private DebuggerContextListener mySessionListener = new DebuggerContextListener() {
    public void changeEvent(DebuggerContextImpl newContext, int event) {

      if (event == DebuggerSession.EVENT_PAUSE && myDebuggerStateManager.myDebuggerSession != newContext.getDebuggerSession()) {
        // if paused in non-active session; switch current session
        myDebuggerStateManager.setState(newContext, newContext.getDebuggerSession().getState(), event, null);
        return;
      }

      if(myDebuggerStateManager.myDebuggerSession == newContext.getDebuggerSession()) {
        myDebuggerStateManager.fireStateChanged(newContext, event);
      }

      if(event == DebuggerSession.EVENT_DISPOSE) {
        dispose(newContext.getDebuggerSession());
        if(myDebuggerStateManager.myDebuggerSession == newContext.getDebuggerSession()) {
          myDebuggerStateManager.setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_DISPOSE, null);
        }
      }
    }
  };
  private static final @NonNls String DEBUG_KEY_NAME = "idea.xdebug.key";

  public void addDebuggerManagerListener(DebuggerManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeDebuggerManagerListener(DebuggerManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public DebuggerManagerImpl(Project project, StartupManager startupManager) {
    myProject = project;
    myBreakpointManager = new BreakpointManager(myProject, startupManager, this);
  }

  public DebuggerSession getSession(DebugProcess process) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (Iterator iterator = getSessions().iterator(); iterator.hasNext();) {
      DebuggerSession debuggerSession = (DebuggerSession)iterator.next();
      if (process == debuggerSession.getProcess()) return debuggerSession;
    }
    return null;
  }

  public Collection<DebuggerSession> getSessions() {
    return mySessions.values();
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void projectClosed() {
    myBreakpointManager.dispose();
  }

  public void projectOpened() {
    myBreakpointManager.init();
  }

  public void readExternal(Element element) throws InvalidDataException {
    myBreakpointManager.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myBreakpointManager.writeExternal(element);
  }


  public DebuggerSession attachVirtualMachine(String sessionName, RunProfileState state,
                                              RemoteConnection remoteConnection,
                                              boolean pollConnection) throws ExecutionException {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    DebuggerSession session = new DebuggerSession(sessionName, new DebugProcessEvents(myProject));

    final ExecutionResult executionResult = session.attach(state, remoteConnection, pollConnection);

    session.getContextManager().addListener(mySessionListener);
    getContextManager().setState(DebuggerContextUtil.createDebuggerContext(session, session.getContextManager().getContext().getSuspendContext()), session.getState(), DebuggerSession.EVENT_REFRESH, null);

    final ProcessHandler processHandler = executionResult.getProcessHandler();
    
    synchronized (mySessions) {
      mySessions.put(processHandler, session);
    }
    if (!(processHandler instanceof RemoteDebugProcessHandler)) {
      // add listener only to non-remote process handler:
      // on Unix systems destroying process does not cause VMDeathEvent to be generated,
      // so we need to call debugProcess.stop() explicitly for graceful termination.
      // RemoteProcessHandler on the other hand will call debugProcess.stop() as a part of destroyProcess() and detachProcess() implementation,
      // so we shouldn't add the listener to avoid calling stop() twice
      processHandler.addProcessListener(new ProcessAdapter() {
        public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
          final DebugProcessImpl debugProcess = getDebugProcess(event.getProcessHandler());
          if (debugProcess != null) {
            // if current thread is a "debugger manager thread", stop will execute synchronously
            debugProcess.stop(willBeDestroyed);

            // wait at most 10 seconds: the problem is that debugProcess.stop() can hang if there are troubles in the debuggee
            // if processWillTerminate() is called from AWT thread debugProcess.waitFor() will block it and the whole app will hang
            if (!DebuggerManagerThreadImpl.isManagerThread()) {
              debugProcess.waitFor(10000);
            }
          }
        }

      });
    }
    myDispatcher.getMulticaster().sessionCreated(session);
    return session;
  }


  public DebugProcessImpl getDebugProcess(final ProcessHandler processHandler) {
    synchronized (mySessions) {
      DebuggerSession session = mySessions.get(processHandler);
      return session != null ? session.getProcess() : null;
    }
  }

  public void addDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.addDebugProcessListener(listener);
    }
    else {
      processHandler.addProcessListener(new ProcessAdapter() {
        public void startNotified(ProcessEvent event) {
          DebugProcessImpl debugProcess = getDebugProcess(processHandler);
          if (debugProcess != null) {
            debugProcess.addDebugProcessListener(listener);
          }
          processHandler.removeProcessListener(this);
        }
      });
    }
  }

  public void removeDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.removeDebugProcessListener(listener);
    }
    else {
      processHandler.addProcessListener(new ProcessAdapter() {
        public void startNotified(ProcessEvent event) {
          DebugProcessImpl debugProcess = getDebugProcess(processHandler);
          if (debugProcess != null) {
            debugProcess.removeDebugProcessListener(listener);
          }
          processHandler.removeProcessListener(this);
        }
      });
    }
  }

  public boolean isDebuggerManagerThread() {
    return DebuggerManagerThreadImpl.isManagerThread();
  }

  public String getComponentName() {
    return "DebuggerManager";
  }

  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  public DebuggerContextImpl getContext() { return getContextManager().getContext(); }

  public DebuggerStateManager getContextManager() { return myDebuggerStateManager;}

  static private boolean hasWhitespace(String string) {
    int length = string.length();
    for (int i = 0; i < length; i++) {
      if (Character.isWhitespace(string.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /* Remoting */
  private static void checkTargetJPDAInstalled(JavaParameters parameters) throws ExecutionException {
    final ProjectJdk jdk = parameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(DebuggerBundle.message("error.jdk.not.specified"));
    }
    final String versionString = jdk.getVersionString();
    if (versionString.indexOf("1.0") > -1 || versionString.indexOf("1.1") > -1) {
      throw new ExecutionException(DebuggerBundle.message("error.unsupported.jdk.version", versionString));
    }
    if (SystemInfo.isWindows && versionString.indexOf("1.2") > -1) {
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null || !homeDirectory.isValid()) {
        throw new ExecutionException(DebuggerBundle.message("error.invalid.jdk.home", versionString));
      }
      //noinspection HardCodedStringLiteral
      File dllFile = new File(
        homeDirectory.getPath().replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "jdwp.dll"
      );
      if (!dllFile.exists()) {
        GetJPDADialog dialog = new GetJPDADialog();
        dialog.show();
        throw new ExecutionException(DebuggerBundle.message("error.debug.libraries.missing"));
      }
    }
  }

  /**
   * for Target JDKs versions 1.2.x - 1.3.0 the Classic VM should be used for debugging
   */
  private static boolean shouldForceClassicVM(ProjectJdk jdk) {
    if (SystemInfo.isMac) {
      return false;
    }
    if (jdk == null) return false;

    String version = PathUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (version != null) {
      if (version.compareTo("1.4") >= 0) {
        return false;
      }
      if (version.startsWith("1.2") && SystemInfo.isWindows) {
        return true;
      }
      version = version + ".0";
      if (version.startsWith("1.3.0") && SystemInfo.isWindows) {
        return true;
      }
      if ((version.startsWith("1.3.1_07") || version.startsWith("1.3.1_08")) && SystemInfo.isWindows) {
        return false; // fixes bug for these JDKs that it cannot start with -classic option
      }
    }

    return DebuggerSettings.getInstance().FORCE_CLASSIC_VM;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean serverMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity)
    throws ExecutionException {
    if (checkValidity) {
      checkTargetJPDAInstalled(parameters);
    }

    final boolean useSockets = transport == DebuggerSettings.SOCKET_TRANSPORT;

    String address  = "";
    if (debugPort == null || "".equals(debugPort)) {
      try {
        address = DebuggerUtils.getInstance().findAvailableDebugAddress(useSockets);
      }
      catch (ExecutionException e) {
        if (checkValidity) {
          throw e;
        }
      }
    }
    else {
      address = debugPort;
    }

    String listenTo = null;
    if(serverMode && useSockets) {
      try {
        listenTo = InetAddress.getLocalHost().getHostName() + ":" + address;
      }
      catch (UnknownHostException e) {
        listenTo = "localhost:" + address;
      }
    }
    else {
      listenTo = address;
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void run() {
        PathUtilEx.addRtJar(parameters.getClassPath());
        boolean classicVM = shouldForceClassicVM(parameters.getJdk());
        parameters.getVMParametersList().replaceOrPrepend("-classic", classicVM ? "-classic" : "");

        final String debugKey = System.getProperty(DEBUG_KEY_NAME, "-Xdebug");
        parameters.getVMParametersList().replaceOrAppend(debugKey, debugKey);

        if (shouldForceNoJIT(parameters.getJdk())) {
          parameters.getVMParametersList().replaceOrAppend("-Xnoagent", "-Xnoagent");
          parameters.getVMParametersList().replaceOrAppend("-Djava.compiler=", "-Djava.compiler=NONE");
        }
      }
    });

    final TransportServiceWrapper transportService = TransportServiceWrapper.getTransportService(useSockets);
    String xrun = "transport=" + transportService.transportId() + ",address=" + listenTo;
    if(serverMode) {
      xrun += ",suspend=y,server=n";
    }
    else {
      xrun += ",suspend=n,server=y";
    }

    if (hasWhitespace(xrun)) {
      xrun = "\"" + xrun + "\"";
    }
    parameters.getVMParametersList().replaceOrAppend("-Xrunjdwp:", "-Xrunjdwp:" + xrun);

    return new RemoteConnection(useSockets, "127.0.0.1", address, serverMode);
  }

  private static boolean shouldForceNoJIT(ProjectJdk jdk) {
    if (jdk == null) return true;

    String version = PathUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);

    if (version == null) return true;

    return version.startsWith("1.2") || version.startsWith("1.3");
  }

  public static RemoteConnection createDebugParameters(final JavaParameters parameters, GenericDebuggerRunnerSettings settings, boolean checkValidity)
    throws ExecutionException {
    return createDebugParameters(parameters, settings.LOCAL, settings.getTransport(), settings.DEBUG_PORT, checkValidity);

  }

  private static class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerSession myDebuggerSession;

    public DebuggerContextImpl getContext() {
      return myDebuggerSession == null ? DebuggerContextImpl.EMPTY_CONTEXT : myDebuggerSession.getContextManager().getContext();
    }

    public void setState(final DebuggerContextImpl context, int state, int event, String description) {
      LOG.assertTrue(SwingUtilities.isEventDispatchThread());
      myDebuggerSession = context.getDebuggerSession();
      if (myDebuggerSession != null) {
        myDebuggerSession.getContextManager().setState(context, state, event, description);
      }
      else {
        fireStateChanged(context, event);
      }
    }
  }

  private void dispose(DebuggerSession session) {
    ProcessHandler processHandler = session.getProcess().getExecutionResult().getProcessHandler();

    synchronized (mySessions) {
      DebuggerSession removed = mySessions.remove(processHandler);
      LOG.assertTrue(removed != null);
      myDispatcher.getMulticaster().sessionRemoved(session);
    }

  }
}