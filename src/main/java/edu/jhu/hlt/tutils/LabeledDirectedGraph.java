package edu.jhu.hlt.tutils;

import java.util.Arrays;
import java.util.BitSet;

import edu.jhu.hlt.concrete.Dependency;
import edu.jhu.hlt.concrete.DependencyParse;

/**
 * Adjacency list style graph representation.
 *
 * @author travis
 */
public class LabeledDirectedGraph {

  // TODO Represent node->conode vs node<-conode with high order bit in label.
  public static final int EDGE_LABEL_BITS = 16;
  public static final int TOKEN_INDEX_BITS = 24;

  // Bit readout (see pack):
  // 24 bits for node index
  // 16 bits for edge label
  // 24 bits for conode index
  // Index is not important, but edges must be sorted (in the order given by the bit readout).
  // NOTE: edge label uses the highest bit for the direction of the edge.
  // 1 indicates conode->node (conode is parent) and 0 indicates node->conode (conode is child)
  private long[] edges;

  // Index is a token index, value is the first index in edges s.t. the edge
  // label direction is node -> conode.
  private int[] splitPoints;

  /**
   * If you already have the data from another LabeledDirectedGraph, you can
   * use this 0-cost constructor. Otherwise use the Builder class.
   *
   * @param splitPoints can be null, in which case this constructor will compute
   * the split points.
   */
  public LabeledDirectedGraph(long[] edges, int[] splitPoints) {
    if (splitPoints == null)
      splitPoints = computeSplitPoints(edges);
    this.edges = edges;
    this.splitPoints = splitPoints;
  }

  public LabeledDirectedGraph() {}

  public int getNumEdges() {
    return edges.length;
  }

  public int getNumNodes() {
    return splitPoints.length;
  }

  /**
   * Pointer to a node in the graph with the ability to give parents and children.
   */
  public class Node {
    private int split;
    private int node;
    private int numParents;
    private int numChildren;

    public Node(int nodeIndex) {
      this.node = nodeIndex;
      this.split = splitPoints[nodeIndex];
      this.numParents = -1;
      this.numChildren = -1;
    }

    public int numParents() {
      if (numParents < 0) {
        numParents = 0;
        for (int i = split - 1; i >= 0; i--) {
          int n = unpackNode(edges[i]);
          if (n == node)
            numParents++;
        }
      }
      return numParents;
    }

    public long getParentEdge(int i) {
      assert i >= 0 && i < numParents();
      return edges[split - (i + 1)];
    }

    public Node getParentNode(int i) {
      long e = getParentEdge(i);
      int n = unpackConode(e);
      int s = splitPoints[n];
      return LabeledDirectedGraph.this.new Node(s);
    }

    /** Returns the node index for the previous state */
    public int gotoParentNode(int i) {
      int oldNode = node;
      long e = getParentEdge(i);
      this.node = unpackConode(e);
      this.split = splitPoints[this.node];
      this.numParents = -1;
      this.numChildren = -1;
      return oldNode;
    }

    public int numChildren() {
      if (numChildren < 0) {
        numChildren = 0;
        for (int i = split; i < edges.length; i++) {
          int n = unpackNode(edges[i]);
          if (n == node)
            numChildren++;
        }
      }
      return numChildren;
    }

    public long getChildEdge(int i) {
      assert i >= 0 && i < numChildren();
      return edges[split + i];
    }

    public Node getChildNode(int i) {
      long e = getChildEdge(i);
      int n = unpackConode(e);
      int s = splitPoints[n];
      return LabeledDirectedGraph.this.new Node(s);
    }

    /** Returns the node index for the previous state */
    public int gotoChildNode(int i) {
      int oldNode = node;
      long e = getChildEdge(i);
      this.node = unpackConode(e);
      this.split = splitPoints[this.node];
      this.numParents = -1;
      this.numChildren = -1;
      return oldNode;
    }
  }

  /**
   * Helps build LabeledDirectedGraphs.
   */
  public class Builder {
    private int top;

    public Builder() {
      top = 0;
      edges = new long[16];
      splitPoints = null;
    }

    public void add(int parent, int child, int edgeLabel) {
      if (top < 0)
        throw new RuntimeException("you can't add after freeze!");
      // Check to make sure edges is big enough
      if (top >= edges.length - 1) {
        int newSize = (int) (edges.length * 1.6 + 2.5);
        edges = Arrays.copyOf(edges, newSize);
      }
      edges[top++] = pack(parent, edgeLabel, child, true);
      edges[top++] = pack(child, edgeLabel, parent, false);
    }

    public LabeledDirectedGraph freeze() {
      // Truncate the edge array
      long[] trunc = new long[top];
      for (int i = 0; i < top; i++)
        trunc[i] = edges[i];
      edges = trunc;

      // Sort edges
      Arrays.sort(edges);

      // Compute splitPoints
      splitPoints = computeSplitPoints(edges);

      // Disallow future adds
      top = -1;

      return LabeledDirectedGraph.this;
    }
  }

  public static int[] computeSplitPoints(long[] edges) {
    BitSet nodes = new BitSet();
    for (int i = 0; i < edges.length; i++) {
      long e = edges[i];
      nodes.set(unpackNode(e));
      nodes.set(unpackConode(e));
    }
    int numNodes = nodes.length();
    int[] splitPoints = new int[numNodes];
    int ptr = 0;
    for (int i = 0; i < numNodes; i++) {
      if (nodes.get(i)) {
        // update ptr
        splitPoints[i] = ptr;
        throw new RuntimeException("implement me");
      } else {
        splitPoints[i] = -1;
      }
    }
    return splitPoints;
  }

  private static long pack(int node, int edge, int conode, boolean nodeIsParentOfConode) {
    assert node < (1 << TOKEN_INDEX_BITS);
    assert edge < (1 << EDGE_LABEL_BITS);
    assert conode < (1 << TOKEN_INDEX_BITS);
    if (!nodeIsParentOfConode) {
      assert (edge >> EDGE_LABEL_BITS) == 0;
      edge |= 1 << EDGE_LABEL_BITS;
    }
    long p = node;
    p = (p << EDGE_LABEL_BITS) ^ edge;
    p = (p << TOKEN_INDEX_BITS) ^ conode;
    return p;
  }

  public static int unpackNode(long edge) {
    long mask = (1l << TOKEN_INDEX_BITS) - 1;
    return (int) ((edge >> (EDGE_LABEL_BITS + TOKEN_INDEX_BITS)) & mask);
  }

  public static int unpackEdge(long edge) {
    long mask = (1l << EDGE_LABEL_BITS) - 1;
    return (int) ((edge >> TOKEN_INDEX_BITS) & mask);
  }

  public static int unpackConode(long edge) {
    long mask = (1l << TOKEN_INDEX_BITS) - 1;
    return (int) (edge & mask);
  }

  public static LabeledDirectedGraph fromConcrete(DependencyParse p, MultiAlphabet alph) {
    LabeledDirectedGraph.Builder g = new LabeledDirectedGraph().new Builder();
    // TODO check p is 0-indexed
    // TODO warn if p is tree
    for (Dependency d : p.getDependencyList()) {
      int e = alph.dep(d.getEdgeType());
      g.add(d.getGov(), d.getDep(), e);
    }
    return g.freeze();
  }
}
