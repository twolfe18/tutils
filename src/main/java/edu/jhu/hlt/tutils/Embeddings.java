package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;

import edu.jhu.prim.map.IntIntHashMap;

public class Embeddings {

  private MultiAlphabet alph;       // String -> I1
  private IntIntHashMap mux;        // I1 -> I2
  private double[][] embeddings;    // I2 -> Vector

  /**
   * Expects a file with a first line of "<vocab size> <vector dimension>"
   * followed by many lines of "<word string> <vector value>+".
   */
  public static Embeddings getVioletEmbeddings(File f, MultiAlphabet alph) {
    try (BufferedReader r = FileUtil.getReader(f)) {
      String header = r.readLine();
      String[] toks = header.split("\\s+");
      assert toks.length == 2;
      int vocabSize = Integer.parseInt(toks[0]);
      int dimension = Integer.parseInt(toks[1]);
      Embeddings e = new Embeddings();
      e.embeddings = new double[vocabSize][dimension];
      e.alph = alph;
      e.mux = new IntIntHashMap();
      int i2 = 0;
      while (r.ready()) {
        String line = r.readLine();
        toks = line.split("\\s+");
        assert toks.length == dimension + 1;
        for (int j = 0; j < dimension; j++)
          e.embeddings[i2][j] = Double.parseDouble(toks[j + 1]);
        int i1 = e.alph.word(toks[0]);
        e.mux.put(i1, i2);
        i2++;
      }
      return e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void l2Normalize() {
    for (int i = 0; i < embeddings.length; i++)
      l2Normalize(embeddings[i]);
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

  public double[] getVectorSum(String... word) {
    int[] i1 = new int[word.length];
    for (int i = 0; i < word.length; i++)
      i1[i] = alph.word(word[i]);
    return getVectorSum(i1);
  }

  public double[] getVectorSum(int... i1) {
    double[] vec = new double[embeddings[0].length];
    for (int i = 0; i < i1.length; i++) {
      double[] vi = getVector(i1[i]);
      assert vi.length == vec.length;
      for (int j = 0; j < vi.length; j++)
        vec[j] += vi[j];
    }
    return vec;
  }

  public double[] getVector(String word) {
    int i1 = alph.word(word);
    return getVector(i1);
  }

  public double[] getVector(int i1) {
    int i2 = mux.get(i1);
    return embeddings[i2];
  }

}
