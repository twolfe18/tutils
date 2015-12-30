package edu.jhu.hlt.tutils;

import java.io.Serializable;

import edu.jhu.hlt.tutils.hash.Hash;

public class IntTrip implements Serializable {
  private static final long serialVersionUID = -2733379904840446206L;

  public final int first, second, third;
  public final int hash;

  public IntTrip(int first, int second, int third) {
    this.first = first;
    this.second = second;
    this.third = third;
    this.hash = Hash.mix(first, second, third);
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
