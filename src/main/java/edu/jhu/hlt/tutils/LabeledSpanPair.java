package edu.jhu.hlt.tutils;

import java.io.Serializable;

import edu.jhu.hlt.tutils.hash.Hash;

public class LabeledSpanPair extends SpanPair implements Serializable {
  private static final long serialVersionUID = -8863663195586041528L;

  private String label;
  private int labelHash;

  public LabeledSpanPair(Span a, Span b, String label) {
    super(a, b);
    this.label = label;
    this.labelHash = Hash.hash(label);
  }

  public String getLabel() {
    return label;
  }

  @Override
  public int hashCode() {
    return Hash.mix(hash, labelHash);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof LabeledSpanPair) {
      LabeledSpanPair ls = (LabeledSpanPair) other;
      return super.equals(ls) && label.equals(ls.label);
    }
    return false;
  }
}
