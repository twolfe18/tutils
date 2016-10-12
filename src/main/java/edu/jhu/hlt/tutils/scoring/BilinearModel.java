package edu.jhu.hlt.tutils.scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fasterxml.jackson.databind.deser.DataFormatReaders;

import edu.jhu.hlt.tutils.IntPair;
import edu.jhu.prim.Primitives.MutableDouble;

/**
 * How to test:
 * I originally wanted to fit: f(y)^t \theta f(x)
 * where f(y) and f(x) produce a sparse high dimensional vector, but where there is some "similarity" between features.
 * Lets produce data according to that model and fit it with this model.
 * 
 * How to mimic "similarity"?
 * Assuming f(y) \in {0,1}^d
 * Partition [d] into k sets.
 * assume that theta[*][i] == theta[*][j] if partition(i) == partition(j)
 *
 *
 * score(y,x) = (A f(y))^t \theta (A f(x))
 * f(*) \in R^D
 * A \in R^{D \times K}
 * \theta \in R^{K \times K}
 *
 * Example of feature in f(x):
 * "headword is John" => {type="headword", value="John"}
 * In other code I had an "offDiag" set of type pairs
 * Here, M hides this.
 * We could write
 *   score(y,x) = \sum_{i,j \in template pairs} (A^i f^i(y))^t \theta^{ij} (A^j f^j(x))
 * Or we can assume \theta is just very sparse.
 * I think the sum notation is better because I don't have a good composite/sparse matrix library.
 * In the example above, i and j would indicate the feature (template) type, i.e. "headword"
 *
 * If you want to approximate simple features like headword(y) == headword(x)
 * You can make A a one-hot random mapping, similar to the feature hashing trick.
 * i.e. it maps from say D=50,000 (words) to K=512 (features)
 * If A maps every row to a random index in K, then there is a 1/512 chance of collision if the headwords don't match.
 * Further, if you use theta1 or theta2, you get different weights based on what headword(y) is.
 * 
 * This extends to using resources like WordNet, where you would write features like: wnSynSet(y) == wnSynSet(x)
 * Put this way, a resource like WN can be though of as a regularizer for the A matrix.
 * If y,x are one-hot word vectors, then we can talk about A_{WN} which projects to another one-hot vector.
 * Could have regularizer which encourages dotOrSim(A_{WN} y, A_{WN} x) => dotOrSim(A y, A x)
 * Same for PPDB, etc.
 *
 * TODO Consider how to do this with Mahalanobis distance instead of dot-product similarity.
 * https://en.wikipedia.org/wiki/Mahalanobis_distance
 */
public class BilinearModel {
  
  public enum Mode {
    MATRIX,
    VECTOR,
    SCALAR,
  }
  
  // Projection features
  // These must live out here for the simple reason that we may need to instantiate
  // the same feature type in two places,
  // e.g. a ProjFeats for {type=headword, value="John"} and one for {type=headword, value="Mary"}
  private double[][][] type2feat2dim2param;
  private Map<IntPair, Object> thetaParams;     // values are double[][], double[], or MutableDouble
  
  public BilinearModel(int numTypes) {
    type2feat2dim2param = new double[numTypes][][];
    thetaParams = new HashMap<>();
  }
  
  public void randInitEmbWeights(Random rand, double sd) {
    for (int i = 0; i < type2feat2dim2param.length; i++) {
      if (type2feat2dim2param[i] == null)
        continue;
      for (int j = 0; j < type2feat2dim2param[i].length; j++)
        for (int k = 0; k < type2feat2dim2param[i][j].length; k++)
          type2feat2dim2param[i][j][k] = rand.nextGaussian() * sd;
    }
  }
  
  public void randInitThetaWeights(Random rand, double sd) {
    for (Object t : thetaParams.values()) {
      if (t instanceof double[][]) {
        double[][] tt = (double[][]) t;
        for (int i = 0; i < tt.length; i++)
          for (int j = 0; j < tt[i].length; j++)
            tt[i][j] = rand.nextGaussian() * sd;
      } else if (t instanceof double[]) {
        double[] tt = (double[]) t;
        for (int i = 0; i < tt.length; i++)
          tt[i] = rand.nextGaussian() * sd;
      } else if (t instanceof MutableDouble) {
        ((MutableDouble) t).v = rand.nextGaussian() * sd;
      } else {
        throw new RuntimeException("wat: " + t);
      }
    }
  }
  
  /**
   * @param type
   * @param domain is how many features belong to this type
   * @param dimension is how big the embedding should be
   */
  public void addFeature(int type, int domain, int dimension) {
    if (type < 0 || type >= type2feat2dim2param.length) {
      throw new IllegalArgumentException("type=" + type + " doesn't match the range "
          + "of types specified at construction: [0," + type2feat2dim2param.length + ")");
    }
    assert type2feat2dim2param[type] == null;
    type2feat2dim2param[type] = new double[domain][dimension];
  }
  
