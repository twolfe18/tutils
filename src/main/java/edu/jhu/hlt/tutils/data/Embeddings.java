package edu.jhu.hlt.tutils.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.Serializable;

import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.MultiAlphabet;
import edu.jhu.hlt.tutils.ling.Language;
import edu.jhu.prim.map.IntIntHashMap;

public class Embeddings implements Serializable {
  private static final long serialVersionUID = -1602073535396837260L;

  public static final boolean USE_DOUBLE_AS_DEFAULT = false;
  public static final int DEFAULT_VOCAB_LIMIT = 400_000;

  private MultiAlphabet alph;       // String -> I1
  private IntIntHashMap mux;        // I1 -> I2
  private double[][] embeddings;    // I2 -> Vector
  private float[][] embeddingsF;    // I2 -> Vector

  public static Embeddings getEmbeddings(Language lang, MultiAlphabet alph) {
    if (lang == Language.ZH) {
      return getEmbFromTextFile(
          new File("data/chinese/gigaword_word_vectors"),
          alph, USE_DOUBLE_AS_DEFAULT, DEFAULT_VOCAB_LIMIT);
    }
    if (lang == Language.EN) {
      return getEmbFromTextFile(
          new File("data/embeddings/word2vec/word2vec-filter-3m-300.txt.gz"),
          alph, USE_DOUBLE_AS_DEFAULT, DEFAULT_VOCAB_LIMIT);
    }
    throw new RuntimeException("don't have any embeddings for " + lang);
  }

  /**
   * Expects a file with a first line of "<vocab size> <vector dimension>"
   * followed by many lines of "<word string> <vector value>+".
   *
   * It is assumed that these are sorted by "whether this word should be included",
   * and limit says how many to take at the most.
   */
  private static Embeddings getEmbFromTextFile(File f, MultiAlphabet alph, boolean useDoubles, int limit) {
    Log.info("reading embeddings from " + f.getPath());
    try (BufferedReader r = FileUtil.getReader(f)) {
      String header = r.readLine();
      String[] toks = header.split("\\s+");
      assert toks.length == 2;
      int vocabSize = Integer.parseInt(toks[0]);
      int dimension = Integer.parseInt(toks[1]);
      if (limit < dimension) {
        Log.warn("limiting to the first " + limit + " words of " + dimension + " in " + f.getPath());
        dimension = limit;
      }
      Embeddings e = new Embeddings();
      if (useDoubles)
        e.embeddings = new double[vocabSize][dimension];
      else
        e.embeddingsF = new float[vocabSize][dimension];
      e.alph = alph;
      e.mux = new IntIntHashMap();
      int i2 = 0;
      for (int i = 0; i < vocabSize && r.ready(); i++) {
        String line = r.readLine();
        toks = line.split("\\s+");
        assert toks.length == dimension + 1;
        if (e.embeddings == null) {
          for (int j = 0; j < dimension; j++)
            e.embeddingsF[i2][j] = Float.parseFloat(toks[j + 1]);
        } else {
          for (int j = 0; j < dimension; j++)
            e.embeddings[i2][j] = Double.parseDouble(toks[j + 1]);
        }
        int i1 = e.alph.word(toks[0]);
        e.mux.put(i1, i2);
        i2++;
      }
      Log.info("done, mux.size=" + e.mux.size() + " i2=" + i2);
      return e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public int dimension() {
    return embeddings == null ? embeddingsF[0].length : embeddings[0].length;
  }

  public boolean usesDoubles() {
    return embeddings != null;
  }

  public void l2Normalize() {
    for (int i = 0; i < embeddings.length; i++)
      l2Normalize(embeddings[i]);
  }

  public static void l2Normalize(float[] vec) {
    double l2 = 0;
    for (int j = 0; j < vec.length; j++) {
      double v = vec[j];
      l2 += v * v;
    }
    l2 = Math.sqrt(l2);
    if (Math.abs(l2) > 1e-10)
      for (int j = 0; j < vec.length; j++)
        vec[j] /= l2;
  }

  public static void l2Normalize(double[] vec) {
    double l2 = 0;
    for (int j = 0; j < vec.length; j++) {
      double v = vec[j];
      l2 += v * v;
    }
    l2 = Math.sqrt(l2);
    if (Math.abs(l2) > 1e-10)
      for (int j = 0; j < vec.length; j++)
        vec[j] /= l2;
  }

  /** Returns 0 if the the dot produce is 0 */
  public static double cosineSim(double[] a, double[] b) {
    if (a.length != b.length)
      throw new IllegalArgumentException();
    double dot = 0;
    double aa = 0, bb = 0;
    for (int i = 0; i < a.length; i++) {
      double ai = a[i];
      double bi = b[i];
      dot += ai * bi;
      aa += ai * ai;
      bb += bi * bi;
    }
    if (Math.abs(dot) < 1e-10)
      return dot;
    return dot / (Math.sqrt(aa) * Math.sqrt(bb));
  }

  /** Returns 0 if the the dot produce is 0 */
  public static double cosineSim(float[] a, float[] b) {
    if (a.length != b.length)
      throw new IllegalArgumentException();
    double dot = 0;
    double aa = 0, bb = 0;
    for (int i = 0; i < a.length; i++) {
      double ai = a[i];
      double bi = b[i];
      dot += ai * bi;
      aa += ai * ai;
      bb += bi * bi;
    }
    if (Math.abs(dot) < 1e-10)
      return dot;
    return dot / (Math.sqrt(aa) * Math.sqrt(bb));
  }

  /** Will work with floats (via up-conversion) or doubles */
  public double[] getVectorSum(String... word) {
    int[] i1 = new int[word.length];
    for (int i = 0; i < word.length; i++)
      i1[i] = alph.word(word[i]);
    return getVectorSum(i1);
  }

  /** Will work with floats (via up-conversion) or doubles */
  public double[] getVectorSum(int... i1) {
    double[] vec = new double[dimension()];
    if (embeddings == null) {
      for (int i = 0; i < i1.length; i++) {
        double[] vi = getVector(i1[i]);
        assert vi.length == vec.length;
        for (int j = 0; j < vi.length; j++)
          vec[j] += vi[j];
      }
    } else {
      for (int i = 0; i < i1.length; i++) {
        double[] vi = getVector(i1[i]);
        assert vi.length == vec.length;
        for (int j = 0; j < vi.length; j++)
          vec[j] += vi[j];
      }
    }
    return vec;
  }

  /** Only works if using floats */
  public float[] getVectorSumF(String... word) {
    int[] i1 = new int[word.length];
    for (int i = 0; i < word.length; i++)
      i1[i] = alph.word(word[i]);
    return getVectorSumF(i1);
  }

  /** Only works if using floats */
  public float[] getVectorSumF(int... i1) {
    float[] vec = new float[dimension()];
    for (int i = 0; i < i1.length; i++) {
      float[] vi = getVectorF(i1[i]);
      assert vi.length == vec.length;
      for (int j = 0; j < vi.length; j++)
        vec[j] += vi[j];
    }
    return vec;
  }

  /** Only works if using doubles */
  public double[] getVector(String word) {
    int i1 = alph.word(word);
    return getVector(i1);
  }

  /** Only works if using doubles */
  public double[] getVector(int i1) {
    int i2 = mux.get(i1);
    return embeddings[i2];
  }

  /** Only works if using floats */
  public float[] getVectorF(String word) {
    int i1 = alph.word(word);
    return getVectorF(i1);
  }

  /** Only works if using floats */
  public float[] getVectorF(int i1) {
    int i2 = mux.get(i1);
    return embeddingsF[i2];
  }

}
