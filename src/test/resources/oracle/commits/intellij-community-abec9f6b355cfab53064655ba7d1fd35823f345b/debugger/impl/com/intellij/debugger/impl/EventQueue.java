package com.intellij.debugger.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class EventQueue<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.EventQueue");

  private final LinkedList[] myEvents;
  private final ReentrantLock myLock;
  private final Condition myEventsAvailable;

  private volatile E myCurrentEvent;

  private volatile boolean myIsClosed = false;

  public EventQueue (int countPriorities) {
    myLock = new ReentrantLock();
    myEventsAvailable = myLock.newCondition();
    myEvents = new LinkedList[countPriorities];
    for (int i = 0; i < myEvents.length; i++) {
      myEvents[i] = new LinkedList<E>();
    }
  }

  public void pushBack(@NotNull E event, int priority) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("pushBack event " + event);
    }

    myLock.lock();
    try {
      getEventsList(priority).addFirst(event);
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
  }

  public void put(@NotNull E event, int priority) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("put event " + event);
    }

    myLock.lock();
    try {
      getEventsList(priority).offer(event);
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
  }

  private LinkedList<E> getEventsList(final int priority) {
    return (LinkedList<E>)myEvents[priority];
  }

  public void close(){
    myLock.lock();
    try {
      myIsClosed = true;
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
  }

  private E getEvent() throws EventQueueClosedException {
    myLock.lock();
    try {
      while (true) {
        if(myIsClosed) {
          throw new EventQueueClosedException();
        }
        for (int i = 0; i < myEvents.length; i++) {
          final E event = getEventsList(i).poll();
          if (event != null) {
            return event;
          }
        }
        myEventsAvailable.awaitUninterruptibly();
      }
    }
    finally {
      myLock.unlock();
    }
  }

  public E get() throws EventQueueClosedException {
    return myCurrentEvent = getEvent();
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  public E getCurrentEvent() {
    return myCurrentEvent;
  }
}
