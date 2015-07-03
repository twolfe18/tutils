package edu.jhu.hlt.tutils;

/**
 * Immutable.
 * @author travis
 */
public class IntPair {

  public final int first, second;
  public final int hash;

  public IntPair(int first, int second) {
    this.first = first;
    this.second = second;
    this.hash = Integer.reverse(first) ^ second;
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
}