  public void addInteraction(int leftType, int rightType, Mode mode) {
    if (type2feat2dim2param[leftType] == null)
      throw new IllegalStateException("call addFeature first");
    if (type2feat2dim2param[rightType] == null)
      throw new IllegalStateException("call addFeature first");
    int Dy = type2feat2dim2param[leftType][0].length;
    int Dx = type2feat2dim2param[rightType][0].length;
    Object params;
    switch (mode) {
      case MATRIX:
        double[][] p = new double[Dy][Dx];
        for (int i = 0; i < Math.min(Dy, Dx); i++)
          p[i][i] = 1;
        params = p;
        break;
      case VECTOR:
        if (Dy != Dx)
          throw new RuntimeException();
        params = new double[Dy];
        Arrays.fill((double[]) params, 1);
        break;
      case SCALAR:
        params = new MutableDouble(1);
        break;
      default:
        throw new RuntimeException("unkown mode: " + mode);
    }
    IntPair key = new IntPair(leftType, rightType);
    Object old = thetaParams.put(key, params);
    assert old == null : "double alloc key=" + key;
  }

  public ProjFeats score(int type, int[] features, boolean reindex) {
    double[][] A = type2feat2dim2param[type];
    if (A == null) {
//      throw new RuntimeException("did you call addFeature(type=" + type + ")?");
      return null;
    }
    ProjFeats p = new ProjFeats(type, A);
    p.setFeatures(features, reindex);
    return p;
  }
  
  private static Map<Integer, ProjFeats> indexOnType(Iterable<ProjFeats> feats) {
    Map<Integer, ProjFeats> fi = new HashMap<>();
    for (ProjFeats f : feats) {
      Object old = fi.put(f.type, f);
//      assert old == null;
      if (old != null) {
        throw new IllegalArgumentException("each feature type may only have one value:"
            + " type=" + f.type + " old=" + old + " new=" + f);
      }
    }
    return fi;
  }
  
  public static class MissingFeatureException extends Exception {
    private static final long serialVersionUID = 8274651195636251328L;
    public int type;
    public IntPair interaction;
    public MissingFeatureException(int type, IntPair ij) {
      this.type = type;
      this.interaction = ij;
    }
  }
  
  public Adjoints score(List<ProjFeats> fy, List<ProjFeats> fx) throws MissingFeatureException {
    boolean learnTheta = true;
    return score(fy, fx, learnTheta);
  }
  public Adjoints score(List<ProjFeats> fy, List<ProjFeats> fx, boolean learnTheta) throws MissingFeatureException {
    Map<Integer, ProjFeats> fyi = indexOnType(fy);
    Map<Integer, ProjFeats> fxi = indexOnType(fx);
    List<Adjoints> a = new ArrayList<>();
    for (IntPair ij : thetaParams.keySet()) {
      ProjFeats l = fyi.get(ij.first);
      ProjFeats r = fxi.get(ij.second);
      if (l == null)
        throw new MissingFeatureException(ij.first, ij);
      if (r == null)
        throw new MissingFeatureException(ij.second, ij);
      Object params = thetaParams.get(ij);
      Theta t;
      if (params instanceof double[][]) {
        t = new Theta2(ij.first, ij.second, learnTheta, (double[][]) params);
      } else if (params instanceof double[]) {
        t = new Theta1(ij.first, ij.second, learnTheta, (double[]) params);
      } else if (params instanceof MutableDouble) {
        t = new Theta0(ij.first, ij.second, learnTheta, (MutableDouble) params);
      } else {
        throw new RuntimeException("unknown param type: " + params);
      }
      t.setFeatures(l, r);
      a.add(t);
    }
    return new Adjoints.Sum(a);
  }

  
  /**
   * Represents parameters and features (similar to Adjoints, but with a
   * vector-valued output not real valued). Parameters are permanently housed
   * here, but this is a temporary home for features (i.e. use a setter).
   */
  public static class ProjFeats {
    int type;       // e.g. headword
    double[][] A;   // e.g. PARAMS, embeddings, outer index is word
    int[] x;        // e.g. FEATS, [indexOf("John")], i.e. indices of 1-values in {0,1}^D vector
    double[] Ax;    // cache. Needed to ensure that updates to A don't affect gradients
    
//    /**
//     * @param type denotes the feature (template) type. Output embeddings are to be interpreted as (type, vector).
//     * @param D is related to the domain of input features: x \in {0,1}^D
//     * @param K is the dimension of the feature embeddings.
//     */
//    public ProjFeats(int type, int D, int K) {
    public ProjFeats(int type, double[][] A) {
      if (A == null)
        throw new IllegalArgumentException();
      this.type = type;
//      this.A = new double[D][K];
      this.A = A;
    }
    
