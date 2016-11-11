package edu.jhu.hlt.tutils;

import java.util.Arrays;

/**
 * Concrete UUIDs use Strings internally, where you pay 2 bytes * (32+4) chars =
 * 72 bytes, not counting the extra pointers. Java uses two longs, which is only
 * 16, but we can do a little better when we store an entire list.
 *
 * @author travis
 */
public class EfficientUuidList {
  private int size;
  private long[] buf;
  
  public EfficientUuidList(int capacity) {
    size = 0;
    buf = new long[2*capacity];
  }
  
  public int size() {
    return size;
  }
  
  public void add(String uuidString) {
    if (size*2 == buf.length)
      buf = Arrays.copyOf(buf, buf.length+2);
    java.util.UUID u = java.util.UUID.fromString(uuidString);
    buf[2*size+0] = u.getMostSignificantBits();
    buf[2*size+1] = u.getLeastSignificantBits();
    size++;
  }
  
  public java.util.UUID get(int i) {
    if (i < 0 || i >= size)
      throw new IllegalArgumentException();
    java.util.UUID u = new java.util.UUID(buf[2*i+0], buf[2*i+1]);
    return u;
  }
  
  public String getString(int i) {
    return get(i).toString();
  }
  
  public static void simpleTest() {
    String u = "a3550bda-583c-6724-902e-78fba4bad39c";
    EfficientUuidList l = new EfficientUuidList(1);
    if (l.size() != 0)
      throw new RuntimeException("bug!");
    l.add(u);
    if (!u.equals(l.getString(0)))
      throw new RuntimeException("bug!");
    if (l.size() != 1)
      throw new RuntimeException("bug!");
    String u2 = "504d9937-d74e-4026-f3d6-1f30d9bd013d";
    l = new EfficientUuidList(2);
    l.add(u2);
    l.add(u);
    if (!u2.equals(l.getString(0)))
      throw new RuntimeException("bug!");
    if (!u.equals(l.getString(1)))
      throw new RuntimeException("bug!");
    if (l.size() != 2)
      throw new RuntimeException("bug!");
  }
}