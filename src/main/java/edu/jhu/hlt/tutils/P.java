package edu.jhu.hlt.tutils;

import java.io.Serializable;

public final class P implements Serializable, Comparable<P> {
  private static final long serialVersionUID = 2621736032079472383L;

  public final String key;
  public final double value;

  public P(String k, double v) {
    this.key = k;
    this.value = v;
  }

  @Override
  public int compareTo(P other) {
    if (value < other.value)
      return -1;
    if (value > other.value)
      return +1;
    return key.compareTo(other.key);
  }
  
  @Override
  public String toString() {
    return "(" + key + " " + value + ")";
  }
}