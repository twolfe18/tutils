package edu.jhu.hlt.tutils;

import java.io.Serializable;

/**
 * Take an argmin over a set which has double-valued scores.
 *
 * @author travis
 */
public class ArgMin<T> implements Serializable {
  private static final long serialVersionUID = -2419317323319510333L;

  private T bestItem;
  private double bestScore;
  private int offers = 0;

  public void offer(T item, double score) {
    if (item == null || Double.isInfinite(score) || Double.isNaN(score))
      throw new IllegalArgumentException();
    offers++;
    if (bestItem == null || score < bestScore) {
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
