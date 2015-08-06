package edu.jhu.hlt.tutils.features;

import java.io.Serializable;

public abstract class TemplateM implements Serializable {
  private static final long serialVersionUID = -4716784939915473008L;

  // ON ONE HAND: I considered having this just be super, but you can't do super.super...
  // ON THE OTHER: what if you have something with many parents (e.g. Features as a common leaf)
//  protected TemplateM parent;

  public abstract void apply(Context c);

  public abstract void unapply(Context c);
}