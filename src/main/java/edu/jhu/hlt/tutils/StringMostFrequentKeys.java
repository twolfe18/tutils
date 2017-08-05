package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @see MostFrequentKeys
 * @author travis
 */
public class StringMostFrequentKeys implements Iterable<P>, Serializable {
  private static final long serialVersionUID = 240954973546410019L;

  private Map<String, P> m;
  private TreeSet<P> t;
  private int capacity;
  
  public StringMostFrequentKeys(int capacity) {
    this.m = new HashMap<>();
    this.t = new TreeSet<>();
    this.capacity = capacity;
  }
  
  public P leaseFrequentInTopK() {
    return t.first();
  }
  
  public boolean contains(String item) {
    P x = m.get(item);
    if (x != null) assert t.contains(x);
//    assert (x != null) == t.contains(x);
    return x != null;
  }
  
  public boolean remove(String item) {
    P x = m.remove(item);
    if (x == null)
      return false;
    boolean r = t.remove(x);
    assert r;
    return true;
  }
  
  public void add(String item, double count) {
    P old = m.remove(item);
    if (old != null)
      t.remove(old);
    while (t.size() == capacity && t.first().value < count) {
      P f = t.pollFirst();
      m.remove(f.key);
    }
    if (size() < capacity) {
      P i = new P(item, count);
      m.put(item, i);
      t.add(i);
    }
  }
  
  public double get(String item, double defaultCount) {
    P c = m.get(item);
    if (c == null)
      return defaultCount;
    return c.value;
  }
  
  public int size() {
    assert t.size() == m.size();
    return t.size();
  }
  
  public int capacity() {
    return capacity;
  }

  /** Iterates in score/count order descending */
  @Override
  public Iterator<P> iterator() {
    return t.descendingIterator();
  }
  
  private void debugAdd(String item, double count) {
    System.out.println("after adding " + item + ":" + count);
    add(item, count);
    System.out.println("t: " + t);
    System.out.println("m: " + m);
    System.out.println();
    Set<String> a = m.keySet();
    Set<String> b = new HashSet<>();
    for (P p : t)
      b.add(p.key);
    assert a.equals(b);
  }
  
  public static void main(String[] args) {
    StringMostFrequentKeys mfk = new StringMostFrequentKeys(4);
    
    mfk.debugAdd("foo", 1);
    mfk.debugAdd("foo", 2);
    mfk.debugAdd("bar", 2);
    mfk.debugAdd("baz", 5);
    mfk.debugAdd("quux", 4);
    mfk.debugAdd("qaz", 2);
    mfk.debugAdd("qazzy", 3);
    
  }

}
