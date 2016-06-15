package edu.jhu.hlt.tutils;

import edu.jhu.hlt.tutils.hash.Hash;

/**
 * A pair of two items which have the same type.
 * Don't use this will null values.
 * Does not cache the hash.
 *
 * @author travis
 */
public class Pair2<T> {

  private T left, right;

  public Pair2(T left, T right) {
    this.left = left;
    this.right = right;
  }

  public T getLeft() {
    return left;
  }

  public T getRight() {
    return right;
  }

  @Override
  public int hashCode() {
    return Hash.mix(left.hashCode(), right.hashCode());
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Pair2) {
      @SuppressWarnings("unchecked")
      Pair2<T> p = (Pair2<T>) other;
      return left.equals(p.left) && right.equals(p.right);
    }
    return false;
  }
}
