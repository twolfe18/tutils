package edu.jhu.hlt.tutils.features;

import java.util.ArrayDeque;
import java.util.Arrays;

import edu.jhu.hlt.tutils.Document;
import edu.jhu.prim.vector.IntDoubleUnsortedVector;
import edu.jhu.prim.vector.IntDoubleVector;

/**
 * Holds the input to and the output from a few templates. This is the only
 * argument to a {@link Template} function meaning they don't need to return
 * anything.
 *
 * @author travis
 */
public class Context {
  public static int DEFAULT_TEMPLATE_CAPACITY = 8;

  // Input (to features/templates)
  public Document doc;
  public int token;
  public int cons;

  // Output
  // See TemplateTree for how these are managed
  public int flatIndex = 0;         // index of product of templates
  public int templateValueBuffer;   // return value from Template.accept

  // These are needed in order to "undo" adds/updates to flatIndex, which is
  // only needed when Templates are put into a tree.
  private int[] templateValues;
  private int[] templateRanges;
  private int numTemplates;    // if you set this to -1, this extraction is "cancelled" or "annihilated"

  // The ultimate goal of a context is to arrive at a score for some labels/actions.
  // There should be a special sub-class of Templates which read information out
  // of the registers above, lookup a label-weight, and sum it into this array.
  private double[] scores;

  // This is basically a log off additions to scores used for gradient updates.
  // Note that this is not even needed for prediction.
  // Set this to null for just prediction and initialize to signal that features should recorded
  private ArrayDeque<Extraction> feats;

  // How "muted" should this feature/extraction be. This must be >= 0.
  // The default rule is that features fire with weight = exp(-muting) and that
  // muting is monotonically non-decreasing as more Templates act on this
  // context. Note that muting is related to regluarization (you can use a
  // constant l1/l2 regularizer constant globally and then modulate the
  // per-feature regularization by making it fire with smaller (more
  // regularization) or larger (less regularization) values.
  double muting;

  public Context(Document doc, int numLabels) {
    clear();
    this.doc = doc;
    this.scores = new double[numLabels];
    this.feats = new ArrayDeque<>();
    templateValues = new int[DEFAULT_TEMPLATE_CAPACITY];
    templateRanges = new int[DEFAULT_TEMPLATE_CAPACITY];
  }

  public void clear() {
    doc = null;
    token = Document.UNINITIALIZED;
    cons = Document.UNINITIALIZED;
    numTemplates = 0;
    if (feats != null)
      feats.clear();
    if (scores != null)
      Arrays.fill(scores, 0);
  }

  public ArrayDeque<Extraction> getExtractions() {
    return feats;
  }

  public IntDoubleVector getExtractionsAsIDV() {
    int n = feats.size();
    IntDoubleUnsortedVector fv = new IntDoubleUnsortedVector(n);
    for (Extraction e : feats) {
      int x = (int) e.x;
      fv.add(x, 1);
    }
    return fv;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(Context doc=" + doc.getId() + "\n");
    sb.append("  templateValueBuffer=" + templateValueBuffer + "\n");
    if (flatIndex >= 0)
      sb.append("  flatIndex=" + flatIndex + "\n");
    if (token >= 0)
      sb.append("  token=" + token + "\n");
    if (cons >= 0)
      sb.append("  cons=" + cons + "\n");
    sb.append("  templateValues=" + Arrays.toString(Arrays.copyOf(templateValues, numTemplates)) + "\n");
    sb.append("  feats=" + feats + "\n");
    sb.append(")");
    return sb.toString();
  }

  public void cancel() {
    numTemplates = -1;
  }

  // TODO rename to isZero?
  public boolean isViable() {
    return numTemplates >= 0;
  }

  public int getFlatIndex() {
    assert flatIndex >= 0;
    return flatIndex;
  }

  public void add(int templateValue, int templateRange) {
    assert templateValue < templateRange
      : "templateValue=" + templateValue + " templateRange=" + templateRange;
    flatIndex = templateValue + templateRange * flatIndex;
    templateValues[numTemplates] = templateValue;
    templateRanges[numTemplates] = templateRange;
    numTemplates++;
  }

  public void unAdd() {
    assert numTemplates > 0;
    numTemplates--;
    flatIndex -= templateValues[numTemplates];
    flatIndex /= templateRanges[numTemplates];
  }

  /**
   * @param x is ancillary information needed to perform gradient updates. If
   * null, then only the score for y is updated by z, and no logging is done
   * (cannot do gradient updates without re-running extraction).
   * @param y is the label to be updated.
   * @param z is the amount by which to update y's score.
   */
  public void updateLabelScore(Object x, int y, double z) {
    scores[y] += z;
    if (x != null)
      feats.push(new Extraction(x, y, z));
  }

  public Extraction unUpdateLabelScore() {
    Extraction e = feats.pop();
    scores[e.y] -= e.z;
    return e;
  }

  // i.e. a feature/template value
  public static class Extraction {
    public final Object x;   // may be an int, int[], etc. Anything that can index a weight.
    public final int y;      // which label this extraction applies to (index into scores)
    public final double z;   // value of feature function (see muting)
    public Extraction(Object x, int y, double z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
    @Override
    public String toString() {
      return "(Extraction x=" + x + " y=" + y + " z=" + z + ")";
    }
  }
}