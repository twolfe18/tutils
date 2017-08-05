package edu.jhu.hlt.tutils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map.Entry;

import com.google.common.hash.HashFunction;

import edu.jhu.hlt.tutils.hash.GuavaHashUtil;
import edu.jhu.hlt.tutils.hash.Hash;

/**
 * Compactly represents an upper bound on the count of elements
 * where you are only allowed to increment counts.
 *
 * http://theory.stanford.edu/~tim/s15/l/l2.pdf
 * Note: This contains a good description of how to implement
 * epsilon-heavy-hitters on top of count-min-sketch, where you
 * maintain approximate counts for every item in the stream,
 * and promote HHs in and out of a heap (which I believe also
 * needs a key->location map/index).
 * 
 * Conservative updates (Estan and Varghese, 2002)
 * https://www.umiacs.umd.edu/~jags/pdfs/LSNLPsketchWAC10.pdf
 * Briefly: say an item hashes to counters with values {4,2,1}.
 * You would say that its sketch count is 1. With a normal
 * increment, you would update all the counters to {5,3,2}, but
 * this is un-necessary: you could just as well only update the
 * min to get to {4,2,2}.
 * 
 * @author travis
 */
public class CountMinSketchNew implements Serializable {
  private static final long serialVersionUID = -4304140822737269498L;

  // This is the seed for all the hash functions used by MaxMinSketch/CountMinSketch
  // Don't change this or else you will jumble all serialized data.
  public static final int SEED = 9001;
  
  public enum CountWidth {
    BITS_8,
    BITS_16,
    BITS_32,
  }

  protected int nhash;
  protected int logb;
  protected long ninc;
  protected boolean conservativeUpdates;
  private CountWidth mode;
  private byte[][]   z8;
  private short[][] z16;
  private int[][]   z32;

  public void writeTo(File f) throws IOException {
    Log.info("writing to " + f.getPath());
    TimeMarker tm = new TimeMarker();
    try (OutputStream os = FileUtil.getOutputStream(f);
        BufferedOutputStream bos = new BufferedOutputStream(os);
        DataOutputStream dos = new DataOutputStream(bos)) {
      dos.writeInt(nhash);
      dos.writeInt(logb);
      dos.writeLong(ninc);
      dos.writeBoolean(conservativeUpdates);
      dos.writeUTF(mode.name());
      switch (mode) {
      case BITS_8:
        for (int i = 0; i < z8.length; i++) {
          for (int j = 0; j < z8[i].length; j++)
            dos.writeByte(z8[i][j]);
          if (tm.enoughTimePassed(3))
            Log.info("wrote " + (i+1) + " of " + nhash + " rows");
        }
        break;
      case BITS_16:
        for (int i = 0; i < z16.length; i++) {
          for (int j = 0; j < z16[i].length; j++)
            dos.writeShort(z16[i][j]);
          if (tm.enoughTimePassed(3))
            Log.info("wrote " + (i+1) + " of " + nhash + " rows");
        }
        break;
      case BITS_32:
        for (int i = 0; i < z32.length; i++) {
          for (int j = 0; j < z32[i].length; j++)
            dos.writeInt(z32[i][j]);
          if (tm.enoughTimePassed(3))
            Log.info("wrote " + (i+1) + " of " + nhash + " rows");
        }
        break;
      }
    }
    Log.info(String.format("wrote %.1f MB in %.2f seconds", ((double)bytes())/(1024*1024), tm.secondsSinceFirstMark()));
  }
  
  public void readFrom(File f) throws IOException {
    Log.info("reading from " + f.getPath());
    TimeMarker tm = new TimeMarker();
    try (InputStream is = FileUtil.getInputStream(f);
        BufferedInputStream bis = new BufferedInputStream(is);
        DataInputStream dis = new DataInputStream(bis)) {
      nhash = dis.readInt();
      logb = dis.readInt();
      ninc = dis.readLong();
      conservativeUpdates = dis.readBoolean();
      mode = CountWidth.valueOf(dis.readUTF());
      Log.info("nhash=" + nhash + " logb=" + logb + " ninc=" + ninc + " conservativeUpdates=" + conservativeUpdates + " mode=" + mode);
      initZ();
      switch (mode) {
      case BITS_8:
        for (int i = 0; i < z8.length; i++) {
          for (int j = 0; j < z8[i].length; j++)
            z8[i][j] = dis.readByte();
          if (tm.enoughTimePassed(3))
            Log.info("read " + (i+1) + " of " + nhash + " rows");
        }
        break;
      case BITS_16:
        for (int i = 0; i < z16.length; i++) {
          for (int j = 0; j < z16[i].length; j++)
            z16[i][j] = dis.readShort();
          if (tm.enoughTimePassed(3))
            Log.info("read " + (i+1) + " of " + nhash + " rows");
        }
        break;
      case BITS_32:
        for (int i = 0; i < z32.length; i++) {
          for (int j = 0; j < z32[i].length; j++)
            z32[i][j] = dis.readInt();
          if (tm.enoughTimePassed(3))
            Log.info("read " + (i+1) + " of " + nhash + " rows");
        }
        break;
      }
    }
    Log.info(String.format("read %.1f MB in %.2f seconds", ((double)bytes())/(1024*1024), tm.secondsSinceFirstMark()));
  }
  