    public void randInitParams(Random rand, double scale) {
      for (int i = 0; i < A.length; i++)
        for (int j = 0; j < A[i].length; j++)
          A[i][j] = rand.nextGaussian() * scale;
    }
    
    public double[][] getParams() {
      return A;
    }
    
    public int[] getFeatures() {
      return x;
    }
    
    /**
     * This class stores a pointer to x, and thus owns it, so don't modify it later!
     * @param x are feature indices, possibly a single feature.
     * @param reindex says if the feature indices are in [0,D) if false, or unrestricted if true.
     * When true, we assume x are good feature hash values, and re-compute their index modulo D.
     */
    public void setFeatures(int[] x, boolean reindex) {
      this.x = x;
      this.Ax = null;
      if (reindex) {
        for (int i = 0; i < x.length; i++)
          x[i] = Math.floorMod(x[i], A.length);
      }
      for (int i = 0; i < x.length; i++)
        assert x[i] >= 0 && x[i] < A.length;
    }
    
    public double[] readAx(boolean allowCompute) {
      if (Ax == null) {
        if (!allowCompute)
          throw new RuntimeException("you have to call forwards before backwards");
        int D = A[0].length;
        Ax = new double[D];
        for (int i = 0; i < x.length; i++) {
          double[] w = A[x[i]];
          for (int j = 0; j < w.length; j++)
            Ax[j] += w[j];
        }
      }
      return Ax;
    }
    
    public void backwards(double[] dErr_dForwards) {
      for (int i = 0; i < x.length; i++) {
        double[] w  = A[x[i]];
        for (int j = 0; j < w.length; j++)
          w[j] -= dErr_dForwards[j];
      }
    }
  }
  
  /**
   * Params and features are only borrowed.
   * Params are updated.
   * Features are also updated, since they may contain params.
   */
  public static abstract class Theta implements Adjoints {

    int leftType, rightType;
    
    // Features, use setter, not permanently housed here
    protected ProjFeats left, right;
    
    // NOTE: This class cannot own these vectors
    // Only one of these can be non-null
    // If this is non-null, then required(dim(left) == dim(right))
    double[] theta1;
    // Also required(dim(left) == dim(right)), as if theta2 := theta0 * I
    MutableDouble theta0;

    boolean learnTheta;
    
    public Theta(int leftType, int rightType, boolean learnTheta) {//, MutableDouble theta0, double[] theta1, double[][] theta2) {
      this.leftType = leftType;
      this.rightType = rightType;
      this.learnTheta = learnTheta;
    }

    public void setFeatures(ProjFeats left, ProjFeats right) {
      if (left.type != leftType)
        throw new IllegalArgumentException();
      if (right.type != rightType)
        throw new IllegalArgumentException();
      this.left = left;
      this.right = right;
    }
  }
  
  /**
   * One parameter for every pair of embedding dimensions:
   * score(y,x) = \sum_ij theta1[i][j] * f(y)[i] * f(x)[j]
   */
  public static class Theta2 extends Theta {
    double[][] theta2;
    
    // s += y_i * theta_ij * x_j
    // Can regroup to think about how to interpret features/model:
    // s += theta_ij * (y_i * x_j) or
    // s += theta_ij * f_ij

    public Theta2(int leftType, int rightType, boolean learnTheta, double[][] theta2) {
      super(leftType, rightType, learnTheta);
      this.theta2 = theta2;
    }

    /**
     * Call setFeatures first.
     */
    public double forwards() {
      boolean allowCompute = true;
      double[] left = this.left.readAx(allowCompute);
      double[] right = this.right.readAx(allowCompute);
      double s = 0;
      assert theta2.length == left.length;
      assert theta2[0].length == right.length;
      for (int i = 0; i < left.length; i++)
        for (int j = 0; j < right.length; j++)
          s += theta2[i][j] * left[i] * right[j];
      return s;
    }
    
    public void backwards(double dErr_dForwards) {
      boolean allowCompute = false;
      double[] left = this.left.readAx(allowCompute);
      double[] right = this.right.readAx(allowCompute);
      
      // To compute gradient w.r.t. left and right
      double[] gLeft = new double[left.length];
      double[] gRight = new double[right.length];
      
      assert theta2.length == left.length;
      assert theta2[0].length == right.length;
      for (int i = 0; i < left.length; i++) {
        for (int j = 0; j < right.length; j++) {
          gLeft[i] += theta2[i][j] * right[j];
          gRight[j] += theta2[i][j] * left[i];
          if (learnTheta)
            theta2[i][j] -= dErr_dForwards * left[i] * right[j];
        }
      }

      for (int i = 0; i < gLeft.length; i++)
        gLeft[i] *= dErr_dForwards;
      for (int i = 0; i < gRight.length; i++)
        gRight[i] *= dErr_dForwards;

      this.left.backwards(gLeft);
      this.right.backwards(gRight);
    }
  }
  
