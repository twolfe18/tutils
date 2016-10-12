package edu.jhu.hlt.tutils;

import edu.jhu.hlt.tutils.hash.Hash;

public class LabeledSpan {
  public final String label;
  public final int start, end;
  
  public LabeledSpan(String label, Span s) {
    this(label, s.start, s.end);
  }

  public LabeledSpan(String label, int start, int end) {
    this.label = label;
    this.start = start;
    this.end = end;
  }

  public Span getSpan() {
    return Span.getSpan(start, end);
  }
  
  public String getLabel() {
    return label;
  }
  
  @Override
  public int hashCode() {
    return Hash.mix(start, end, label.hashCode());
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof LabeledSpan) {
      LabeledSpan ls = (LabeledSpan) other;
      return start == ls.start
          && end == ls.end
          && label.equals(ls.label);
    }
    return false;
  }
}
