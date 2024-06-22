/*
* Copyright 2000-2005 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NonNls;

import java.util.LinkedList;

public class WorkerThread extends Thread{
  private LinkedList<Runnable> myTasks = new LinkedList<Runnable>();
  private boolean myToDispose = false;
  private boolean myDisposed = false;

  public WorkerThread(@NonNls String name) {
    super(name);
  }

  public boolean addTask(Runnable action) {
    synchronized(myTasks){
      if(myDisposed) return false;

      myTasks.add(action);
      myTasks.notifyAll();
      return true;
    }
  }

  public boolean addTaskFirst(Runnable action) {
    synchronized(myTasks){
      if(myDisposed) return false;

      myTasks.add(0, action);
      myTasks.notifyAll();
      return true;
    }
  }

  public void dispose(boolean cancelTasks){
    synchronized(myTasks){
      if (cancelTasks){
        myTasks.clear();
      }
      myToDispose = true;
      myTasks.notifyAll();
    }
  }

  public boolean isDisposeRequested() {
    synchronized(myTasks){
      return myToDispose;
    }
  }

  public boolean isDisposed() {
    synchronized(myTasks){
      return myDisposed;
    }
  }

  public void run() {
    while(true){
      while(true){
        Runnable task;
        synchronized(myTasks){
          if (myTasks.isEmpty()) break;
          task = myTasks.removeFirst();
        }
        task.run();
      }

      synchronized(myTasks){
        if (myToDispose && myTasks.isEmpty()){
          myDisposed = true;
          return;
        }

        try{
          myTasks.wait();
        }
        catch(InterruptedException e){
        }
      }
    }
  }
}