  /**
   * One parameter for every embedding dimension:
   * score(y,x) = \sum_i theta1[i] * f(y)[i] * f(x)[i]
   */
  public static class Theta1 extends Theta {
    enum Proj {
      NONE,
      L1,
      L2,
      PROB, // like L1 with the additional constraint that theta >= 0
    }
    Proj pmode = Proj.PROB;
    double[] theta1;

    public Theta1(int leftType, int rightType, boolean learnTheta, double[] theta1) {
      super(leftType, rightType, learnTheta);
      this.theta1 = theta1;
    }

    /**
     * Call setFeatures first.
     */
    public double forwards() {
      boolean allowCompute = true;
      double[] left = this.left.readAx(allowCompute);
      double[] right = this.right.readAx(allowCompute);
      double s = 0;
      assert left.length == right.length;
      assert left.length == theta1.length;
      for (int i = 0; i < left.length; i++)
        s += theta1[i] * left[i] * right[i];
      return s;
    }
    
    public void backwards(double dErr_dForwards) {
      boolean allowCompute = false;
      double[] left = this.left.readAx(allowCompute);
      double[] right = this.right.readAx(allowCompute);
      
      // To compute gradient w.r.t. left and right
      double[] gLeft = new double[left.length];
      double[] gRight = new double[right.length];

      assert left.length == right.length;
      assert left.length == theta1.length;
      for (int i = 0; i < left.length; i++)
        gLeft[i] += theta1[i] * right[i];
      for (int i = 0; i < left.length; i++)
        gRight[i] += theta1[i] * left[i];
      
      if (learnTheta) {
        // This is over-parameterized!
        // We could take out a scale parameter and put it into left/right embeddings.
        for (int i = 0; i < left.length; i++)
          theta1[i] -= dErr_dForwards * left[i] * right[i];
        double s = 0;
        switch (pmode) {
          case PROB:
            for (int i = 0; i < theta1.length; i++)
              if (theta1[i] < 0)
                theta1[i] = 0;
          case L1:
            for (int i = 0; i < theta1.length; i++)
              s += Math.abs(theta1[i]);
            for (int i = 0; i < theta1.length; i++)
              theta1[i] /= s;
            break;
          case L2:
            for (int i = 0; i < theta1.length; i++)
              s += theta1[i] * theta1[i];
            s = Math.sqrt(s);
            for (int i = 0; i < theta1.length; i++)
              theta1[i] /= s;
            break;
          case NONE:
            // no-op
            break;
          default:
            throw new RuntimeException("unknown: " + pmode);
        }
      }

      
      for (int i = 0; i < gLeft.length; i++)
        gLeft[i] *= dErr_dForwards;
      for (int i = 0; i < gRight.length; i++)
        gRight[i] *= dErr_dForwards;

      this.left.backwards(gLeft);
      this.right.backwards(gRight);
    }
  }

  /**
   * One parameter:
   * score(y,x) = theta0 * dot(f(y), f(x))
   */
  public static class Theta0 extends Theta {
    MutableDouble theta0;

    public Theta0(int leftType, int rightType, boolean learnTheta, MutableDouble theta0) {
      super(leftType, rightType, learnTheta);
      this.theta0 = theta0;
    }

    /**
     * Call setFeatures first.
     */
    public double forwards() {
      boolean allowCompute = true;
      double[] left = this.left.readAx(allowCompute);
      double[] right = this.right.readAx(allowCompute);
      double s = 0;
      assert left.length == right.length;
      for (int i = 0; i < left.length; i++)
        s += left[i] * right[i];
      s *= theta0.v;
      return s;
    }

    public void backwards(double dErr_dForwards) {
      boolean allowCompute = false;
      double[] left = this.left.readAx(allowCompute);
      double[] right = this.right.readAx(allowCompute);
      
      // To compute gradient w.r.t. left and right
      double[] gLeft = new double[left.length];
      double[] gRight = new double[right.length];

      assert left.length == right.length;
      for (int i = 0; i < left.length; i++) {
        gLeft[i] += theta0.v * right[i];
        gRight[i] += theta0.v * left[i];
        
        // This term can just get folded into the left/right embeddings
//        if (learnTheta)
//          theta0.v -= dErr_dForwards * left[i] * right[i];
      }
      
      for (int i = 0; i < gLeft.length; i++)
        gLeft[i] *= dErr_dForwards;
      for (int i = 0; i < gRight.length; i++)
        gRight[i] *= dErr_dForwards;

      this.left.backwards(gLeft);
      this.right.backwards(gRight);
    }
  }

}
