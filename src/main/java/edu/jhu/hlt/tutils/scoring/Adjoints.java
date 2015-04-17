package edu.jhu.hlt.tutils.scoring;

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

  /** Update any parameters which are dependents in this expression graph */
  public void backwards(double dErr_dForwards);

  /** Adjoints representing a value which cannot be updated with backwards */
  public static class Constant implements Adjoints {
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

    public static final Constant ONE = new Constant(1d);
    public static final Constant ZERO = new Constant(0d);
    public static final Constant NEGATIVE_ONE = new Constant(-1d);
    public static final Constant NEGATIVE_INFINITY = new Constant(Double.NEGATIVE_INFINITY);
    public static final Constant POSITIVE_INFINITY = new Constant(Double.POSITIVE_INFINITY);
  }

  /** Sum of other Adjoints */
  public static class Sum implements Adjoints {
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
  }

  // TODO max, prod, neg, div, etc

  /**
   * Adjoints that are implemented by a dot product between features and parameters.
   */
  public static class Linear implements Adjoints {
    private final IntDoubleVector theta;      // not owned by this class
    private final IntDoubleVector features;
    private double cachedDotProd;
    private boolean computedCache = false;

    public Linear(IntDoubleVector theta, IntDoubleVector features) {
      this.theta = theta;
      this.features = features;
    }

    @Override
    public double forwards() {
      if (!computedCache) {
        cachedDotProd = theta.dot(features);
        computedCache = true;
      }
      return cachedDotProd;
    }

    @Override
    public void backwards(double dErr_dForwards) {
      throw new RuntimeException("implement me");
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
