package edu.jhu.hlt.tutils.features;

import java.util.ArrayList;
import java.util.List;

/**
 * A tree of {@link Template}s, where every path from root to a leaf represents
 * a product of the {@link Template}s on the path.
 *
 * @author travis
 */
public class TemplateTree extends TemplateM {
  private static final long serialVersionUID = 868342855114908161L;

  protected TemplateTree parent;
  protected List<TemplateM> children;
  protected Template template;
  protected int templateRange;

  public TemplateTree(Template t) {
    if (t == null)
      throw new IllegalArgumentException("null Template");
    this.template = t;
    this.children = new ArrayList<>();
  }

  @Override
  public String toString() {
    return "(TemplateTree " + template.name
        + " templateRange=" + templateRange
        + " numChildren=" + children.size() + ")";
  }

  public void addChild(TemplateM t) {
    if (t instanceof TemplateTree) {
      TemplateTree tt = (TemplateTree) t;
      if (tt.parent != null)
        throw new IllegalArgumentException();
      tt.parent = this;
    }
    this.children.add(t);
  }

  @Override
  public void apply(Context c) {
    // This sets Context.templateValueBuffer
    template.accept(c);
    if (!c.isViable())
      return;
    int x = c.templateValueBuffer;
    if (x >= templateRange)
      templateRange = x + 1;
    int Nc = children.size();
    int R = templateRange * Nc;
    for (int ci = 0; ci < Nc; ci++) {
      // Each child needs to see a different value
      c.add(x + ci * templateRange, R);

      // For each child, let them see the piece that was extracted and do
      // something with it like lookup a weight and update Context.
      // NOTE: It is assumed that these children will never re-write the
      // information extracted earlier. I don't think I need do-undo semantics
      // to enforce this... hopefully.
      TemplateM child = children.get(ci);
      child.apply(c);
      if (c.isViable())
        child.unapply(c);

      // Pairs with the add at the top of the loop
      c.unAdd();
    }
  }

  @Override
  public void unapply(Context c) {
  }
}