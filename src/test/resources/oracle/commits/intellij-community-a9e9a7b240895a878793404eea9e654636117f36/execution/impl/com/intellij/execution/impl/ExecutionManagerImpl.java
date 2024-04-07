package com.intellij.execution.impl;

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.execution.runners.RunStrategyImpl;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author dyoma
 */
public class ExecutionManagerImpl extends ExecutionManager implements ProjectComponent {
  public static final Key<RunProfileState> RUN_PROFILE_STATE_KEY = new Key<RunProfileState>("RUN_PROFILE_STATE_KEY");
  private final Project myProject;

  private RunContentManagerImpl myContentManager;
  @NonNls
  protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

  /**
   * reflection
   */
  ExecutionManagerImpl(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    ((RunContentManagerImpl)getContentManager()).init();
  }

  public void projectClosed() {
    myContentManager.dispose();
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public RunContentManager getContentManager() {
    if (myContentManager == null) {
      myContentManager = new RunContentManagerImpl(myProject);
    }
    return myContentManager;
  }

  public ProcessHandler[] getRunningProcesses() {
    final List<ProcessHandler> handlers = new ArrayList<ProcessHandler>();
    RunContentDescriptor[] descriptors = myContentManager.getAllDescriptors();
    for (RunContentDescriptor descriptor : descriptors) {
      if (descriptor != null) {
        final ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null) {
          handlers.add(processHandler);
        }
      }
    }
    return handlers.toArray(new ProcessHandler[handlers.size()]);
  }

  public void compileAndRun(final Runnable startRunnable,
                            final RunProfile configuration,
                            final RunProfileState state) {
    final Runnable antAwareRunnable = new Runnable() {
      public void run() {
        final AntConfiguration antConfiguration = AntConfiguration.getInstance(myProject);
        if (configuration instanceof RunConfiguration) {
          final RunConfiguration runConfiguration = (RunConfiguration)configuration;
          final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
          final Map<String, Boolean> beforeRun = runManager.getStepsBeforeLaunch(runConfiguration);
          final Boolean enabled = beforeRun.get(AntConfiguration.ANT);

          if (enabled != null && enabled.booleanValue() && antConfiguration != null &&
              antConfiguration.hasTasksToExecuteBeforeRun(runConfiguration)) {
            ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
              public void run() {
                final DataContext dataContext = SimpleDataContext.getProjectContext(myProject);
                if (antConfiguration.executeTaskBeforeRun(dataContext, runConfiguration)) {
                  ApplicationManager.getApplication().invokeLater(startRunnable);
                }
              }
            });
          }
          else {
            startRunnable.run();
          }
        }
        else {
          startRunnable.run();
        }
      }
    };
    Module[] modulesToCompile = state.getModulesToCompile();
    if (modulesToCompile == null) modulesToCompile = Module.EMPTY_ARRAY;
    if (getConfig().isCompileBeforeRunning(configuration) && modulesToCompile.length > 0) {
      final CompileStatusNotification callback = new CompileStatusNotification() {
        public void finished(final boolean aborted, final int errors, final int warnings, CompileContext compileContext) {
          if (errors == 0 && !aborted) {
            ApplicationManager.getApplication().invokeLater(antAwareRunnable);
          }
        }
      };
      CompileScope scope;
      if (Boolean.valueOf(System.getProperty(MAKE_PROJECT_ON_RUN_KEY, Boolean.FALSE.toString())).booleanValue()) {
        // user explicitly requested whole-project make
        scope = new ProjectCompileScope(myProject);
      }
      else {
        scope = new ModuleCompileScope(myProject, modulesToCompile, true);
      }
      scope.putUserData(RUN_PROFILE_STATE_KEY, state);
      CompilerManager.getInstance(myProject).make(scope, callback);
    }
    else {
      antAwareRunnable.run();
    }
  }

  public void execute(JavaParameters cmdLine,
                      String contentName,
                      final DataContext dataContext) throws ExecutionException {
    execute(cmdLine, contentName, dataContext, null);
  }

  public void execute(JavaParameters cmdLine, String contentName, DataContext dataContext, Filter[] filters) throws ExecutionException {
    JavaProgramRunner defaultRunner = ExecutionRegistry.getInstance().getDefaultRunner();
    RunStrategyImpl.getInstance().execute(new DefaultRunProfile(cmdLine, contentName, filters), dataContext, defaultRunner, null, null);
  }

  private final class DefaultRunProfile implements RunProfile {
    private JavaParameters myParameters;
    private String myContentName;
    private Filter[] myFilters;

    public DefaultRunProfile(final JavaParameters parameters, String contentName, Filter[] filters) {
      myParameters = parameters;
      myContentName = contentName;
      myFilters = filters;
    }

    public RunProfileState getState(DataContext context,
                                    RunnerInfo runnerInfo,
                                    RunnerSettings runnerSettings,
                                    ConfigurationPerRunnerSettings configurationSettings) {
      final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings) {
        protected JavaParameters createJavaParameters() {
          return myParameters;
        }
      };
      final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
      if (myFilters != null) {
        for (final Filter myFilter : myFilters) {
          builder.addFilter(myFilter);
        }
      }
      state.setConsoleBuilder(builder);
      return state;
    }

    public String getName() {
      return myContentName;
    }

    public void checkConfiguration() {}

    public Module[] getModules() {
      return new Module[0];
    }
  }

  private RunManagerConfig getConfig() {
    return RunManagerEx.getInstanceEx(myProject).getConfig();
  }

  @NotNull
  public String getComponentName() {
    return "ExecutionManager";
  }
}
