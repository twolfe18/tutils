package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Comparator;

import edu.jhu.hlt.tutils.hash.Hash;

/**
 * Immutable.
 * @author travis
 */
public class IntPair implements Serializable {
  private static final long serialVersionUID = -480011813115806273L;

  public final int first, second;
  public final int hash;

  public IntPair(int first, int second) {
    this.first = first;
    this.second = second;
    this.hash = Hash.mix(first, second);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof IntPair) {
      IntPair x = (IntPair) other;
      return first == x.first && second == x.second;
    }
    return false;
  }

  @Override
  public String toString() {
    return "(" + first + ", " + second + ")";
  }

  /**
   * Compares on first first, then second. Lower values appear first.
   */
  public static Comparator<IntPair> ASCENDING = new Comparator<IntPair>() {
    @Override
    public int compare(IntPair o1, IntPair o2) {
      if (o1.first < o2.first)
        return -1;
      if (o1.first > o2.first)
        return 1;
      if (o1.second < o2.second)
        return -1;
      if (o1.second > o2.second)
        return 1;
      return 0;
    }
  };

  /**
   * Compares on first first, then second. Higher values appear first.
   */
  public static Comparator<IntPair> DESCENDING = new Comparator<IntPair>() {
    @Override
    public int compare(IntPair o1, IntPair o2) {
      return -ASCENDING.compare(o1, o2);
    }
  };
}
