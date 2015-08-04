package edu.jhu.hlt.tutils.features;

import java.io.Serializable;
import java.util.function.Consumer;

import edu.jhu.prim.bimap.IntObjectBimap;

/**
 * Given a {@link Context}, either:
 * 1) update the context with some extracted value and return true or
 * 2) return false signaling that this template didn't fire (and so neither
 * should anything producted with it).
 */
public abstract class Template implements Consumer<Context>, Serializable {
  private static final long serialVersionUID = -4980747883768272868L;

  public String name;
  public int index;

  // NOTE: Anything that modifies one of these data structures must be locked
  // (even something as simple as adding (featureName, featureIndex) values to
  // an alphabet.

  // True if multiple threads may be accessing a given Template at the same
  // time. This means that a lot of locks must be used for anything that
  // modifies state.
  // A common pattern is to create and run some Features single threaded,
  // save them to disk, then later deserialize them and run in multi-threaded
  // mode (aka stateless/read-only mode).
  public static boolean MULTI_THREADED = false;

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
}