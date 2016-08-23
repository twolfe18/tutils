package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import edu.jhu.hlt.concrete.TokenRefSequence;
import edu.jhu.hlt.tutils.hash.Hash;

public final class Span implements Comparable<Span>, Serializable {
  private static final long serialVersionUID = -7592836078770608357L;

  public static final Comparator<Span> BY_WIDTH_ASC = new Comparator<Span>() {
    @Override
    public int compare(Span o1, Span o2) {
      return o1.width() - o2.width();
    }
  };
  public static final Comparator<Span> BY_END_LR_THEN_WIDTH_THIN = new Comparator<Span>() {
    @Override
    public int compare(Span o1, Span o2) {
      int i = o1.end - o2.end;
      if (i != 0)
        return i;
      return o1.width() - o2.width();
    }
  };

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeInt(start);
    out.writeInt(end);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    start = in.readInt();
    end = in.readInt();
  }

  // https://docs.oracle.com/javase/7/docs/platform/serialization/spec/input.html#5903
  private Object readResolve() {
    return Span.getSpan(start, end);
  }

  // TODO Use this for Span[] vs Span[][]
  /**
   * Densely embeds all spans into the natural numbers.
   * Doesn't work for nullSpan, requires start<end.
   */
  public static int index(Span s) {
    return index(s.start, s.end);
  }
  public static int index(int start, int end) {
    assert start < end;
    /*
key = (i,j) where i<j
M = number of mentions

There are two arrangements: {locality in i, locality in j}
(locality in j is preffered)
and two orderings: {small blocks in front, big blocks in front}.
(small blocks in front is preferred due to easier math)

<none s.t. j=0>
(i=0, j=1)     // 1 of these
(i=0, j=2)
(i=1, j=2)     // 2 of these
(i=0, j=3)
(i=1, j=3)
(i=2, j=3)     // 3 of these
...

index(i,j) = Z(j) + i
where Z(j) = sum_{t=1}^{j-1} t = i*(i-1)/2
(Note: related formula for the sum of the first k natural numbers
is k*(k+1)/2, this is the first k-1 numbers, hence (k-1)*k/2)

index(i=2,j=4) = Z(4) + 2
Z(4) = 4*3/2 = 6
=> 8, correct!
     */
    int Z = end * (end - 1) / 2;
    return Z + start;
  }

  public static int indexMaybeNullSpan(Span s) {
    if (s == Span.nullSpan)
      return 0;
    return 1 + index(s);
  }

  public static int encodeSpan(Span s, int sentenceLen) {
    assert s.end <= sentenceLen;
    assert s.start <= sentenceLen;
    return s.start + s.end * (sentenceLen + 1);
  }
  public static Span decodeSpan(int enc, int sentenceLen) {
    assert enc >= 0;
    int start = enc % (sentenceLen + 1);
    int end = enc / (sentenceLen + 1);
    return getSpan(start, end);
  }

  public static List<Span> allSpans(int n, boolean includeNullSpan) {
    List<Span> c = new ArrayList<>();
    if (includeNullSpan)
      c.add(nullSpan);
    for (int i = 0; i < n; i++)
      for (int j = i+1; j <= n; j++)
        c.add(getSpan(i, j));
    return c;
  }

  public int start;  // inclusive
  public int end;    // non-inclusive

  public static final Span nullSpan = new Span(0, 0);

  // intern instances of Span just like String
  // also, you can use ==
  // this table is indexed as [start][width - 1]
  private static Span[][] internedSpans = new Span[0][0];

  public static Span getSpan(int start, int end) {
    // don't store this in the table, because all other spans will
    // obey the invariant start > end (i.e. width >= 1).
    if(start == 0 && end == 0)
      return nullSpan;

    if(start >= end) {
      throw new IllegalArgumentException(
          "start must be less than end: " + start + " >= " + end);
    }

    // make a bigger table if the previous was too small
    if(end > internedSpans.length) {
      int newInternedMaxSentSize = end + 10;

      Span[][] newInternedSpans = new Span[newInternedMaxSentSize][];
      for(int s=0; s<newInternedSpans.length; s++) {
        newInternedSpans[s] = new Span[newInternedMaxSentSize - s];
        for(int width=1; width <= newInternedMaxSentSize - s; width++) {
          // use old value if possible (needed to ensure == works)
          newInternedSpans[s][width - 1] =
              s < internedSpans.length && width-1 < internedSpans[s].length
                ? internedSpans[s][width-1]
                : new Span(s, s+width);
        }
      }
      internedSpans = newInternedSpans;
    }
    int width = end - start;
    return internedSpans[start][width - 1];
  }

  /**
   * Reads a continuous and non-empty Span from tokenIndexList.
   * @param outputEndIsInclusive says whether the Span returned should have an
   * inclusive or exclusive ending (start is always inclusive).
   */
  public static Span getSpan(TokenRefSequence trs, boolean outputEndIsInclusive) {
    int start = trs.getTokenIndexList().get(0);
    int prev = start - 1;
    int end = start;
    for (int i : trs.getTokenIndexList()) {
      assert i >= start : "non-ordered tokens? " + trs.getTokenIndexList();
      assert i == prev + 1 : "gappy tokens? " + trs.getTokenIndexList();
      prev = i;
      end = i;
    }
    if (outputEndIsInclusive)
      return getSpan(start, end);
    return getSpan(start, end + 1);
  }

  private Span(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public int width() { return end - start; }

  /**
   * return true if this span is to the left of
   * other with no overlap.
   */
  public boolean before(Span other) {
    return this.end <= other.start;
  }

  /**
   * return true if this span is to the right of
   * other with no overlap.
   */
  public boolean after(Span other) {
    return this.start >= other.end;
  }

  public boolean covers(Span other) {
    return this.start <= other.start && other.end <= this.end;
  }

  public boolean crosses(Span other) {
    /*
    boolean sharesEndpoint = start == other.start || end == other.end;
    if (sharesEndpoint)
      return false;
    boolean nested1 = start <= other.start && other.end <= end;
    if (nested1)
      return false;
    boolean nested2 = other.start <= start && end <= other.end;
    if (nested2)
      return false;
    return overlaps(other);
    */
    Span a = this;
    Span b = other;
    if (a.start < b.start && b.start < a.end && a.end < b.end)
      return true;
    a = other;
    b = this;
    if (a.start < b.start && b.start < a.end && a.end < b.end)
      return true;
    return false;
  }

  public boolean overlaps(Span other) {
    if(end <= other.start) return false;
    if(start >= other.end) return false;
    return true;
  }

  public boolean includes(int wordIdx) {
    return start <= wordIdx && wordIdx < end;
  }

  public String toString() {
    return String.format("<Span %d-%d>", start, end);
  }

  public int hashCode() {
//    int w = end - start;
//    return (w << 16) ^ start;
    return Hash.mix(start, end - start);
  }

  /** Puts a hash in the lower 16 bits of the returned int (may overflow) */
  public int hashCode16() {
//    int w = end - start;
//    return (w << 9) ^ start;
    return hashCode() & 0xFFFF;
  }

  public static Span inverseShortString(String s) {
    int sep = s.indexOf('-');
    if (sep < 0)
      sep = s.indexOf(',');
    if (sep < 0)
      throw new IllegalArgumentException("must match \"\\d+[-,]\\d+\":" + s);
    int start = Integer.parseInt(s.substring(0, sep));
    int end = Integer.parseInt(s.substring(sep + 1, s.length()));
    return getSpan(start, end);
  }

  /** Argument may be null */
  public static String safeShortString(Span s) {
    if (s == null)
      return "null";
    return s.shortString();
  }

  public String shortString() {
    return start + "-" + end;
  }

  public boolean equals(int start, int end) {
    return start == this.start && end == this.end;
  }

  public boolean equals(Object other) {
    if(other instanceof Span) {
      Span s = (Span) other;
      return start == s.start && end == s.end;
    }
    return false;
  }

  public static Span widthOne(int wordIdx) {
    return getSpan(wordIdx, wordIdx+1);
  }

  @Override
  public int compareTo(Span o) {
    int c1 = end - o.end;
    if (c1 != 0) return c1;
    return start - o.start;
  }

  public static Span randomSpan(int n, Random r) {
    int start = r.nextInt(n);
    int maxWidth = n - start;
    int end = start + r.nextInt(maxWidth) + 1;
    assert end <= n;
    return Span.getSpan(start, end);
  }

  // For benchmark
  private static int eqLoop(Span needle1, Span needle2, Span[] haystack) {
    int c = 0;
    for (int i = 0; i < haystack.length; i++)
      if (haystack[i] == needle1 || haystack[i] == needle2)
        c++;
    return c;
  }
  // For benchmark
  private static int equalsLoop(Span needle1, Span needle2, Span[] haystack) {
    int c = 0;
    for (int i = 0; i < haystack.length; i++)
      if (haystack[i].equals(needle1) || haystack[i].equals(needle2))
        c++;
    return c;
  }
  // For benchmark
  public static void main(String[] args) {

    Span needle1 = Span.getSpan(3, 5);
    Span needle2 = Span.getSpan(7, 17);
    Span[] haystack = new Span[20000000];
    Random rand = new Random(9001);
    for (int i = 0; i < haystack.length; i++) {
      int a = rand.nextInt(60);
      int b = rand.nextInt(60);
      if (a == b)
        b++;
      if (a > b) {
        int t = a; a = b; b = t;
      }
      haystack[i] = Span.getSpan(a, b);
    }

    long s1 = System.currentTimeMillis();
    int c = 0;
    for (int i = 0; i < 10; i++)
      c += eqLoop(needle1, needle2, haystack);
    long s2 = System.currentTimeMillis();
    int d = 0;
    for (int i = 0; i < 10; i++)
      d += equalsLoop(needle1, needle2, haystack);
    long s3 = System.currentTimeMillis();

    System.out.println((s2-s1) + " for == and " + (s3-s2) + " for equals");
    assert c == d;
  }

  public static void serCheck() {
    // Check that readResolve is working
    // (so that interning + java serialization is working)
    Span a = Span.getSpan(1, 2);

    Span b = new Span(1, 2);

    assert a != b;
    System.out.println(a == b);

    File f = new File("/tmp/foo");
    FileUtil.serialize(b, f);
    Span bb = (Span) FileUtil.deserialize(f);

    assert bb != b;
    assert bb == a;
    System.out.println(bb == b);
    System.out.println(bb == a);
  }
}

