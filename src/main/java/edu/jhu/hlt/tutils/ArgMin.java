package edu.jhu.hlt.tutils;

/**
 * Take an argmin over a set which has double-valued scores.
 *
 * @author travis
 */
public class ArgMin<T> {
  private T bestItem;
  private double bestScore;

  public void offer(T item, double score) {
    if (item == null || Double.isInfinite(score) || Double.isNaN(score))
      throw new IllegalArgumentException();
    if (bestItem == null || score < bestScore) {
      bestItem = item;
      bestScore = score;
    }
  }

  public T get() {
    assert bestItem != null;
    return bestItem;
  }
}
