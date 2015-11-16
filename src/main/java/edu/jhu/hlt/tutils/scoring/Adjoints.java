package edu.jhu.hlt.tutils.scoring;

import java.io.Serializable;

import edu.jhu.prim.vector.IntDoubleDenseVector;
import edu.jhu.prim.vector.IntDoubleVector;

/**
 * A class for doing module-based automatic differentiation or backprop. This
 * specifically represents a node in an expression graph. It is up to you to
 * implement this correctly (including book-keeping needed to update params).
 * Adjoints can obviously compose to make bigger Adjoints.
 *
 * @author travis
 */
public interface Adjoints {

  /** Compute the score of this expression from all of its dependents */
  public double forwards();

  /**
   * Update any parameters which are dependents in this expression graph.
   *
   * NOTE: This method MAY NOT DEPEND ON the current value of the parameters!
   * If we produce N Adjoints when a training example is observed, any
   * permutation of those N adjoints should yield the same parameter values.
   */
  public void backwards(double dErr_dForwards);


  /** Adjoints representing a value which cannot be updated with backwards */
  public static class Constant implements Adjoints, Serializable {
    private static final long serialVersionUID = 6071918010765316387L;
    private final double value;
    public Constant(double v) {
      this.value = v;
    }
    @Override
    public double forwards() {
      return value;
    }
    @Override
    public void backwards(double dErr_dForwards) {
      // No-op
    }
    @Override
    public String toString() {
      return "(Constant " + value + ")";
    }

    public static final Constant ONE = new Constant(1d);
    public static final Constant ZERO = new Constant(0d);
    public static final Constant NEGATIVE_ONE = new Constant(-1d);
    public static final Constant NEGATIVE_INFINITY = new Constant(Double.NEGATIVE_INFINITY);
    public static final Constant POSITIVE_INFINITY = new Constant(Double.POSITIVE_INFINITY);
  }

  /** Sum of other Adjoints */
  public static class Sum implements Adjoints, Serializable {
    private static final long serialVersionUID = -1294541640504248521L;
    private final Adjoints[] items;
    public Sum(Adjoints... items) {
      this.items = items;
    }
    @Override
    public double forwards() {
      double s = 0;
      for (Adjoints a : items)
        s += a.forwards();
      return s;
    }
    @Override
    public void backwards(double dErr_dForwards) {
      for (Adjoints a : items)
        a.backwards(dErr_dForwards);
    }
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("(Sum");
      for (Adjoints a : items)
        sb.append(" " + a);
      sb.append(')');
      return sb.toString();
    }
  }

  public static class Scale implements Adjoints, Serializable {
    private static final long serialVersionUID = -6450629521248111070L;

    public final Adjoints wrapped;
    public final double scale;

    public Scale(double scale, Adjoints wrapped) {
      this.wrapped = wrapped;
      this.scale = scale;
    }

    @Override
    public double forwards() {
      return scale * wrapped.forwards();
    }

    @Override
    public void backwards(double dErr_dForwards) {
      wrapped.backwards(scale * dErr_dForwards);
    }
  }

  // TODO max, prod, neg, div, etc

  public static class SingleHiddenLayer implements Adjoints {
    private IntDoubleVector features;
    private IntDoubleVector[] f2h;     // features -> hidden
    private IntDoubleVector h2o;       // hidden -> output

    // Result of forwards pass
    private IntDoubleVector hiddenActivations;
    private double output;

    public SingleHiddenLayer(IntDoubleVector[] f2h, IntDoubleVector h2o, IntDoubleVector features) {
      this.f2h = f2h;
      this.h2o = h2o;
      this.features = features;
    }

    public int hiddenDimension() {
      return f2h.length;
    }

    @Override
    public double forwards() {
      if (hiddenActivations == null) {
        int D = hiddenDimension();
        hiddenActivations = new IntDoubleDenseVector(D);
        for (int i = 0; i < D; i++) {
          double wx = features.dot(f2h[i]);
          double en = Math.exp(-wx);
          double s = (1 - en) / (1 + en);
//          double s = Math.max(0, wx);
          hiddenActivations.add(i, s);
        }
        output = hiddenActivations.dot(h2o);
      }
      return output;
    }

    @Override
    public void backwards(double dErr_dForwards) {
      // Update h2o (alpha)
      hiddenActivations.apply((i,v) -> {
        h2o.add(i, -dErr_dForwards * v);
        return v;
      });

      // Update f2h (beta)
      int D = hiddenDimension();
      features.apply((j,x) -> {
        for (int i = 0; i < D; i++) {
          double alpha = h2o.get(i);
          double activation = hiddenActivations.get(i);
          double slope = 2 * activation * (1 - activation);
//          double slope = activation > 0 ? 1 : 0;
          double dY_dBeta = alpha * slope * x;
          f2h[i].add(j, -dErr_dForwards * dY_dBeta);
        }
        return x;
      });
    }
  }

  /**
   * Adjoints that are implemented by a dot product between features and parameters.
   */
  public static class Linear implements Adjoints {
    private final IntDoubleVector theta;      // not owned by this class
    private final IntDoubleVector features;
//    private double cachedDotProd;
//    private boolean computedCache = false;

//    private final IntDoubleVector gradSumSq;

    public Linear(IntDoubleVector theta, IntDoubleVector features) {
      if (theta == null)
        throw new IllegalArgumentException("theta cannot be null");
      if (features == null)
        throw new IllegalArgumentException("features cannot be null");
      this.theta = theta;
      this.features = features;
//      this.gradSumSq = new IntDoubleDenseVector();
    }

    @Override
    public double forwards() {
//      if (!computedCache) {
//        cachedDotProd = theta.dot(features);
//        computedCache = true;
//      }
//      return cachedDotProd;
      return theta.dot(features);
    }

    @Override
    public void backwards(double dErr_dForwards) {
      IntDoubleVector update = features.copy();

      update.scale(-dErr_dForwards);
//      double lr = getStepSize();
//      update.apply((i,v) -> {
//        double g = -dErr_dForwards * v;
//        gradSumSq.add(i, g * g);
//        return lr * g / (1e-9 + Math.sqrt(gradSumSq.get(i)));
//      });

      theta.add(update);
    }

    /**
     * You can override this method to introduce interesting
     * learning rates/step sizes
     */
    public double getStepSize() {
      return 1;
    }
  }
}
