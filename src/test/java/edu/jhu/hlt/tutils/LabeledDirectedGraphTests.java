package edu.jhu.hlt.tutils;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class LabeledDirectedGraphTests {

  @Before
  public void setup() {
    LabeledDirectedGraph.DEBUG = true;
  }

  @Test
  public void test0() {
    // I think I have an assumption that the graph is dense,
    // i.e. that there will be no node i if there isn't a node i-1.
    // While this is attainable in general, it makes representing dependency
    // graphs hard where a token index is used for a node id.

    // Resolution: No density assumption
    // I had a bug in computeSplitPoints which only showed up when the last node
    // had no children.

    for (int edge = 0; edge < 10; edge++) {
      LabeledDirectedGraph g = new LabeledDirectedGraph();
      LabeledDirectedGraph.Builder b = g.new Builder();
      b.add(0, 5, edge);
      b.freeze();

      LabeledDirectedGraph.Node p = g.getNode(0);
      LabeledDirectedGraph.Node c = g.getNode(5);

      assertEquals(1, p.numChildren());
      assertEquals(0, p.numParents());
      assertEquals(5, p.getChild(0));
      assertEquals(edge, LabeledDirectedGraph.unpackEdge(p.getChildEdge(0)));

      assertEquals(0, c.numChildren());
      assertEquals(1, c.numParents());
      assertEquals(0, c.getParent(0));
      assertEquals(edge, LabeledDirectedGraph.unpackEdge(c.getParentEdge(0)));
    }

  }
}
