package edu.jhu.hlt.tutils.features;

import java.io.Serializable;

public abstract class TemplateM implements Serializable {
  private static final long serialVersionUID = -4716784939915473008L;

  // I considered having this just be super, but you can't do super.super...
  protected TemplateM parent;

  public abstract void apply(Context c);

  public abstract void unapply(Context c);
}