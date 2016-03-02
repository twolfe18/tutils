package edu.jhu.hlt.tutils;

import edu.jhu.hlt.tutils.hash.Hash;

public class Either<L, R> {
  private L left;
  private R right;

  protected Either(L left, R right) {
    this.left = left;
    this.right = right;
  }

  public boolean isLeft() {
    return left != null;
  }
  public boolean isRight() {
    return right != null;
  }

  public L getLeft() {
    assert isLeft();
    return left;
  }
  public R getRight() {
    assert isRight();
    return right;
  }

  @Override
  public int hashCode() {
    if (isLeft())
      return Hash.mix(12577927, left.hashCode());
    return Hash.mix(826921, right.hashCode());
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof Either) {
      Either<?, ?> x = (Either<?,?>) other;
      if (isLeft() == x.isLeft()) {
        return isLeft()
            ? getLeft().equals(x.getLeft())
            : getRight().equals(x.getRight());
      }
    }
    return false;
  }

  @Override
  public String toString() {
    if (isLeft())
      return "Left(" + left.toString() + ")";
    return "Right(" + right.toString() + ")";
  }

  public static <LT, RT> Either<LT, RT> left(LT left) {
    if (left == null)
      throw new IllegalArgumentException("null values are not allowed");
    return new Either<LT, RT>(left, null);
  }

  public static <LT, RT> Either<LT, RT> right(RT right) {
    if (right == null)
      throw new IllegalArgumentException("null values are not allowed");
    return new Either<LT, RT>(null, right);
  }
}
