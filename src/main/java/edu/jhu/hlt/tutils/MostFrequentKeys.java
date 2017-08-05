package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.jhu.prim.tuple.Pair;

/**
 * A building block for many heavy-hitters style algorithms which use
 * {@link CountMinSketch}s and don't store all the keys. This class
 * stores the top-K keys by count. You only have the key when you update
 * the sketch, and when you add to this class it will store the key if it
 * has a top-K count.
 * 
 * @param <T> is the type of the item stored. It must be comparable and hash/equals-able
 * because internally a hashmap is used. Additionally, ties in the scoring function are
 * broken by Comparable<T>, keeping higher/last over lower/earlier elements.
 * 
 * @author travis
 */
public class MostFrequentKeys<T extends Comparable<T>> implements Serializable, Iterable<Pair<T, Double>> {
  private static final long serialVersionUID = 2856303357548105332L;

  private Map<T, Pair<T, Double>> m;
  private TreeSet<Pair<T, Double>> t;
  private int capacity;
  
  public MostFrequentKeys(int capacity) {
    this.m = new HashMap<>();
    Comparator<Pair<T, Double>> c0 = new Comparator<Pair<T, Double>>() {
      @Override
      public int compare(Pair<T, Double> o1, Pair<T, Double> o2) {
        double c1 = o1.get2();
        double c2 = o2.get2();
        assert !Double.isNaN(c1);
        assert !Double.isNaN(c2);
        if (c1 < c2)
          return -1;
        if (c1 > c2)
          return +1;
        return o1.get1().compareTo(o2.get1());
      }
    };
//    Comparator<Pair<T, Double>> c = (Comparator<Pair<T, Double>> & Serializable) c0;
//    this.t = new TreeSet<>(c);
    this.t = new TreeSet<>(c0);
    this.capacity = capacity;
  }
  
  public boolean contains(T item) {
    Pair<T, Double> x = m.get(item);
    if (x != null) assert t.contains(x);
//    assert (x != null) == t.contains(x);
    return x != null;
  }
  
  public boolean remove(T item) {
    Pair<T, Double> x = m.remove(item);
    if (x == null)
      return false;
    boolean r = t.remove(x);
    assert r;
    return true;
  }
  
  public void add(T item, double count) {
    Pair<T, Double> old = m.remove(item);
    if (old != null)
      t.remove(old);
    while (t.size() == capacity && t.first().get2() < count) {
      Pair<T, Double> f = t.pollFirst();
      m.remove(f.get1());
    }
    if (size() < capacity) {
      Pair<T, Double> i = new Pair<>(item, count);
      m.put(item, i);
      t.add(i);
    }
  }
  
  public double get(T item, double defaultCount) {
    Pair<T, Double> c = m.get(item);
    if (c == null)
      return defaultCount;
    return c.get2();
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
  public Iterator<Pair<T, Double>> iterator() {
    return t.descendingIterator();
  }
  
  private void debugAdd(T item, double count) {
    System.out.println("after adding " + item + ":" + count);
    add(item, count);
    System.out.println("t: " + t);
    System.out.println("m: " + m);
    System.out.println();
    Set<T> a = m.keySet();
    Set<T> b = new HashSet<>();
    for (Pair<T, Double> p : t)
      b.add(p.get1());
    assert a.equals(b);
  }
  
  public static void main(String[] args) {
    MostFrequentKeys<String> mfk = new MostFrequentKeys<>(4);
    
    mfk.debugAdd("foo", 1);
    mfk.debugAdd("foo", 2);
    mfk.debugAdd("bar", 2);
    mfk.debugAdd("baz", 5);
    mfk.debugAdd("quux", 4);
    mfk.debugAdd("qaz", 2);
    mfk.debugAdd("qazzy", 3);
    
  }
}