  private void initZ() {
    z8 = null;
    z16 = null;
    z32 = null;
    switch (mode) {
    case BITS_8:
      z8 = new byte[nhash][1<<logb];
      break;
    case BITS_16:
      z16 = new short[nhash][1<<logb];
      break;
    case BITS_32:
      z32 = new int[nhash][1<<logb];
      break;
    }
  }
  
  /**
   * @param nHash higher values tighten probabilistic bound on relative error
   * @param logCountersPerHash higher values tighten bias (expected absolute error)
   * @param conservativeUpdates should basically almost always be true
   */
  public CountMinSketchNew(int nHash, int logCountersPerHash, boolean conservativeUpdates, CountWidth w) {
    if (logCountersPerHash < 0)
      throw new IllegalAccessError();
    this.mode = w;
    this.logb = logCountersPerHash;
    this.nhash = nHash;
    this.ninc = 0;
    this.conservativeUpdates = conservativeUpdates;
    this.initZ();
    Log.info("using " + (bytes()/(1L<<20)) + " MB, requires " + (nhash*logb) + " bits of hash per element");
  }
  
  long bytes() {
    switch (mode) {
    case BITS_8:
      return 1L * nhash * (1L<<logb);
    case BITS_16:
      return 2L * nhash * (1L<<logb);
    case BITS_32:
      return 4L * nhash * (1L<<logb);
    default:
      throw new RuntimeException("wat: " + mode);
    }
  }
  
  int get(int hash, int bucket) {
    switch (mode) {
    case BITS_8: return z8[hash][bucket];
    case BITS_16: return z16[hash][bucket];
    case BITS_32: return z32[hash][bucket];
    default:
      throw new RuntimeException("wat: " + mode);
    }
  }

  int increment(int hash, int bucket) {
    switch (mode) {
    case BITS_8:
      if (z8[hash][bucket] < Byte.MAX_VALUE)
        z8[hash][bucket]++;
      return z8[hash][bucket];
    case BITS_16:
      if (z16[hash][bucket] < Short.MAX_VALUE)
        z16[hash][bucket]++;
      return z16[hash][bucket];
    case BITS_32:
      if (z32[hash][bucket] < Integer.MAX_VALUE)
        z32[hash][bucket]++;
      return z32[hash][bucket];
    default:
      throw new RuntimeException("wat: " + mode);
    }
  }
  
  /**
   * Provides measures of how "saturated" this CMS is.
   */
  public class CapacitySummary {
    double avgColMin;
    int maxColMin;
    int minColMin;
    int max;
    
    public CapacitySummary() {
      int nrow = nhash;
      int ncol = 1<<logb;
      int[] colMins = new int[ncol];
      Arrays.fill(colMins, Integer.MAX_VALUE);
      for (int i = 0; i < nrow; i++) {
        for (int j = 0; j < ncol; j++) {
          colMins[j] = Math.min(colMins[j], get(i,j));
          max = Math.max(max, get(i,j));
        }
      }
      
      long s = minColMin = maxColMin = colMins[0];
      for (int i = 1; i < colMins.length; i++) {
        s += colMins[i];
        minColMin = Math.min(minColMin, colMins[i]);
        maxColMin = Math.max(maxColMin, colMins[i]);
      }
      this.avgColMin = s / ((double) colMins.length);
    }
    
    @Override
    public String toString() {
      return "(CmsCapacity"
          + " nrow=" + nhash
          + " ncol=" + logb
          + " max=" + max
          + " minColMin=" + minColMin
          + " maxColMin=" + maxColMin
          + String.format(" avgColMin=%.2f)", avgColMin);
    }
  }
  
  static int extractHash(int i, byte[] hasheBytes, int logb) {
    int hi = 0;
    int bitStart = logb * i;
    int bitStop = logb * (i+1);
    for (int j = bitStart; j < bitStop; j++) {
      byte b = hasheBytes[j/8];
      b = (byte) ((b >> (j%8)) & 1);
      hi = (hi<<1) + b;
    }
    return hi;
  }
  
