/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jetbrains.annotations.NonNls;

public class MergingUpdateQueue implements Runnable, Disposable {

  private boolean myActive;

  // protected for Fabrique:
  protected final Set<Update> mySheduledUpdates = new TreeSet<Update>();

  private Alarm myWaiterForMerge = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myFlushing;

  private String myName;
  private int myMergingTimeSpan;
  private final JComponent myComponent;
  private boolean myPassThrough;

  public MergingUpdateQueue(@NonNls String name, int mergingTimeSpan, boolean isActive, JComponent component) {
    myMergingTimeSpan = mergingTimeSpan;
    myComponent = component;
    myName = name;
    myPassThrough = ApplicationManager.getApplication().isUnitTestMode();

    if (isActive) {
      showNotify();
    }
  }

  public void setMergingTimeSpan(int timeSpan) {
    myMergingTimeSpan = timeSpan;
    if (myActive) {
      restartTimer();
    }
  }

  public void cancelAllUpdates() {
    synchronized(mySheduledUpdates) {
      mySheduledUpdates.clear();
    }
  }

  public final boolean isPassThrough() {
    return myPassThrough;
  }

  public final void setPassThrough(boolean passThrough) {
    myPassThrough = passThrough;
  }

  public void hideNotify() {
    if (!myActive) {
      return;
    }

    myActive = false;
    clearWaiter();
  }

  public void showNotify() {
    if (myActive) {
      return;
    }

    restartTimer();
    myActive = true;
    flush();
  }

  private void restartTimer() {
    clearWaiter();
    myWaiterForMerge.addRequest(this, myMergingTimeSpan, getModalityState());
  }


  public void run() {
    flush();
  }

  public void flush() {
    if (mySheduledUpdates.size() > 0) {
      flush(true);
    }
  }

  public boolean isFlushing() {
    return myFlushing;
  }

  public void flush(boolean invokeLaterIfNotDispatch) {
    if (myFlushing) {
      return;
    }
    if (!isModalityStateCorrect()) {
      return;
    }

    myFlushing = true;
    final Runnable toRun = new Runnable() {
      public void run() {
        try {
          List<Update> toUpdate;
          final Update[] all;

          synchronized(mySheduledUpdates) {
            toUpdate = new ArrayList<Update>(mySheduledUpdates.size());
            all = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
            mySheduledUpdates.clear();
          }

          for (Update each : all) {
            if (!isExpired(each)) {
              toUpdate.add(each);
            }
            each.setProcessed();
          }

          execute(toUpdate.toArray(new Update[toUpdate.size()]));
        }
        finally {
          myFlushing = false;
        }
      }
    };

    if (invokeLaterIfNotDispatch && !ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(toRun, ModalityState.NON_MMODAL);
    }
    else {
      toRun.run();
    }
  }

  private boolean isModalityStateCorrect() {
    ModalityState current = ApplicationManager.getApplication().getCurrentModalityState();
    final ModalityState modalityState = getModalityState();
    return !current.dominates(modalityState);
  }

  private boolean isExpired(Update each) {
    return each.isDisposed() || each.isExpired();
  }

  protected void execute(final Update[] update) {
    for (final Update each : update) {
      if (each.executeInWriteAction()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            each.run();
          }
        });
      }
      else {
        each.run();
      }
    }
  }

  public void queue(final Update update) {
    final Application app = ApplicationManager.getApplication();
    if (myPassThrough) {
      app.invokeLater(update);
      return;
    }

    synchronized(mySheduledUpdates) {
      boolean updateWasEatenByQueue = eatThisOrOthers(update);
      if (updateWasEatenByQueue) {
        return;
      }

      if (myActive) {
        if (mySheduledUpdates.isEmpty()) {
          restartTimer();
        }
        put(update);
      }
      else {
        put(update);
      }
    }
  }

  private boolean eatThisOrOthers(Update update) {
    if (mySheduledUpdates.contains(update)) {
      return false;
    }

    final Update[] updates = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
    for (Update eachInQueue : updates) {
      if (eachInQueue.canEat(update)) {
        return true;
      }
      if (update.canEat(eachInQueue)) {
        mySheduledUpdates.remove(eachInQueue);
      }
    }
    return false;
  }

  public final void run(Update update) {
    execute(new Update[] {update});
  }

  private void put(Update update) {
    mySheduledUpdates.remove(update);
    mySheduledUpdates.add(update);
  }

  protected boolean passThroughForUnitTesting() {
    return true;
  }

  public boolean isActive() {
    return myActive;
  }

  public void dispose() {
    clearWaiter();
  }

  private void clearWaiter() {
    myWaiterForMerge.cancelAllRequests();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Merger: " + myName + " active=" + myActive + " sheduled=" + mySheduledUpdates;
  }

  public boolean containsUpdateOf(int priority) {
    Update[] update = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
    for (Update each : update) {
      if (each.getPriority() == priority) {
        return true;
      }
    }
    return false;
  }

  public ModalityState getModalityState() {
    if (myComponent == null) {
      return ModalityState.NON_MMODAL;
    }
    return ModalityState.stateForComponent(myComponent);
  }
}
