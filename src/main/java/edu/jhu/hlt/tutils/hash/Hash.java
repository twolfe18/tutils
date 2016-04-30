package edu.jhu.hlt.tutils.hash;

import java.io.File;
import java.security.MessageDigest;

public class Hash {

  public static int hash(String s) {
    return hash(s, 0, s.length());
  }
  public static int hash(String s, int start, int end) {
    int h = 0;
    for (int i = start; i < end; i++) {
      char c = s.charAt(i);
      h += c;
      h += (h << 10);
      h ^= (h >> 6);
    }
    h += (h << 3);
    h ^= (h >> 11);
    h += (h << 15);
    return h;
  }

  /**
   * Adapted from Jenkins hash:
   * https://en.wikipedia.org/wiki/Jenkins_hash_function
   */
  public static int mix(int a, int b) {
    int h = 0;

    // Pretends as if you read a and then b
    // a
    h += a & 0xFF;
    h += h << 10;
    h ^= h >> 6;
    h += a & 0xFF00;
    h += h << 10;
    h ^= h >> 6;
    h += a & 0xFF0000;
    h += h << 10;
    h ^= h >> 6;
    h += a & 0xFF000000;
    h += h << 10;
    h ^= h >> 6;
    // b
    h += b & 0xFF;
    h += h << 10;
    h ^= h >> 6;
    h += b & 0xFF00;
    h += h << 10;
    h ^= h >> 6;
    h += b & 0xFF0000;
    h += h << 10;
    h ^= h >> 6;
    h += b & 0xFF000000;
    h += h << 10;
    h ^= h >> 6;

    h += h << 3;
    h ^= h >> 11;
    h += h << 15;
    return h;
  }

  public static long mix64(long a, long b) {
    long h = 0;
    long c;
    for (int i = 0; i < 64; i += 8) {
      long mask = 0xFFL << i;
      // a
      c = (a & mask) >>> i;
      h += c;
      h += h << 10;
      h ^= h >>> 6;
      // b
      c = (b & mask) >>> i;
      h += c;
      h += h << 10;
      h ^= h >>> 6;
    }
    h += h << 3;
    h ^= h >>> 11;
    h += h << 15;
    return h;
  }

  @SafeVarargs
  public static <T> int mixHashcodes(T... items) {
    int[] hc = new int[items.length];
    for (int i = 0; i < hc.length; i++)
      hc[i] = items[i].hashCode();
    return mix(hc);
  }

  public static int mix(int... items) {
    if (items.length == 0)
      return 0;
    if (items.length == 1)
      return items[0];
    long h = mix64(items[0], items[1]);
    for (int i = 2; i < items.length; i++)
      h = mix64(h, items[i]);
    return (int) (h & 0xFFFFFFFFL);
  }
  public static long mix64(long... items) {
    if (items.length == 0)
      return 0;
    if (items.length == 1)
      return items[0];
    long h = mix64(items[0], items[1]);
    for (int i = 2; i < items.length; i++)
      h = mix64(h, items[i]);
    return h;
  }

  /** A slow but decent hash for strings */
  public static int sha256(String s) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      messageDigest.update(s.getBytes());
      String e = new String(messageDigest.digest());
      return e.hashCode();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static int fileName(File f) {
    return sha256(f.getPath());
  }
}