  /**
   * @param hashes is the hash of this item, must have at least nHash*logCountersPerHash bits
   * @param increment is whether to increment the count of this item
   * @return the count of the hashed item, after incrementing (i.e. ++x not x++).
   */
  public int apply(byte[] hashes, boolean increment) {
    if (hashes.length*8 < nhash * logb)
      throw new IllegalArgumentException("hashes.length=" + hashes.length + " nhash=" + nhash + " logb=" + logb);
    int m = Integer.MAX_VALUE;
    for (int i = 0; i < nhash; i++) {
      int hi = extractHash(i, hashes, logb);
      if (increment && !conservativeUpdates)
        increment(i, hi);
      m = Math.min(m, get(i, hi));
    }
    if (increment && conservativeUpdates) {
      for (int i = 0; i < nhash; i++) {
        int hi = extractHash(i, hashes, logb);
        if (get(i,hi) == m)
          increment(i, hi);
      }
      m++;
    }
    if (increment)
      ninc++;
    return m;
  }
  
  public long numIncrements() {
    return ninc;
  }
  
  public int numIncrementsInt() {
    if (ninc > Integer.MAX_VALUE)
      throw new RuntimeException();
    return (int) ninc;
  }
  
  public int numHashFunctions() {
    return nhash;
  }
  
  public int logNumBuckets() {
    return logb;
  }
  
  public static class StringCountMinSketchNew extends CountMinSketchNew {
    private static final long serialVersionUID = 3346679142656988448L;

    private transient HashFunction hf;
    private transient Charset cs;

    public StringCountMinSketchNew(int nHash, int logCountersPerHash, boolean conservativeUpdates, CountWidth w) {
      super(nHash, logCountersPerHash, conservativeUpdates, w);
//      hf = Hashing.goodFastHash(nhash * logb);
      hf = GuavaHashUtil.goodFastHash(nhash * logb, SEED);
    }

    public int apply(String item, boolean increment) {
      if (cs == null)
        cs = Charset.forName("UTF-8");
      if (hf == null) {
//        hf = Hashing.goodFastHash(nhash * logb);
        hf = GuavaHashUtil.goodFastHash(nhash * logb, SEED);
      }
      byte[] h = hf.hashString(item, cs).asBytes();
      return apply(h, increment);
    }
  }
  
  public static void main(String[] args) throws Exception {

    int nHash = 8;     // higher values tighten probabilistic bound on relative error
    int logCountersPerHash = 16;    // higher values tighten bias (expected absolute error)
    boolean conservativeUpdates = true;
    CountWidth w = CountWidth.BITS_32;
    StringCountMinSketchNew cms = new StringCountMinSketchNew(nHash, logCountersPerHash, conservativeUpdates, w);
    Counts<String> exact = new Counts<>();

//    File f = new File("/tmp/english.txt");
    File f = new File("data/english.txt");
    Log.info("reading " + f.getPath());
    try (BufferedReader r = FileUtil.getReader(f)) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        String[] toks = line.split("\\s+");
        for (int i = 0; i < toks.length; i++) {
          cms.apply(toks[i], true);
          exact.increment(toks[i]);
        }
      }
    }
    
    // Estimate error
    long tae = 0;
    long[] maeN = new long[10];
    double tre = 0;
    double[] mreN = new double[10];
    for (Entry<String, Integer> e : exact.entrySet()) {
      int ae = cms.apply(e.getKey(), false) - e.getValue();
      assert ae >= 0;
      tae += ae;
      double re = ae / ((double) e.getValue());
      tre += re;
      
      for (int i = 0; i < mreN.length; i++) {
        int thresh = 1<<i;
        if (e.getValue() <= thresh)
          break;
        mreN[i] = Math.max(mreN[i], re);
        maeN[i] = Math.max(maeN[i], ae);
      }
      
      if (Hash.hash(e.getKey()) % 100 == 0) {
        System.out.printf("%-24s %.2f % 3d % 5d\n", e.getKey(), re, ae, e.getValue());
      }
    }

    System.out.printf("avgAbsErr=%.3f avgRelErr=%.3f\n",
        ((double) tae) / exact.numNonZero(), tre / exact.numNonZero());
    
    for (int i = 0; i < mreN.length; i++)
      System.out.printf("maxRelErr|c>%d\t%.3f\n", 1<<i, mreN[i]);

    for (int i = 0; i < mreN.length; i++)
      System.out.printf("maxAbsErr|c>%d\t% 3d\n", 1<<i, maeN[i]);
    
    Log.info("done");
  }
}
