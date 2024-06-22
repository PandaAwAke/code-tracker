/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.containers;

import gnu.trove.THashMap;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Fully copied from java.util.WeakHashMap except "get" method optimization.
 */
public final class WeakHashMap<K,V> extends AbstractMap<K,V> implements Map<K,V> {

  private static interface Key{
    public Object get();
  }

  private static class WeakKey extends WeakReference implements Key{
    private int myHash;	/* Hashcode of key, stored here since the key may be tossed by the GC */

    private WeakKey(Object k) {
      super(k);
      myHash = k.hashCode();
    }

    public static WeakKey create(Object k) {
      return k != null ? new WeakKey(k) : null;
    }

    private WeakKey(Object k, ReferenceQueue q) {
      super(k, q);
      myHash = k.hashCode();
    }

    private static WeakKey create(Object k, ReferenceQueue q) {
      return k != null ? new WeakKey(k, q) : null;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = this.get();
      Object u = ((Key)o).get();
      if ((t == null) || (u == null)) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private static class HardKey implements Key{
    private Object myObject;
    private int myHash;

    public HardKey(Object object) {
      myObject = object;
      myHash = object.hashCode();
    }

    public Object get() {
      return myObject;
    }

    public void set(Object object) {
      myObject = object;
      if (object != null){
        myHash = object.hashCode();
      }
      else{
        myHash = 0;
      }
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Object t = this.get();
      Object u = ((Key)o).get();
      if ((t == null) || (u == null)) return false;
      if (t == u) return true;
      return t.equals(u);
    }

    public int hashCode() {
      return myHash;
    }
  }

  private Map myMap;

  private ReferenceQueue myReferenceQueue = new ReferenceQueue();

  private HardKey myHardKeyInstance = new HardKey(""); // "singleton"

  private void processQueue() {
    WeakKey wk;
    while((wk = (WeakKey)myReferenceQueue.poll()) != null){
      myMap.remove(wk);
    }
  }

  public WeakHashMap(int initialCapacity, float loadFactor) {
    myMap = new THashMap(initialCapacity, loadFactor);
  }

  public WeakHashMap(int initialCapacity) {
    myMap = new THashMap(initialCapacity);
  }

  public WeakHashMap() {
    myMap = new THashMap();
  }

  public WeakHashMap(Map t) {
    this(Math.max(2 * t.size(), 11), 0.75f);
    putAll(t);
  }

  public int size() {
    return entrySet().size();
  }

  public boolean isEmpty() {
    return entrySet().isEmpty();
  }

  public boolean containsKey(Object key) {
    // optimization:
    if (key == null){
      return myMap.containsKey(null);
    }
    else{
      myHardKeyInstance.set(key);
      boolean result = myMap.containsKey(myHardKeyInstance);
      myHardKeyInstance.set(null);
      return result;
    }
    //return myMap.containsKey(WeakKey.create(key));
  }

  public V get(Object key) {
    //return myMap.get(WeakKey.create(key));
    // optimization:
    if (key == null){
      return (V)myMap.get(null);
    }
    else{
      myHardKeyInstance.set(key);
      Object result = myMap.get(myHardKeyInstance);
      myHardKeyInstance.set(null);
      return (V)result;
    }
  }

  public V put(K key, V value) {
    processQueue();
    return (V)myMap.put(WeakKey.create(key, myReferenceQueue), value);
  }

  public V remove(Object key) {
    processQueue();

    // optimization:
    if (key == null){
      return (V)myMap.remove(null);
    }
    else{
      myHardKeyInstance.set(key);
      Object result = myMap.remove(myHardKeyInstance);
      myHardKeyInstance.set(null);
      return (V)result;
    }
    //return myMap.remove(WeakKey.create(key));
  }

  public void clear() {
    processQueue();
    myMap.clear();
  }

  static private class Entry implements Map.Entry {
    private Map.Entry ent;
    private Object key;	/* Strong reference to key, so that the GC
                                 will leave it alone as long as this Entry
                                 exists */

    Entry(Map.Entry ent, Object key) {
      this.ent = ent;
      this.key = key;
    }

    public Object getKey() {
      return key;
    }

    public Object getValue() {
      return ent.getValue();
    }

    public Object setValue(Object value) {
      return ent.setValue(value);
    }

    private static boolean valEquals(Object o1, Object o2) {
      return (o1 == null) ? (o2 == null) : o1.equals(o2);
    }

    public boolean equals(Object o) {
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      return (valEquals(key, e.getKey())
        && valEquals(getValue(), e.getValue()));
    }

    public int hashCode() {
      Object v;
      return (((key == null) ? 0 : key.hashCode())
        ^ (((v = getValue()) == null) ? 0 : v.hashCode()));
    }

  }

  /* Internal class for entry sets */
  private class EntrySet extends AbstractSet {
    Set hashEntrySet = myMap.entrySet();

    public Iterator iterator() {

      return new Iterator() {
        Iterator hashIterator = hashEntrySet.iterator();
        Entry next = null;

        public boolean hasNext() {
          while(hashIterator.hasNext()){
            Map.Entry ent = (Map.Entry)hashIterator.next();
            WeakKey wk = (WeakKey)ent.getKey();
            Object k = null;
            if ((wk != null) && ((k = wk.get()) == null)){
              /* Weak key has been cleared by GC */
              continue;
            }
            next = new Entry(ent, k);
            return true;
          }
          return false;
        }

        public Object next() {
          if ((next == null) && !hasNext())
            throw new NoSuchElementException();
          Entry e = next;
          next = null;
          return e;
        }

        public void remove() {
          hashIterator.remove();
        }

      };
    }

    public boolean isEmpty() {
      return !(iterator().hasNext());
    }

    public int size() {
      int j = 0;
      for(Iterator i = iterator(); i.hasNext(); i.next()) j++;
      return j;
    }

    public boolean remove(Object o) {
      processQueue();
      if (!(o instanceof Map.Entry)) return false;
      Map.Entry e = (Map.Entry)o;
      Object ev = e.getValue();

      // optimization:
      Object key;
      if (o == null){
        key = null;
      }
      else{
        myHardKeyInstance.set(o);
        key = myHardKeyInstance;
      }
      //WeakKey key = WeakKey.create(e.getKey());

      Object hv = myMap.get(key);
      boolean toRemove = hv == null ? (ev == null && myMap.containsKey(key)) : hv.equals(ev);
      if (toRemove){
        myMap.remove(key);
      }
      myHardKeyInstance.set(null);
      return toRemove;
    }

    public int hashCode() {
      int h = 0;
      for(Iterator i = hashEntrySet.iterator(); i.hasNext();){
        Map.Entry ent = (Map.Entry)i.next();
        WeakKey wk = (WeakKey)ent.getKey();
        Object v;
        if (wk == null) continue;
        h += (wk.hashCode()
          ^ (((v = ent.getValue()) == null) ? 0 : v.hashCode()));
      }
      return h;
    }

  }

  private Set entrySet = null;

  public Set<Map.Entry<K,V>> entrySet() {
    if (entrySet == null) entrySet = new EntrySet();
    return entrySet;
  }

}
