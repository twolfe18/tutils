package edu.jhu.hlt.tutils;

import java.util.Comparator;

public class IntDouble {
  
  public final int i;
  public final double v;
  
  public IntDouble(int i, double v) {
    this.i = i;
    this.v = v;
  }

  public static final Comparator<IntDouble> BY_DOUBLE_DESC = new Comparator<IntDouble>() {
    @Override
    public int compare(IntDouble o1, IntDouble o2) {
      if (o1.v > o2.v)
        return -1;
      if (o1.v < o2.v)
        return +1;
      return 0;
    }
  };
}
