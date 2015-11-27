package edu.jhu.hlt.tutils;

public class SpanPair {
  public final int aStart, aEnd;
  public final int bStart, bEnd;
  public final int hash;

  public SpanPair(Span a, Span b) {
    aStart = a.start;
    aEnd = a.end;
    bStart = b.start;
    bEnd = b.end;
    int h = aStart;
    h = Hash.mix(h, aEnd - aStart);
    h = Hash.mix(h, bStart);
    h = Hash.mix(h, bEnd - bStart);
    hash = h;
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
}