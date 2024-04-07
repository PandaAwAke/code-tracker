/**
 * @author cdr
 */
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
package com.intellij.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings({"unchecked"})
public class SmartList<E> extends AbstractList<E> {
  private int mySize = 0;
  private Object myElem = null;

  public SmartList() {
  }

  public SmartList(Collection<? extends E> c) {
    addAll(c);
  }

  public E get(int index) {
    if (index < 0 || index >= mySize) {
      //noinspection HardCodedStringLiteral
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index > 0 && index < " + mySize);
    }
    if (mySize == 1) {
      return (E)myElem;
    }
    if (mySize == 2) {
      return (E)((Object[])myElem)[index];
    }
    else {
      return ((List<E>)myElem).get(index);
    }
  }

  public boolean add(E e) {
    if (mySize == 0) {
      myElem = e;
    }
    else if (mySize == 1) {
      Object[] array= new Object[2];
      array[0] = myElem;
      array[1] = e;
      myElem = array;
    }
    else if (mySize == 2) {
      List<E> list = new ArrayList<E>(3);
      final Object[] array = (Object[])myElem;
      list.add((E)array[0]);
      list.add((E)array[1]);
      list.add(e);
      myElem = list;
    }
    else {
      ((List<E>)myElem).add(e);
    }

    mySize++;
    return true;
  }

  public int size() {
    return mySize;
  }

  public void clear() {
    myElem = null;
    mySize = 0;
  }

  public E set(final int index, final E element) {
    if (index < 0 || index >= mySize) {
      //noinspection HardCodedStringLiteral
      throw new IndexOutOfBoundsException("index= " + index + ". Must be index > 0 && index < " + mySize);
    }
    final E oldValue;
    if (mySize == 1) {
      oldValue = (E)myElem;
      myElem = element;
    }
    else if (mySize == 2) {
      final Object[] array = (Object[])myElem;
      oldValue = (E)array[index];
      array[index] = element;
    }
    else {
      oldValue = ((List<E>)myElem).set(index, element);
    }

    return oldValue;
  }
}

