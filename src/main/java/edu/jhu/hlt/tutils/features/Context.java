package edu.jhu.hlt.tutils.features;

import java.util.ArrayDeque;
import java.util.Arrays;

import edu.jhu.hlt.tutils.Document;

/**
 * Holds the input to and the output from a few templates. This is the only
 * argument to a feature function and means that they don't need to return
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
  // These are currently un-named registers for passing information from one
  // Template to another. Right now, Templates don't know about each other, but
  // this is somewhat limiting. The only way that templates can talk to each
  // other is by "cancelling" an extraction.
  //
  // This is the ONLY THING that templates may safely mutate.
  // See TemplateTree for the reason: we will need to "roll back" template
  // applications in order to support a tree of templates (as opposed to a list).
  private int numTemplates;    // if you set this to -1, this extraction is "cancelled" or "annihilated"
//  private int[] templateIndices;    // NOT ACTUALLY NECESSARY :) see IndexFlattener, the key is to walk up the TemplateTree to determine what template added a value
  private int[] templateValues;

  // See IndexFlattener
  private int flatIndex = -1;

  // The ultimate goal of a context is to arrive at a score for some labels/actions.
  // There should be a special sub-class of Templates which read information out
  // of the registers above, lookup a label-weight, and sum it into this array.
  private double[] scores;

  // This is basically a log off additions to scores used for gradient updates.
  // Note that this is not even needed for prediction.
  // Set this to null for just prediction and initialize to signal that features should recorded
//  private List<Extraction> features;
  private ArrayDeque<Extraction> feats;

  // How "muted" should this feature/extraction be. This must be >= 0.
  // The default rule is that features fire with weight = exp(-muting) and that
  // muting is monotonically non-decreasing as more Templates act on this
  // context. Note that muting is related to regluarization (you can use a
  // constant l1/l2 regularizer constant globally and then modulate the
  // per-feature regularization by making it fire with smaller (more
  // regularization) or larger (less regularization) values.
  double muting;

  public Context(Document doc, int numLabels, boolean recordScoreUpdates) {
    clear();
    this.doc = doc;
    this.scores = new double[numLabels];
    if (recordScoreUpdates)
      this.feats = new ArrayDeque<>();
  }

  public void clear() {
    doc = null;
    token = Document.UNINITIALIZED;
    cons = Document.UNINITIALIZED;
    numTemplates = 0;
    templateValues = new int[DEFAULT_TEMPLATE_CAPACITY];
    if (feats != null)
      feats.clear();
    if (scores != null)
      Arrays.fill(scores, 0);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(Context doc=" + doc.getId() + "\n");
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

  // TODO isZero?
  public boolean isViable() {
    return numTemplates >= 0;
  }

  public void setFlatIndex(int flatIndex) {
    assert flatIndex >= 0;
    this.flatIndex = flatIndex;
  }

  public void clearFlatIndex() {
    this.flatIndex = -1;
  }

  public int getFlatIndex() {
    assert flatIndex >= 0;
    return flatIndex;
  }

  public int numTemplates() {
    assert numTemplates >= 0;
    return numTemplates;
  }

  public int get(int templateIndex) {
    assert templateIndex < numTemplates;
    return templateValues[templateIndex];
  }

  public int getLast() {
    assert numTemplates > 0;
    return templateValues[numTemplates - 1];
  }

  public void add(int templateValue) {
    templateValues[numTemplates] = templateValue;
    numTemplates++;
  }

  /** Undoes an add */
  public void rollback() {
    assert numTemplates > 0;
    numTemplates--;
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

//  /** Only call this once per {@link Template#accept(Context)} */
//  public void set(int template, int value) {
//    if (numTemplates < 0) {
//      assert false : "check first?";
//      return;
//    }
//    templateIndices[numTemplates] = template;
//    templateValues[numTemplates] = value;
//    numTemplates++;
//  }

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