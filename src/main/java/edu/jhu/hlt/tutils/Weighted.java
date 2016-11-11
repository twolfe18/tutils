package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Comparator;

public class Weighted<T> implements Serializable {
  private static final long serialVersionUID = 4976175061369904278L;

  public final T item;
  public final double weight;
  
  public Weighted(T s, double w) {
    item = s;
    weight = w;
  }

  public String toString() {
    return String.format("(%s %.3f)", item, weight);
  }
  
  public static <R> Comparator<Weighted<R>> byScoreAsc() {
    return  new Comparator<Weighted<R>>() {
      @Override
      public int compare(Weighted<R> o1, Weighted<R> o2) {
        if (o1.weight < o2.weight)
          return -1;
        if (o1.weight > o2.weight)
          return +1;
        return 0;
      }
    };
  }

  public static <R> Comparator<Weighted<R>> byScoreDesc() {
    return  new Comparator<Weighted<R>>() {
      @Override
      public int compare(Weighted<R> o1, Weighted<R> o2) {
        if (o1.weight < o2.weight)
          return +1;
        if (o1.weight > o2.weight)
          return -1;
        return 0;
      }
    };
  }
}
