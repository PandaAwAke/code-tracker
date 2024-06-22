/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class CollectUtil<E> {
  public abstract <T extends E> HashSet<T> toSet(Iterator<T> iterator);

  public <Dom, Rng extends E> HashSet<Rng> toSet(Iterator<Dom> iterator, Convertor<Dom, Rng> convertor) {
    return toSet(ConvertingIterator.create(iterator, convertor));
  }

  public <Dom, Rng extends E> HashSet<Rng> toSet(Dom[] objects, Convertor<Dom, Rng> convertor) {
    return toSet(ContainerUtil.iterate(objects), convertor);
  }

  public Object[] toArray(Iterator iterator) {
    return toList(iterator).toArray();
  }

  public abstract <T extends E> ArrayList<T> toList(Iterator<T> iterator);

  public <Dom, Rng extends E> ArrayList<Rng> toList(Iterator<Dom> iterator, Convertor<Dom, Rng> convertor) {
    return toList(ConvertingIterator.create(iterator, convertor));
  }

  public <Dom, Rng extends E> ArrayList<Rng> toList(Dom[] objects, Convertor<Dom, Rng> convertor) {
    return toList(ContainerUtil.iterate(objects), convertor);
  }

  public <Dom, Rng extends E> List<Rng> toList(List<Dom> list, Convertor<Dom, Rng> convertor) {
    return toList(list.iterator(), convertor);
  }

  public static <T> CollectUtil<T> select(Condition<T> condition) {
    return new Select<T>(condition);
  }

  public static final CollectUtil COLLECT = new CollectUtil<Object>() {
    public <T> HashSet<T> toSet(Iterator<T> iterator) {
      return ContainerUtil.collectSet(iterator);
    }

    public <T> ArrayList<T> toList(Iterator<T> iterator) {
      return ContainerUtil.collect(iterator);
    }
  };

  public static final CollectUtil SKIP_NULLS = new Select(FilteringIterator.NOT_NULL);

  private static class Select<E> extends CollectUtil<E> {
    private final Condition<E> myCondition;

    public Select(Condition<E> condition) {
      myCondition = condition;
    }

    public <T extends E> ArrayList<T> toList(Iterator<T> iterator) {
      return COLLECT.toList(FilteringIterator.create(iterator, myCondition));
    }

    public <T extends E> HashSet<T> toSet(Iterator<T> iterator) {
      return COLLECT.toSet(FilteringIterator.create(iterator, myCondition));
    }
  }
}
