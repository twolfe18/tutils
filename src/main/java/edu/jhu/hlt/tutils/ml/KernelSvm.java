package edu.jhu.hlt.tutils.ml;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.data.SvmLightDataIO;
import edu.jhu.hlt.tutils.rand.Shuffle;
import edu.jhu.prim.vector.IntDoubleVector;

/**
 * SGD implementation of a special version of the RBF kernel SVM:
 *   K(a,b) = exp(-(a - b) * diag(1 + sigma) * (a - b))
 * which is regularized by:
 *   R(params) = lambda * ||alpha||^2 + (1 - lambda) * ||sigma||^2
 * the total objective is:
 *   min_{params} R(params) + C * hinge
 *
 * Bah! Above is whacky...
 * Objective really is:
 *   max_{\alpha} \sum_i \alpha_i - 1/2 \sum_j \alpha_i \alpha_j y_i y_j K(x_i, x_j)
 * s.t.
 *   0 \le \alpha_i \le C
 *   \sum_i \alpha_i y_i = 0
 *
 * @author travis
 */
public class KernelSvm {

  // Learning
  public double lr = 0.05d;
  public double C = 100d;
  public double margin = 0.5d;
  public double lambda = 0.5d;
  public double omega = 0.1d;   // is actually the one next to sigma
  private Random rand;
  private int pi[], piIdx;   // permutation of data, position

  // Params
  private double[] alpha;
  private double[] sigma;
  private boolean updateSigma = false;

  // Data
  private IntDoubleVector[] x;
  private boolean[] y;

  public KernelSvm(List<SvmLightDataIO.Example> xy, Random rand) {
    int n = xy.size();
    boolean[] y = new boolean[n];
    IntDoubleVector[] x = new IntDoubleVector[n];
    for (int i = 0; i < n; i++) {
      y[i] = xy.get(i).y;
      x[i] = xy.get(i).x;
    }
    init(y, x, rand);
  }
 
  public KernelSvm(boolean[] y, IntDoubleVector[] x, Random rand) {
    init(y, x, rand);
  }

  public void init(boolean[] y, IntDoubleVector[] x, Random rand) {
    if (y.length != x.length)
      throw new RuntimeException();
    this.rand = rand;
    this.margin = 1;
    this.y = y;
    this.x = x;
    this.alpha = new double[y.length];
    Arrays.fill(alpha, 1d / alpha.length);
    int d = 0;
    for (IntDoubleVector xx : x)
      if (d <= xx.getNumImplicitEntries())
        d = xx.getNumImplicitEntries() + 1;
    this.sigma = new double[d];
    this.pi = Shuffle.permutation(y.length, rand);
    this.piIdx = 0;
  }

  public double step(int numSteps) {
    double l = 0;
    for (int i = 0; i < numSteps; i++)
      l += step();
    return l;
  }

  public double step() {
    if (piIdx == pi.length) {
      Shuffle.fisherYates(pi, rand);
      piIdx = 0;
    }
    Computation yhat = predict(x[piIdx]);
    double l = yhat.update(y[piIdx], true);
    piIdx++;
    return l;
  }

  class Computation {
    private IntDoubleVector a;
    private IntDoubleVector[] dd;   // (a-b) * (a-b)
    private double[] ki;            // alpha[i] * K(a,b[i])
    private double k;               // sum(ki)

    public Computation(IntDoubleVector a, int numInstances) {
      this.a = a;
      this.dd = new IntDoubleVector[numInstances];
      this.ki = new double[numInstances];
      this.k = 0;
      for (int i = 0; i < numInstances; i++)
        set(i, x[i]);
    }

    public void set(int i, IntDoubleVector b) {
      dd[i] = a.copy();
      dd[i].subtract(b);
      dd[i].apply((idx,v) -> {
        ki[i] += v * (omega + sigma[i]) * v;
        return v * v;
      });
      ki[i] = Math.exp(-ki[i]);
      k += (y[i] ? alpha[i] : -alpha[i]) * ki[i];
    }

    public double getScore() {
      return k;
    }

    public double loss(boolean y) {
      return update(y, false);
    }

    public double update(boolean y, boolean apply) {
      double update = 0;
      double hinge = 0;
      if (y && k < margin) {
        update = 1;
        hinge = margin - k;
      } else if (!y && k > -margin) {
        update = -1;
        hinge = k + margin;
      }
      if (update != 0 && apply) {
        final double s = update * lr;
        for (int i = 0; i < dd.length; i++) {
          // d/dSigma y * alpha * exp[ -d * (1 + sigma) * d ] = y * alpha * exp[ stuff ] * -d^2
          final double ki = this.ki[i];
          final double ai = alpha[i];
          final double yi = KernelSvm.this.y[i] ? 1 : -1;
          assert ki > 0;
          if (updateSigma) {
            dd[i].apply((j,dd) -> {
              sigma[j] += s * yi * ai * ki * -dd;
              return dd;
            });
          }
          // d/dAlpha y * alpha * exp[ -d * (1 + sigma) * d ] = y * exp[ stuff ]
          alpha[i] += s * yi * ki;
        }
      }
      // R(params) = lambda * ||alpha||^2 + (1 - lambda) * ||sigma||^2
      // d/dAlpha = -2 * lambda * alpha[i]
      double l2a = 0;
      double ac = 2 * lambda * lr / C;
      for (int i = 0; i < alpha.length; i++) {
        l2a += alpha[i] * alpha[i];
        if (apply) alpha[i] -= ac * alpha[i];
      }
      // d/dSigma = -2 * (1-lambda) * sigma[i]
      double l2s = 0;
      if (updateSigma) {
        double as = 2 * (1 - lambda) * lr / C;
        for (int i = 0; i < sigma.length; i++) {
          l2s += sigma[i] * sigma[i];
          if (apply) sigma[i] -= as * sigma[i];
        }
      }
      return hinge + ((lambda * l2a) + (1 - lambda) * l2s) / C;
    }
  }

  public static double sign(double d) {
    if (d < 0) return -1;
    if (d > 0) return 1;
    return 0;
  }

  public Computation predict(IntDoubleVector a) {
    return new Computation(a, pi.length);
  }

  public double score(IntDoubleVector a) {
    return predict(a).getScore();
  }

  public static void main(String[] args) throws Exception {
    File d = new File("/home/travis/code/coref/svm-linear-vs-kernel-experiment");
    Log.info("reading data from " + d.getPath());
    File trainF = new File(d, "data/full.train.txt");
    File testF = new File(d, "data/full.dev.txt");
    List<SvmLightDataIO.Example> train = SvmLightDataIO.parse(trainF, false);
    List<SvmLightDataIO.Example> test = SvmLightDataIO.parse(testF, false);
    int scale = 100;
    test = test.subList(0, Math.min(test.size(), 2 * scale));
    train = train.subList(0, 5000);
    Log.info("nTrain=" + train.size() + " nTest=" + test.size());
    KernelSvm ksvm = new KernelSvm(train, new Random(9001));
    for (int i = 0; i < 50; i++) {
      int nt = 3 * scale;
      double trainLoss = ksvm.step(nt) / nt;
      double testLoss = 0;
      Log.info("computing test loss for " + test.size() + " examples");
      for (SvmLightDataIO.Example e : test)
        testLoss += ksvm.predict(e.x).loss(e.y);
      testLoss /= test.size();
      Log.info("trainLoss=" + trainLoss + " testLoss=" + testLoss);
    }
  }
}
