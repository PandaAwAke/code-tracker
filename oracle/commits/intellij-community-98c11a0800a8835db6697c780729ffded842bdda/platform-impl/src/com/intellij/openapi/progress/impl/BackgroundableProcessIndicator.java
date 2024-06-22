/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 20, 2006
 * Time: 8:40:15 PM
 */
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BackgroundableProcessIndicator extends ProgressWindow {
  protected StatusBarEx myStatusBar;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})

  private PerformInBackgroundOption myOption;
  private TaskInfo myInfo;

  private boolean myDisposed;
  private DumbModeAction myDumbModeAction = DumbModeAction.NOTHING;

  public BackgroundableProcessIndicator(Task.Backgroundable task) {
    this(task.getProject(), task, task);

    myDumbModeAction = task.getDumbModeAction();
    if (myDumbModeAction == DumbModeAction.CANCEL) {
      ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        public void beforeEnteringDumbMode() {
          cancel();
        }

        public void enteredDumbMode() {
        }

        public void exitDumbMode() {
        }
      });
    }
  }

  public BackgroundableProcessIndicator(@Nullable Project project, TaskInfo info, @NotNull PerformInBackgroundOption option) {
    super(info.isCancellable(), true, project, info.getCancelText());
    setOwnerTask(info);
    setProcessId(info.getProcessId());
    myOption = option;
    myInfo = info;
    setTitle(info.getTitle());
    final Project nonDefaultProject = (project == null || project.isDisposed()) ? null : ((project.isDefault()) ? null : project);
    final IdeFrame frame = ((WindowManagerEx)WindowManager.getInstance()).findFrameFor(nonDefaultProject);
    myStatusBar = (StatusBarEx)frame.getStatusBar();
    if (option.shouldStartInBackground()) {
      doBackground();
    }
  }

  public BackgroundableProcessIndicator(Project project,
                                        @Nls final String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nls final String cancelButtonText,
                                        @Nls final String backgroundStopTooltip, final boolean cancellable) {
    this(project, new TaskInfo() {
      public String getProcessId() {
        return "<unknown>";
      }

      public String getTitle() {
        return progressTitle;
      }

      public String getCancelText() {
        return cancelButtonText;
      }

      public String getCancelTooltipText() {
        return backgroundStopTooltip;
      }

      public boolean isCancellable() {
        return cancellable;
      }
    }, option);
  }

  public DumbModeAction getDumbModeAction() {
    return myDumbModeAction;
  }

  protected void showDialog() {
    if (myDisposed) return;

    if (myOption.shouldStartInBackground()) {
      return;
    }

    super.showDialog();
  }

  public void background() {
    if (myDisposed) return;

    myOption.processSentToBackground();
    doBackground();
    super.background();
  }

  private void doBackground() {
    myStatusBar.add(this, myInfo);
  }

  public void dispose() {
    super.dispose();
    myDisposed = true;
    myInfo = null;
    myStatusBar = null;
    myOption = null;
  }
}