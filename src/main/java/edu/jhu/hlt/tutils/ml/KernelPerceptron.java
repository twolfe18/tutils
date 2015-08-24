package edu.jhu.hlt.tutils.ml;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.LineIterator;

import edu.jhu.hlt.tutils.FPR;
import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.hlt.tutils.data.SvmLightDataIO;
import edu.jhu.hlt.tutils.ml.FindBestThreshold.Mode;
import edu.jhu.prim.vector.IntDoubleDenseVector;
import edu.jhu.prim.vector.IntDoubleUnsortedVector;
import edu.jhu.prim.vector.IntDoubleVector;

public class KernelPerceptron {

  /** x1 and x2 must be sorted and ascending */
  public static double diff(int[] x1, int[] x2, boolean jaccard) {
    int a = 0, b = 0;
    int diff = 0;
    while (a < x1.length || b < x2.length) {
      if (a == x1.length) {
        diff++;
        b++;
      } else if (b == x2.length) {
        diff++;
        a++;
      } else if (x1[a] < x2[b]) {
        diff++;
        a++;
      } else if (x1[a] > x2[b]) {
        diff++;
        b++;
      } else {
        a++;
        b++;
      }
    }
    if (jaccard)
      return ((double) diff) / (x1.length + x2.length);
    else
      return diff;
  }

  public static class SV {
    public double alpha;
    public double y;
    public int[] x;     // support non-binary features?
    public SV next;

    public SV(double alpha, double y, IntDoubleUnsortedVector x) {
      this.alpha = alpha;
      this.y = y;
      x.compact();
      this.x = x.getInternalIndices();
    }

    public double partial(IntDoubleUnsortedVector features, double gamma) {
      int[] xi = features.getInternalIndices();
      double diff = diff(x, xi, JACCARD);
      return y * alpha * Math.exp(-gamma * diff);
    }
  }

  public static boolean JACCARD = false;  // true doesn't seem to help
  private IntDoubleVector linearWeights;
  private SV svLLPos, svLLNeg;
  private int nSvPos = 0, nSvNeg = 0;
  private int budgetSvPos = 100;
  private int budgetSvNeg = 100;
  private int nTrainPos = 0, nTrainNeg = 0, nPredict = 0, nTune = 0, nTest = 0;
  private double gamma = 0.1;
  private double margin = 0.1;
  private double intercept = 0;
  private boolean useIntercept = true;
  private double linearL2Penalty = Math.exp(-4);

  private FindBestThreshold thresh;
  private FPR performance;

  public KernelPerceptron(int bPos, int bNeg, double gamma) {
    this.budgetSvPos = bPos;
    this.budgetSvNeg = bNeg;
    this.gamma = gamma;
    this.performance = new FPR(false);
    this.thresh = new FindBestThreshold(Mode.F1);
    this.linearWeights = new IntDoubleDenseVector();
  }

  public int numTrain() {
    return nTrainPos + nTrainNeg;
  }

  public int numTrainPos() {
    return nTrainPos;
  }

  public int numTrainNeg() {
    return nTrainNeg;
  }

  public int numPredicted() {
    return nPredict;
  }

  public int numTune() {
    return nTune;
  }

  public int numTest() {
    return nTest;
  }

  public void add(boolean y, IntDoubleUnsortedVector x) {
    final double yy = y ? 1 : -1;
    if (linearWeights != null) {
      x.apply((i,v) -> {
        linearWeights.add(i, 0.05 * yy * v);
        return v;
      });
      linearWeights.scale(1 - linearL2Penalty);
    }
    if (useIntercept)
      intercept += yy * margin / 10;
    SV nsv = new SV(1, yy, x);
    if (y) {
      nsv.next = svLLPos;
      svLLPos = nsv;
      nSvPos++;
    } else {
      nsv.next = svLLNeg;
      svLLNeg = nsv;
      nSvNeg++;
    }
  }

  public void prune(boolean y, IntDoubleUnsortedVector x) {
    // Lets do forgetron for now
    SV ll;
    if (y) {
      ll = svLLPos;
      nSvPos--;
    } else {
      ll = svLLNeg;
      nSvNeg--;
    }
    while (ll.next.next != null)
      ll = ll.next;
    ll.next = null;
  }

