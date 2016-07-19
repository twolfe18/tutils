package edu.jhu.hlt.tutils;

import java.io.Serializable;

import edu.jhu.hlt.tutils.hash.Hash;

public class SpanPair implements Serializable {
  private static final long serialVersionUID = -5236947178233672871L;

  public final int aStart, aEnd;
  public final int bStart, bEnd;
  public final int hash;

  public SpanPair(Span a, Span b) {
    aStart = a.start;
    aEnd = a.end;
    bStart = b.start;
    bEnd = b.end;
    hash = Hash.mix(aStart, bStart, aEnd, bEnd);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof SpanPair) {
      SpanPair s = (SpanPair) other;
      return aStart == s.aStart
          && aEnd == s.aEnd
          && bStart == s.bStart
          && bEnd == s.bEnd;
    }
    return false;
  }

  public Span get1() {
    return Span.getSpan(aStart, aEnd);
  }

  public Span get2() {
    return Span.getSpan(bStart, bEnd);
  }

  @Override
  public String toString() {
    return "(" + get1() + ", " + get2() + ")";
  }
}