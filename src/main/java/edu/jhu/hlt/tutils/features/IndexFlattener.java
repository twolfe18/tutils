package edu.jhu.hlt.tutils.features;

import edu.jhu.prim.tuple.Pair;

/**
 * "Commits" a template into the feature vector. Does so by resolving int[] -> int.
 */
public class IndexFlattener extends TemplateM {
  private static final long serialVersionUID = 1618078792691007876L;

  @Override
  public void apply(Context c) {
    // Walk up from this node up the TemplateTree and resolve each template
    // value to a single int.
    int idx = 1;
    int i = c.numTemplates();
    for (TemplateM node = parent; node != null; node = node.parent) {
      int r;
      if (node instanceof TemplateTree)
        r = ((TemplateTree) node).range;
      else
        throw new RuntimeException();
      i--;
      int x = c.get(i);
      idx = x + r * idx;
    }
    assert i == 0 : "i=" + i;
    // Add this value to the feature vector
    c.setFlatIndex(idx);
  }

  @Override
  public void unapply(Context c) {
    c.clearFlatIndex();
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
  public static class Params extends IndexFlattener {
    private static final long serialVersionUID = -6279675913451848033L;

    private double[][] weights;   // indexed by [x][y]

    public Params(int numFeats, int numLabels) {
      this.weights = new double[numFeats][numLabels];
    }

    @Override
    public void apply(Context c) {
      // Add features to c
      super.apply(c);
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
      super.unapply(c);
    }
  }
}