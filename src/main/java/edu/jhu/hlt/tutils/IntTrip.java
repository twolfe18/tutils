package edu.jhu.hlt.tutils;

public class IntTrip {

  public final int first, second, third;
  public final int hash;

  public IntTrip(int first, int second, int third) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.hash = first + 31 * (second + 31 * third);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof IntTrip) {
      IntTrip x = (IntTrip) other;
      return first == x.first && second == x.second && third == x.third;
    }
    return false;
  }

  @Override
  public String toString() {
    return "(" + first + ", " + second + ", " + third + ")";
  }

}
