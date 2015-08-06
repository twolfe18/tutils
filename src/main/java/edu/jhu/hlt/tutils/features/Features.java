package edu.jhu.hlt.tutils.features;

import edu.jhu.prim.tuple.Pair;

/**
 * Semantics need to be clarified, but the primary goal of this class is to move
 * {@link Context#flatIndex} into {@link Context#feats}. If no learning is being
 * preformed, then this can be replaced with just reading from {@link
 * Context#flatIndex}, looking up a weight, and adding to {@link
 * Context#scores}.
 *
 * @author travis
 */
public class Features extends TemplateM {
  private static final long serialVersionUID = 1618078792691007876L;

  @Override
  public void apply(Context c) {
    int x = c.getFlatIndex();
    int y = 0;
    double z = Math.exp(-c.muting);
    c.updateLabelScore(x, y, z);
  }

  @Override
  public void unapply(Context c) {
    // Doesn't unapply because you want to keep the features stored in Context
    //c.unUpdateLabelScore();
  }

  /**
   * Read IntDoubleVector and set scores.
   *
   * This should be a leaf in the TemplateTree. Looks up the weights of {@link
   * Template}s extracted so far and adds them into the {@link Context}s score.
   *
   * TODO Implement a version of this for "frustratingly simple domain adaptation".
   * It will work by
   * 1) reading the domain from {@link Context}
   * 2) having a global weight set (like below) as well as weights for each domain!
   * 3) Object x in updateLabelScore will be (domain,featureIndex)
   */
  public static class Params extends Features {
    private static final long serialVersionUID = -6279675913451848033L;

    private double[][] weights;   // indexed by [x][y]

    public Params(int numFeats, int numLabels) {
      this.weights = new double[numFeats][numLabels];
    }

    @Override
    public void apply(Context c) {
      // Add features to c
      int featureIndex = c.getFlatIndex();
      double m = Math.exp(-c.muting);
      // Used for gradients
      Object x = new Pair<Integer, Double>(featureIndex, m);
      double[] wx = weights[featureIndex];
      for (int y = 0; y < wx.length; y++) {
        double z = m * wx[y];
        c.updateLabelScore(x, y, z);
      }
    }

    @Override
    public void unapply(Context c) {
      c.unUpdateLabelScore();
    }
  }
}