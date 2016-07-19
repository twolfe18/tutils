package edu.jhu.hlt.tutils;

import java.io.Serializable;

public class ArgMax<T> implements Serializable {
  private static final long serialVersionUID = 5760960133608840784L;

  private T bestItem;
  private double bestScore;
  private int offers = 0;

  public void offer(T item, double score) {
    if (item == null || Double.isInfinite(score) || Double.isNaN(score))
      throw new IllegalArgumentException();
    offers++;
    if (bestItem == null || score > bestScore) {
      bestItem = item;
      bestScore = score;
    }
  }

  public int numOffers() {
    return offers;
  }

  public T get() {
    return bestItem;
  }
}
