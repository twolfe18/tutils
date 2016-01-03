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

  public interface ICaching extends Adjoints {
    // forwards has its value cached for the life of the instance
  }

  /** Adjoints representing a value which cannot be updated with backwards */
  public static class Constant implements Adjoints, Serializable, ICaching {
    private static final long serialVersionUID = 6071918010765316387L;
    protected final double value;
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

  public static class NamedConstant extends Constant implements ICaching {
    private static final long serialVersionUID = 6211238756720056089L;
    private String name;
    public NamedConstant(String name, double constant) {
      super(constant);
      this.name = name;
    }
    @Override
    public String toString() {
      return String.format("(%s %.2f)", name, value);
    }
  }

  /** Sum of other Adjoints */
  public static final class Sum implements Adjoints, Serializable {
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
  public static Adjoints sum(Adjoints... summands) {
    return new Sum(summands);
  }

  public static final class CachingBinarySum implements Adjoints, Serializable, ICaching {
    private static final long serialVersionUID = 1409636835431295798L;
    private Adjoints left, right;
    private double forwards;
    public CachingBinarySum(Adjoints left, Adjoints right) {
      this.left = left;
      this.right = right;
      this.forwards = left.forwards() + right.forwards();
    }
    @Override
    public double forwards() {
      return forwards;
    }
    @Override
    public void backwards(double dErr_dForwards) {
      left.backwards(dErr_dForwards);
      right.backwards(dErr_dForwards);
    }
    @Override
    public String toString() {
      return "(CBSum " + left + " + " + right + ")";
    }
  }
  public static final class CachingTernarySum implements Adjoints, Serializable, ICaching {
    private static final long serialVersionUID = 5628255488165277061L;
    private Adjoints a, b, c;
    private double forwards;
    public CachingTernarySum(Adjoints a, Adjoints b, Adjoints c) {
      this.a = a;
      this.b = b;
      this.c = c;
      this.forwards = a.forwards() + b.forwards() + c.forwards();
    }
    @Override
    public double forwards() {
      return forwards;
    }
    @Override
    public void backwards(double dErr_dForwards) {
      a.backwards(dErr_dForwards);
      b.backwards(dErr_dForwards);
      c.backwards(dErr_dForwards);
    }
    @Override
    public String toString() {
      return "(CTSum " + a + " + " + b + " + " + c + ")";
    }
  }
  public static ICaching cacheSum(Adjoints a, Adjoints b) {
    return new CachingBinarySum(a, b);
  }
  public static ICaching cacheSum(Adjoints a, Adjoints b, Adjoints c) {
    return new CachingTernarySum(a, b, c);
  }

  /**
   * Computes forwards() once during the life of an instance. Forwards all
   * calls to backwards().
   */
  public static class Caching implements Adjoints, ICaching {
    private final Adjoints wrapped;
    private double forwards;
    private boolean computed;
    public Caching(Adjoints wrapped) {
      this.wrapped = wrapped;
      this.computed = false;
    }
    @Override
    public double forwards() {
      if (!computed) {
        forwards = wrapped.forwards();
        computed = true;
      }
      return forwards;
    }
    @Override
    public void backwards(double dErr_dForwards) {
      wrapped.backwards(dErr_dForwards);
    }
    @Override
    public String toString() {
      return "(Caching " + wrapped + ")";
    }
  }

  /** Will wrap the given adjoints in a caching instance of this is not already a caching instance */
  public static ICaching cacheIfNeeded(Adjoints a) {
    if (a instanceof ICaching)
      return (ICaching) a;
    return new Caching(a);
  }

  /**
   * a.k.a. HideStructure. Just for debugging, in toString, just show forwards()
   * for the wrapped adjoints
   */
  public static class OnlyShowScore extends Caching {
    private String name;

    public OnlyShowScore(Adjoints wrapped) {
      this(null, wrapped);
    }

    public OnlyShowScore(String name, Adjoints wrapped) {
      super(wrapped);
      this.name = name;
    }

    @Override
    public String toString() {
      if (name == null) {
        return String.format("(OnlyShowScore %.2f)", forwards());
      } else {
        return String.format("(%s %.2f)", name, forwards());
      }
    }
  }

  /**
   * Scales some given adjoints by a constant. Good for sign flips, etc.
   */
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

    @Override
    public String toString() {
      return "(Scale " + scale + " * " + wrapped + ")";
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
   * 
   * TODO Steal the sparse-dot product code from fnparse...Vector Adjoints.
   */
  public static class Linear implements Adjoints {
    private final IntDoubleVector theta;      // not owned by this class
    private final IntDoubleVector features;

    public Linear(IntDoubleVector theta, IntDoubleVector features) {
      if (theta == null)
        throw new IllegalArgumentException("theta cannot be null");
      if (features == null)
        throw new IllegalArgumentException("features cannot be null");
      this.theta = theta;
      this.features = features;
    }

    @Override
    public double forwards() {
      return theta.dot(features);
    }

    @Override
    public void backwards(double dErr_dForwards) {
      IntDoubleVector update = features.copy();
      update.scale(-dErr_dForwards);
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