  /** returns true if an update was made */
  public boolean update(boolean y, IntDoubleUnsortedVector x) {
    if (y) nTrainPos++;
    else nTrainNeg++;
    nPredict--;
    double yhat = predict(x);
    if ((y && yhat < margin) || (!y && yhat > -margin)) {
      add(y, x);
      if (nSvPos > budgetSvPos || nSvNeg > budgetSvNeg)
        prune(y, x);
      return true;
    }
    return false;
  }

  // TODO could have a faster version of this which only returns a boolean by
  // doing the pos first, then bailing out as soon as the score dips below 0
  public double predict(IntDoubleUnsortedVector x) {
    nPredict++;
    double smooth = 0.0;
    double sPos = smooth;
    for (SV cur = svLLPos; cur != null; cur = cur.next)
      sPos += cur.partial(x, gamma);
    double sNeg = smooth;
    for (SV cur = svLLNeg; cur != null; cur = cur.next)
      sNeg += cur.partial(x, gamma);
//    return sPos / (nSvPos + smooth) + sNeg / (nSvNeg + smooth);
    double wx = linearWeights == null ? 0 : linearWeights.dot(x);
    return sPos + sNeg + wx + intercept;
  }

  public void tune(boolean y, IntDoubleUnsortedVector x) {
    nPredict--;
    nTune++;
    thresh.add(y, predict(x));
  }

  public FindBestThreshold getThreshold() {
    return thresh;
  }

  /** Don't interleave this with calls to tune */
  public void test(boolean y, IntDoubleUnsortedVector x) {
    nTest++;
    nPredict--;
    double yhat = predict(x);
    double tau = thresh.getBestThreshold();
    performance.accum(y, yhat > tau);
  }

  public FPR getPerformance() {
    return performance;
  }

  public double getAccuracy() {
    return performance.getTP() / nTest;
  }

  public static void main(String[] args) throws Exception {
    // 108k examples
//    File f = new File("/tmp/test-lasvm-wrapper/train.withComments.txt");
    File f = new File("/tmp/test-lasvm-wrapper/train.cheat.txt");
    Map<String, KernelPerceptron> kps = new HashMap<>();
//    for (double gamma : Arrays.asList(0.1, 1d)) {
//      for (int s : Arrays.asList(100, 200, 400)) {
//        for (double r : Arrays.asList(0.5, 1d, 2d)) {
    for (double gamma : Arrays.asList(0.1)) {
      for (int s : Arrays.asList(40)) {
        for (double r : Arrays.asList(1d)) {
          if (JACCARD)
            gamma *= 10;
          KernelPerceptron kp = new KernelPerceptron(s, (int) (s*r), gamma);
          String name = "pos=" + s + "_neg=" + ((int)(s*r)) + "_gamma=" + gamma;
          kps.put(name, kp);
        }
      }
    }
    boolean cheat = false;
    double scale = 1;
    LineIterator li = new LineIterator(FileUtil.getReader(f));
    while (li.hasNext()) {
      SvmLightDataIO.Example e = new SvmLightDataIO.Example(li.next(), true);
      IntDoubleUnsortedVector x = (IntDoubleUnsortedVector) e.x;
      boolean done = false;
      for (KernelPerceptron kp : kps.values()) {
        if (kp.numTrain() < 70000 * scale) {
          kp.update(e.y, x);
        } else if (kp.numTune() < 10000 * scale) {
          if (cheat) kp.update(e.y, x);
          kp.tune(e.y, x);
        } else if (kp.numTest() < 15000 * Math.sqrt(scale)) {
          if (cheat) kp.update(e.y, x);
          kp.test(e.y, x);
        } else {
          done = true;
        }
      }
      if (done)
        break;
    }
    List<String> names = new ArrayList<>();
    names.addAll(kps.keySet());
    Collections.sort(names, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        double a = kps.get(o1).getPerformance().f1();
        double b = kps.get(o2).getPerformance().f1();
        if (a < b)
          return 1;
        if (a > b)
          return -1;
        return 0;
      }
    });
    for (String n : names) {
      KernelPerceptron kp = kps.get(n);
      FPR p = kp.getPerformance();
      System.out.println(n + "\t" + p + " acc=" + kp.getAccuracy());
    }
  }
}
