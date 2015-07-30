package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import edu.jhu.prim.bimap.IntObjectBimap;
import edu.jhu.prim.vector.IntDoubleUnsortedVector;

/**
 * Features for things in a document.
 *
 * @author travis
 */
public class DocumentFeatures {

  private IntDoubleUnsortedVector[] tokFeats;
  private IntDoubleUnsortedVector[] consFeats;
  // TODO IntDoubleUnsortedVector[][] tok2Feats?
  // TODO IntDoubleUnsortedVector[][] cons2Feats?

  // This is fine, but I want to figure out how to do feature functions
  // Options:
  // 1) static final Map<String, java.util.Function>
  // 2) ...


  /**
   * Holds the input to and the output from a few templates. This is the only
   * argument to a feature function and means that they don't need to return
   * anything.
   */
  public static class Context {
    // Input
    public Document doc;
    public int token;
    public int cons;
    // Output
    int arity;    // if you set this to -1, this extraction is "cancelled" or "anihilated"
    public int f1, f2, f3, f4; // Supports up to 4 templates
    public int v1, v2, v3, v4;

    public void clear() {
      doc = null;
      token = Document.UNINITIALIZED;
      cons = Document.UNINITIALIZED;
      arity = 0;
      f1 = f2 = f3 = f4 = -1;
      v1 = v2 = v3 = v4 = -1;
    }

    public void cancel() {
      arity = -1;
    }

    // TODO isZero?
    public boolean isViable() {
      return arity >= 0;
    }

    public void set(int template, int value) {
      if (arity < 0) {
        assert false : "check first?";
        return;
      }
      arity++;
      if (f1 < 0) {
        f1 = template;
        v1 = value;
      } else if (f2 < 0) {
        f2 = template;
        v2 = value;
      } else if (f3 < 0) {
        f3 = template;
        v3 = value;
      } else if (f4 < 0) {
        f4 = template;
        v4 = value;
      } else {
        throw new RuntimeException("only 4 templates are supported");
      }
    }
  }

  /*
   * NOTE: Don't make the mistake of making Templates/Features stateful and mutable!
   * This ruins thread safety.
   */

  /**
   * Extracts a (key,value) pair given a context (or "cancels" the extraction).
   */
  public static abstract class Template implements Consumer<Context>, Serializable {
    private static final long serialVersionUID = -4980747883768272868L;

    public String name;
    public int index;

    // not strictly necessary?
    // This could be super useful though,
    // e.g. for centering (mean=0) or whitening (mean=0,var=1) a feature
    private IntObjectBimap<String> valueNames;
    private boolean recordValueNames = false;

    public Template(String name) {
      this(name, -1);
    }

    public Template(String name, int index) {
      this.name = name;
      this.index = index;
    }

    // You have to implement this yourself
    @Override
    public abstract void accept(Context c);
  }


  /**
   * A {@link Feature} is a product of {@link Template}s.
   *
   * Because of short-circuit semantics, we will say that order does matter, so
   * a unique Feature is determined by its ordered list of Templates.
   */
  public static class Feature implements Serializable {
    private static final long serialVersionUID = -6143709144163061074L;

    private int arity;
    private Template t1, t2, t3, t4;

    // Template indices -> single index (only one should be non-null).
    // These start off as -1, and when a feature value that is observed is -1, it is set to (offset + (uniq++))
    private int uniq = 0;   // does not depend on offset
    private int offset = 0; // can be set by someone above Feature for globally agreement/uniqueness
    // These do not depend on offset
    private int[][][][] m4;
    private int[][][] m3;
    private int[][] m2;     // e.g. m2[t1_value][t2_value] -> globally unique integer

    public int resolveFeatureIndex(Context c) {
      assert arity == c.arity;
      if (arity == 2) {
        return offset + m2[c.v1][c.v2];
      } else {
        throw new RuntimeException("implement me");
      }
    }

    public int cardinality() {
      return uniq;
    }

    public int maxIndex() {
      return offset + uniq - 1;
    }

    // TODO Other useful things to keep track of?
  }

  /**
   * Supposed to be a singleton which holds all the features in the system.
   *
   * TODO Should there be many instances of this where one is the "master"
   * instance which has all of the features/templates, and then you "sub-select"
   * the features/templates you want?
   */
  public static class FeatureManager {
    private Map<String, Template> templates;
    // Figure out how to get templates right first!
//    private Map<String, Feature> features;      // Don't instantiate a Feature twice since it has stats in it

    public void addTemplate(Template t) {
      if (t.name == null)
        throw new IllegalArgumentException("templates must have names");
      if (t.name.indexOf('+') >= 0)
        throw new IllegalArgumentException("templates may not contain '+': " + t.name);
      if (t.name.indexOf('*') >= 0)
        throw new IllegalArgumentException("templates may not contain '*': " + t.name);
      t.index = templates.size();
      Template old = templates.put(t.name, t);
      if (old != null)
        throw new RuntimeException("dup: " + t.name);
    }

    public static FeatureManager SINGLETON = new FeatureManager();
    static {
      SINGLETON.addTemplate(new Template("tok.pos") {
        @Override
        public void accept(Context c) {
          if (c.token < 0)
            c.cancel();
          else
            c.set(1, c.doc.getPosH(c.token));
        }
      });
    }
  }


//  public static Consumer<Context>[] getFeatureSet(List<String> featureNames) {
//    List<Consumer<Context>> feats = new ArrayList<>();
//    for (String fn : featureNames) {
//      Consumer<Context> f = FEATURES.get(fn);
//      if (f != null)
//        feats.add(f);
//    }
//    @SuppressWarnings("unchecked")
//    Consumer<Context>[] ar = new Consumer[feats.size()];
//    for (int i = 0; i < ar.length; i++)
//      ar[i] = feats.get(i);
//    return ar;
//  }

}
