package edu.jhu.hlt.tutils;

import java.io.Serializable;

/**
 * Not hashable (hashcode/equals are from {@link Object}).
 *
 * @author travis
 */
public class DoublePair implements Serializable {
  private static final long serialVersionUID = 8174191303565303407L;

  public final double first, second;
  
  public DoublePair(double first, double second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public String toString() {
    return "(" + first + ", " + second + ")";
  }

  // TODO Comparators
}
