package edu.jhu.hlt.tutils;

import java.io.Serializable;

public class ArgMax<T> implements Serializable {
  private static final long serialVersionUID = 5760960133608840784L;

  private T bestItem;
  private double bestScore = Double.NaN;
  private int bestIndex = -1;
  private int offers = 0;

  @Override
  public String toString() {
    return String.format("(ArgMax n=%d bestIndex=%d bestScore=%+2f bestItem=%s)",
        offers, bestIndex, bestScore, bestItem);
  }

  /**
   * Returns true if this is the new argmax.
   */
  public boolean offer(T item, double score) {
    if (Double.isInfinite(score) || Double.isNaN(score))
      throw new IllegalArgumentException();
    boolean newArgmax = offers == 0 || score > bestScore;
    if (newArgmax) {
      bestItem = item;
      bestScore = score;
      bestIndex = offers;
    }
    offers++;
    return newArgmax;
  }

  /** How many offers PRECEDED the best one, or the 0-based index of the best item. */
  public int getBestIndex() {
    return bestIndex;
  }

  public int numOffers() {
    return offers;
  }

  public T get() {
    return bestItem;
  }

  public double getBestScore() {
    return bestScore;
  }
}
