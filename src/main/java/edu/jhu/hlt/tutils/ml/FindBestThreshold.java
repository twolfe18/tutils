package edu.jhu.hlt.tutils.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FindBestThreshold {

  public class Instance {
    public final boolean y;
    public final double score;
    public Instance(boolean y, double score) {
      this.y = y;
      this.score = score;
    }
    @Override
    public String toString() {
      return String.format("(%s, %.2f)", y ? "+1" : "-1", score);
    }
  }

  enum Mode { F1, ACC }

  private List<Instance> instances;
  private Mode mode;
  private int nPos, nNeg;
  private double curBestThresh;
  private double penaltyFromZero = Math.exp(-8);
  private boolean clean;

  public FindBestThreshold(Mode mode) {
    this.mode = mode;
    instances = new ArrayList<>();
    nPos = 0;
    nNeg = 0;
    curBestThresh = 0;
    clean = true;
  }

  public int numInstances() {
    return nPos + nNeg;
  }

  public void clear() {
    instances.clear();
    nPos = 0;
    nNeg = 0;
    clean = true;
  }

  public void add(boolean y, double score) {
    clean = false;
    if (y) nPos++;
    else nNeg++;
    this.instances.add(new Instance(y, score));
  }

  /** Memoizes */
  public double getBestThreshold() {
    if (!clean) {
      curBestThresh = computeBestThreshold();
      clean = true;
    }
    return curBestThresh;
  }

  /** Computes every time */
  public double computeBestThreshold() {
    // Sort by model score
    Collections.sort(instances, new Comparator<Instance>() {
      @Override
      public int compare(Instance o1, Instance o2) {
        if (o1.score < o2.score)
          return -1;
        if (o1.score > o2.score)
          return 1;
        return 0;
      }
    });
    // Start from "say no to everything" and work your way up to "say yes to everything"
    int fp, fn, tp;
    if (instances.get(0).y) {
      tp = nPos - 1;
      fp = nNeg;
      fn = 1;
    } else {
      tp = nPos;
      fp = nNeg - 1;
      fn = 0;
    }
    double bestThresh = 0;
    double bestScore = 0;
    int n = instances.size();
    System.out.println("nPos=" + nPos + " nNeg=" + nNeg + " n=" + n);
    assert n == nPos + nNeg;
    for (int i = 1; i < n - 1; i++) {
      // We put the threshold between i and i+1
      double thresh = (instances.get(i).score + instances.get(i+1).score) / 2;
      if (instances.get(i).y) {
        fn++;
        tp--;
      } else {
        fp--;
      }
      double p = ((double) tp) / (tp + fp);
      double r = ((double) tp) / (tp + fn);
      double f = 2 * p * r / (p + r);

      int tn = (i+1) - fn;
      int correct = tn + tp;
      double acc = ((double) correct) / n;

      double pen = penaltyFromZero * thresh * thresh;

      if (i % 1000 == 0) {
        System.out.println("thresh=" + thresh + " f1=" + f
            + " acc=" + acc + " tn=" + tn + " tp=" + tp
            + " pen=" + pen);
      }

      double score = (mode == Mode.F1 ? f : acc) - pen;
      if (score > bestScore) {
        bestScore = score;
        bestThresh = thresh;
      }
    }
    System.out.println("best threshold for " + mode + ": thresh=" + bestThresh + " score=" + bestScore);
    return bestThresh;
  }

}
