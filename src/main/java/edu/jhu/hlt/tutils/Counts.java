package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Counts<T> implements Serializable {
  private static final long serialVersionUID = -2960152232810435237L;

  /** Use this if T=int */
  public static class Int {
    private int[] counts;
    private long sum;
    public Int(int dimension) {
      counts = new int[dimension];
      sum = 0;
    }
    public int get(int index) {
      return counts[index];
    }
    public double getProb(int index) {
      assert sum > 0;
      return ((double) counts[index]) / sum;
    }
    public double getLogProb(int index) {
      return Math.log(counts[index]) - Math.log(sum);
    }
    public int increment(int index) {
      return update(index, 1);
    }
    public int decrement(int index) {
      return update(index, -1);
    }
    public int update(int index, int delta) {
      int old = counts[index];
      sum += delta;
      counts[index] += delta;
      return old;
    }
  }

  public static class Pseudo<R> {
    private Map<R, Double> counts = new HashMap<>();
    private double total = 0;

    public double getCount(R r) {
      Double c = counts.get(r);
      if (c == null)
        return 0;
      return c;
    }

    public double update(R r, double delta) {
      Double old = counts.get(r);
      if (old == null)
        old = 0d;
      counts.put(r, old + delta);
      total += delta;
      return old;
    }

    public double increment(R r) {
      return update(r, 1d);
    }

    public double getTotalCount() {
      return total;
    }

    public Iterable<Entry<R, Double>> entrySet() {
      return counts.entrySet();
    }

    public int numNonZero() {
      return counts.size();
    }
  }

  private Map<T, Integer> counts = new HashMap<>();
  private int total = 0;

  public int getCount(T t) {
    Integer c = counts.get(t);
    return c == null ? 0 : c;
  }

  public double getProportion(T t) {
    if (total == 0)
      return 0d;
    return ((double) getCount(t)) / total;
  }

  public Iterable<Entry<T, Integer>> entrySet() {
    return counts.entrySet();
  }

  /** Returns the count before the update */
  public int increment(T t) {
    return update(t, 1);
  }

  /** Returns the count before the update */
  public int update(T t, int delta) {
    int c = getCount(t);
    long cc = ((long) c) + delta;
    if (cc > Integer.MAX_VALUE || cc < Integer.MIN_VALUE)
      Log.info("WARNING: overflow! key=" + t + " count=" + cc + " delta=" + delta);
    counts.put(t, c + delta);
    total += delta;
    return c;
  }

  public int numNonZero() {
    return counts.size();
  }

  public int getTotalCount() {
    return total;
  }

  public String toString() {
//    return counts.toString();
    StringBuilder sb = null;
    for (T t : getKeysSortedByCount(true)) {
      int c = getCount(t);
      if (sb == null) {
        sb = new StringBuilder("{");
      } else {
        sb.append(' ');
      }
      sb.append(t + ":" + c);
    }
    if (sb == null)
      return "{}";
    sb.append('}');
    return sb.toString();
  }

  /**
   * Returns a string in <key>=<value> format
   */
  public String toStringWithEq() {
    StringBuilder sb = null;
    for (T t : getKeysSortedByCount(true)) {
      int c = getCount(t);
      if (sb == null)
        sb = new StringBuilder();
      else
        sb.append(' ');
      sb.append(t + "=" + c);
    }
    if (sb == null)
      return "";
    return sb.toString();
  }

  /** Will hit a runtime error if keys is not comparable */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public List<T> getKeysSorted() {
    List<T> items = new ArrayList<>();
    items.addAll(counts.keySet());
    Collections.sort((List<Comparable>) items);
    return items;
  }

  public List<T> getKeysSortedByCount(final boolean descending) {
    List<T> items = new ArrayList<>();
    items.addAll(counts.keySet());
    Collections.sort(items, new Comparator<T>() {
      @Override
      public int compare(T arg0, T arg1) {
        int c0 = getCount(arg0);
        int c1 = getCount(arg1);
        if(c0 == c1) return 0;
        if(c1 < c0 ^ descending)
          return 1;
        else
          return -1;
      }
    });
    return items;
  }

  public List<T> countIsAtLeast(int minCount) {
    if(minCount <= 0)
      throw new IllegalArgumentException();
    List<T> l = new ArrayList<T>();
    for(T t : counts.keySet())
      if(getCount(t) >= minCount)
        l.add(t);
    return l;
  }

  public List<T> countIsLessThan(int maxCount) {
    if(maxCount <= 0)
      throw new IllegalArgumentException();
    List<T> l = new ArrayList<T>();
    for(T t : counts.keySet())
      if(getCount(t) < maxCount)
        l.add(t);
    return l;
  }

  public void clear() {
    counts.clear();
    total = 0;
  }

  public Comparator<T> ascendingComparator() {
    return new Comparator<T>() {
      @Override
      public int compare(T arg0, T arg1) {
        return getCount(arg1) - getCount(arg0);
      }
    };
  }

  public Comparator<T> desendingComparator() {
    return new Comparator<T>() {
      @Override
      public int compare(T arg0, T arg1) {
        return getCount(arg0) - getCount(arg1);
      }
    };
  }
}
