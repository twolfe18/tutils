package edu.jhu.hlt.tutils.features;

import java.util.ArrayList;
import java.util.List;

/*
 * I had some fleeting ambition of having a Template choose which child to
 * recuse onto, but I think this is not well defined, or at least it conflicts
 * with how I have been using Context. We should recurse on all children. If
 * that means that TemplateTree = LinkedList<Template>, then so be it. But
 * there are nice ways to build trees based on shared prefixes, see my notes
 * lower.
 */
public class TemplateTree extends TemplateM {
  private static final long serialVersionUID = 868342855114908161L;

  protected Template template;
  protected List<TemplateM> children;

  // How many unique values can the sub-tree rooted at this node take on?
  // TODO I think this may not make sense. This seems like this constrains me
  // to stay within the framework of "every TemplateTree evaluates to an int".
  // A lot of the interesting stuff I have been speculating about (e.g. having
  // a TemplateTree extract features for a set of labels) do not fit into this
  // paradigm.
  // TODO Sometimes you will want to have this (we do just spit out an int and
  // we want to know how many doubles to reserve). In this case we could just
  // have a sub-class that has this field?
  protected int range;

  // Set by a superviser of this tree to avoid collisions. Related to range.
  // TODO Do I need this? IndexFlattener doesn't use this.
  protected int offset;

  public TemplateTree(Template t) {
    if (t == null)
      throw new IllegalArgumentException("null Template");
    this.template = t;
    this.range = 0;
    this.children = new ArrayList<>();
  }

  public void addChild(TemplateM tt) {
    if (tt.parent != null)
      throw new IllegalArgumentException();
    tt.parent = this;
    this.children.add(tt);
  }

  @Override
  public void apply(Context c) {
    // This will extract some piece of a feature and store it in Context
    template.accept(c);
    if (!c.isViable())
      return;

    // Update range
    int tv = c.getLast();
    if (tv >= range)
      range = tv + 1;

    for (TemplateM child : children) {
      // For each child, let them see the piece that was extracted and do
      // something with it like lookup a weight and update Context.
      // NOTE: It is assumed that these children will never re-write the
      // information extracted earlier. I don't think I need do-undo semantics
      // to enforce this... hopefully.
      child.apply(c);
      if (c.isViable())
        child.unapply(c);
    }
  }

  @Override
  public void unapply(Context c) {
    c.rollback();
  }
}