package edu.jhu.hlt.tutils.hash;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

import edu.jhu.hlt.tutils.ExperimentProperties;
import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.TimeMarker;

public class Hash {

  public static int hash(String s) {
    if (s == null)
      return 0;
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
  public static long sha256(String s) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      messageDigest.update(s.getBytes());
      byte[] b = messageDigest.digest();
//      String e = new String(b);
//      return e.hashCode();
      long h = b[0];
      h = (h << 8) ^ b[1];
      h = (h << 8) ^ b[2];
      h = (h << 8) ^ b[3];
      h = (h << 8) ^ b[4];
      h = (h << 8) ^ b[5];
      h = (h << 8) ^ b[6];
      h = (h << 8) ^ b[7];
      return h;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static long fileName(File f) {
    return sha256(f.getPath());
  }

  /**
   * Reads lines from an input file, hashes them, reports the original and
   * the hash to an output file, and logs the time it took.
   */
  public static void main(String[] args) throws IOException {
    ExperimentProperties config = ExperimentProperties.init(args);
    File i = config.getExistingFile("input");

    if (config.containsKey("sha256Output")) {
      File sha256Output = config.getFile("sha256Output");
      TimeMarker t = new TimeMarker();
      int n = 0;
      try (BufferedReader r = FileUtil.getReader(i);
          BufferedWriter w = FileUtil.getWriter(sha256Output)) {
        for (String line = r.readLine(); line != null; line = r.readLine()) {
          long sha256 = sha256(line);
          w.write(Long.toHexString(sha256) + "\t" + line);
          w.newLine();
          if (t.enoughTimePassed(15))
            System.out.println("[sha256] converted " + n + " lines in " + t.secondsSinceFirstMark());
        }
      }
      Log.info("n=" + n + " sha256Time=" + t.secondsSinceFirstMark() + " sha256AvgTime=" + t.secondsPerMark());
    }

    if (config.containsKey("jenkinsOutput")) {
      File jenkinsOutput = config.getFile("jenkinsOutput");
      TimeMarker t = new TimeMarker();
      int n = 0;
      try (BufferedReader r = FileUtil.getReader(i);
          BufferedWriter w = FileUtil.getWriter(jenkinsOutput)) {
        for (String line = r.readLine(); line != null; line = r.readLine()) {
          n++;
          long jenkins = hash(line);
          w.write(Long.toHexString(jenkins) + "\t" + line);
          w.newLine();
          if (t.enoughTimePassed(15))
            System.out.println("[jenkins] converted " + n + " lines in " + t.secondsSinceFirstMark());
        }
      }
      Log.info("n=" + n + " jenkinsTime=" + t.secondsSinceFirstMark() + " jenkinsAvgTime=" + t.secondsPerMark());
    }
  }
}
