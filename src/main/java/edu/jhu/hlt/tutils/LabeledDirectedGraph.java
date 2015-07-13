package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Random;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Dependency;
import edu.jhu.hlt.concrete.DependencyParse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.serialization.TarGzCompactCommunicationSerializer;
import edu.jhu.hlt.concrete.util.ConcreteException;

/**
 * Adjacency list style graph representation.
 *
 * Only allows non-negative node indices for now, so you have to use N as a root
 * index (where there are N valid tokens which are 0-indexed).
 *
 * TODO: Add the following
   Shortest path queries:
   - general tool for shortest path given two endpoints: http://en.wikipedia.org/wiki/Bidirectional_search
   - may be faster to just do Dijkstra twice, each time going in the "parent" direction (near-trees will have branching factor of 1)
     make sure you use the fibonacci heap version: http://people.cs.kuleuven.be/~jon.sneyers/presentations/dijkstra_chr.pdf
   - LOOPS?!
     just keep a bitset around for these algorithms to detect loops

   All path queries:
   - similar algorithms as above, but you need to keep a stack of nodes from source with you
     always read from the stack (when you complete a path, need to copy the whole thing off)
     on exploration out of a node, can push/pop on items on/off the stack to just use one stack (avoid copying)
 *
 * @author travis
 */
public class LabeledDirectedGraph implements Serializable {
  private static final long serialVersionUID = 5303020866316351673L;

  public static boolean DEBUG = false;

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

  /*
   * TODO split point is undefined in cases where nodes either have no children
   * or no edges at all.
   * 
   * No edges => splitPoint[n] = -1
   * Parent(s) but no child(ren) => splitPoint[n] = 
   * Child(ren) but no parent(s) => splitPoint[n] = 
   * 
   * Children* starting at splitPoint (which could point to first parent of next node)
   * Parent*rev starting at one behind split point (this position, again, could belong to another node)
   */

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

  /**
   * This will return 2 times the number of directed edges since this representation
   * stores incident and adjacent edges.
   */
  public int getNumEdges() {
    return edges.length;
  }

  public int getNumNodes() {
    return splitPoints.length;
  }

  public Node getNode(int nodeIndex) {
    return this.new Node(nodeIndex);
  }

  public long getEdge(int edgeIndex) {
    return edges[edgeIndex];
  }

  public String toString(MultiAlphabet alph) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < getNumEdges(); i++)
      sb.append(showEdge(getEdge(i), alph));
    return sb.toString();
  }

  public static String showEdge(long edge) {
    return showEdge(edge, null);
  }
  public static String showEdge(long edge, MultiAlphabet alph) {
    int node = unpackNode(edge);
    int conode = unpackConode(edge);
    String e = alph == null ? ""+unpackEdge(edge) : alph.dep(unpackEdge(edge));
    if (unpackDirection(edge))
      return String.format("%d->%d %s\n", node, conode, e);
    else
      return String.format("%d<-%d %s\n", node, conode, e);
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
      if (nodeIndex >= splitPoints.length) {
        throw new RuntimeException("either this graph was constructed "
            + "improperly (likely if there are no edges) or nodeIndex="
            + nodeIndex + " is illegal, splitPoints.length="
            + splitPoints.length + " edges.length=" + edges.length);
      }
      this.node = nodeIndex;
      this.split = splitPoints[nodeIndex];
      this.numParents = -1;
      this.numChildren = -1;
    }

    public int getNodeIndex() {
      return node;
    }

    public String toString() {
      return String.format("<Node %d sp=%d #parent=%d #child=%d>",
          node, split, numParents(), numChildren());
    }

    public int numParents() {
      if (numParents < 0) {
        numParents = 0;
        for (int i = split - 1; i >= 0; i--) {
          int n = unpackNode(edges[i]);
          if (n == node) {
            assert !unpackDirection(edges[i]);
            numParents++;
          } else {
            break;
          }
        }
      }
      return numParents;
    }

    public long getParentEdge(int i) {
      assert i >= 0 && i < numParents();
      return edges[split - (i + 1)];
    }

    public int getParent(int i) {
      long e = getParentEdge(i);
      int n = unpackConode(e);
      return n;
    }

    public Node getParentNode(int i) {
      long e = getParentEdge(i);
      int n = unpackConode(e);
      return LabeledDirectedGraph.this.new Node(n);
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
        for (int i = split; i >= 0 && i < edges.length; i++) {
          int n = unpackNode(edges[i]);
          if (n == node) {
            numChildren++;
            assert unpackDirection(edges[i]);
          } else {
            break;
          }
        }
      }
      return numChildren;
    }

    public long getChildEdge(int i) {
      assert i >= 0 && i < numChildren();
      return edges[split + i];
    }

    public int getChild(int i) {
      long e = getChildEdge(i);
      int n = unpackConode(e);
      return n;
    }

    public Node getChildNode(int i) {
      long e = getChildEdge(i);
      int n = unpackConode(e);
      return LabeledDirectedGraph.this.new Node(n);
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

    public int numEdges() {
      return top;
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

    /**
     * Uses 0-indexes for tokens with n as root/wall (where n is the length of the
     * sentence).
     * @param n is the length of the sentence/tokenization. Since a dependency
     * parse need not (necessarily) cover all the tokens, this is required.
     * @param root must be >= n (and thus >= 0), and is the number used to denote
     * the root node. Should be greater than any other valid node index.
     * @param offset is the 0-index of the first token in the {@link DependencyParse}
     * (concrete uses sentence-specific indexes, this uses document/global indices).
     */
    public void addFromConcrete(DependencyParse p, int offset, int n, int root, MultiAlphabet alph) {
      if (p == null)
        return;
      if (root < 0)
        throw new IllegalArgumentException();
      assert n <= root;
      for (Dependency d : p.getDependencyList()) {
        int e = alph.dep(d.getEdgeType());
        int gov = root;
        if (d.isSetGov() && d.getGov() >= 0) {
          gov = d.getGov() + offset;
          assert gov < root;
          assert d.getGov() < n;
        } else {
          gov = root;
        }
        int dep = d.getDep() + offset;
        assert dep < root;
        assert d.getDep() < n;
        assert gov >= 0 && dep >= 0;
//        System.out.println("gov=" + gov + " dep=" + dep + " offset=" + offset
//            + " n=" + n + " root=" + root
//            + " gov+offset=" + (gov+offset) + " dep+offset=" + (dep+offset));
        add(gov, dep, e);
      }
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
      if (DEBUG) System.out.println("[computeSplitPoints] i=" + i);
      if (nodes.get(i)) {
        // We have seen this node, so there will be edges for it.
        // Find the first edge where node==i and is node->conode.
        if (DEBUG)
          System.out.println("looking for node=" + i + " ptr=" + ptr);
        while (true) {
          long e = edges[ptr];
          int n = unpackNode(e);
          boolean d = unpackDirection(e);
          if (DEBUG)
            System.out.println("inspecting ptr=" + ptr + " n=" + n + " cn=" + unpackConode(e) + " d=" + d);
          if (n == i && d)
            break;
          if (n > i) {
            // No children, put split point one after last parent, which will
            // be here if checked eagerly.
            break;
          }
          ptr++;
        }
        if (DEBUG) System.out.println("[computeSplitPoints] setting splitPoints[" + i +"] = " + ptr);
        splitPoints[i] = ptr;
      } else {
        if (DEBUG) System.out.println("[computeSplitPoints] setting splitPoints[" + i +"] = -1");
        splitPoints[i] = -1;
      }
    }
    if (DEBUG) System.out.println("[computeSplitPoints] done");
    return splitPoints;
  }

  private static long pack(int node, int edge, int conode, boolean nodeIsParentOfConode) {
    if (node < 0 || edge < 0 || conode < 0) {
      throw new IllegalArgumentException("all values must be >=0,"
          + " node=" + node + " edge=" + edge + " conode=" + conode);
    }

    boolean debugThis = false;

    if (DEBUG && debugThis) {
      System.out.println("[pack] node=" + node + " edge=" + edge + " conode=" + conode);
      System.out.println("[pack] node=" + pad64(node) + " edge=" + pad64(edge) + " conode=" + pad64(conode));
    }

    assert node < (1l << TOKEN_INDEX_BITS);
    assert edge < (1l << (EDGE_LABEL_BITS - 1));    // need one bit for direction
    assert conode < (1l << TOKEN_INDEX_BITS);

    assert node >= 0 : "node=" + pad64(node);
    assert conode >= 0 : "conode=" + pad64(conode);
    assert edge >= 0 : "edge=" + pad64(edge);

    if (nodeIsParentOfConode) {
      assert (edge >> EDGE_LABEL_BITS) == 0;
      edge |= 1l << (EDGE_LABEL_BITS - 1);
    }

    if (DEBUG && debugThis) {
      System.out.println("[p 0] T = " + TOKEN_INDEX_BITS);
      System.out.println("[p 0] E = " + EDGE_LABEL_BITS);
    }

    long p = node;
    if (DEBUG && debugThis) {
      System.out.println("[p 1] p = " + pad64(p));
      System.out.println("[p 1] e = " + pad64(edge));
      System.out.println("[p 1] n = " + pad64(p << EDGE_LABEL_BITS));
    }
    p = (p << EDGE_LABEL_BITS) | edge;
    if (DEBUG && debugThis)
      System.out.println("[p 2] p = " + pad64(p));

    p = (p << TOKEN_INDEX_BITS) | conode;
    if (DEBUG && debugThis)
      System.out.println("[p 3] p = " + pad64(p));

    assert p >= 0 : "if negative, there has been an overflow";
    return p;
  }

  public static int unpackNode(long edge) {
    long mask = (1l << TOKEN_INDEX_BITS) - 1;
//    if (DEBUG)
//      System.out.println("[unpackNode] edge=" + pad64(edge) + " mask=" + pad64(mask));
    return (int) ((edge >> (EDGE_LABEL_BITS + TOKEN_INDEX_BITS)) & mask);
  }

  /** Returns true if node->conode, i.e. node is the parent of conode */
  public static boolean unpackDirection(long edge) {
    return (edge >> (EDGE_LABEL_BITS + TOKEN_INDEX_BITS - 1) & 1l) != 0;
  }

  public static int unpackEdge(long edge) {
    long mask = (1l << (EDGE_LABEL_BITS - 1)) - 1l;
//    if (DEBUG)
//      System.out.println("[unpackEdge] edge=" + pad64(edge) + " mask=" + pad64(mask));
    return (int) ((edge >> TOKEN_INDEX_BITS) & mask);
  }

  public static int unpackConode(long edge) {
    long mask = (1l << TOKEN_INDEX_BITS) - 1;
//    if (DEBUG)
//      System.out.println("[unpackConode] edge=" + pad64(edge) + " mask=" + pad64(mask));
    return (int) (edge & mask);
  }

  public static int unpackGov(long edge) {
    if (unpackDirection(edge))
      return unpackNode(edge);
    else
      return unpackConode(edge);
  }

  public static int unpackDep(long edge) {
    if (unpackDirection(edge))
      return unpackConode(edge);
    else
      return unpackNode(edge);
  }

  /**
   * Uses 0-indexes for tokens with n as root/wall (where n is the length of the
   * sentence).
   * @param n is the length of the sentence/tokenization. Since a dependency
   * parse need not (necessarily) cover all the tokens, this is required.
   */
  public static LabeledDirectedGraph fromConcrete(DependencyParse p, int n, MultiAlphabet alph) {
    LabeledDirectedGraph.Builder g = new LabeledDirectedGraph().new Builder();
    // TODO check p is 0-indexed
    // TODO warn if p is tree
    for (Dependency d : p.getDependencyList()) {
      int e = alph.dep(d.getEdgeType());
      int gov = n;
      if (d.isSetGov() && d.getGov() >= 0) {
        gov = d.getGov();
        assert gov < n;
      }
      int dep = d.getDep();
      assert dep < n;
      g.add(gov, dep, e);
    }
    return g.freeze();
  }

  // For testing
  public static void main(String[] args) throws ConcreteException, IOException {
    testPacking0();
    testPacking1();
    testDeps();
  }

  /**
   * You can use the following to grep out the results:
grep -P '^\d+\D{2}\d+ \S+|Dependency' -n /tmp/LabeledDirectedGraph.log | head -n 50
194:Dependency(dep:2, edgeType:root)
195:Dependency(gov:1, dep:0, edgeType:det)
196:Dependency(gov:2, dep:1, edgeType:nsubj)
197:Dependency(gov:5, dep:4, edgeType:det)
198:Dependency(gov:2, dep:5, edgeType:prep_on)
199:Dependency(gov:8, dep:6, edgeType:mark)
200:Dependency(gov:8, dep:7, edgeType:nsubj)
201:Dependency(gov:2, dep:8, edgeType:advcl)
202:Dependency(gov:8, dep:9, edgeType:dobj)
367:0<-1 det
368:1<-2 nsubj
369:1->0 det
370:2<-11 root
371:2->1 nsubj
372:2->5 prep_on
373:2->8 advcl
374:4<-5 det
375:5<-2 prep_on
376:5->4 det
377:6<-8 mark
378:7<-8 nsubj
379:8<-2 advcl
380:8->7 nsubj
381:8->6 mark
382:8->9 dobj
383:9<-8 dobj
384:11->2 root
   */
  public static void testDeps() throws ConcreteException, IOException {
    File f = new File("/home/travis/code/schema-challenge/features/framenet/data/train.concrete.tgz");
    TarGzCompactCommunicationSerializer ts = new TarGzCompactCommunicationSerializer();
    Iterator<Communication> itr = ts.fromTarGz(Files.newInputStream(f.toPath()));
    while (itr.hasNext()) {
      Communication c = itr.next();
      for (Section section : c.getSectionList()) {
        for (Sentence sentence : section.getSentenceList()) {
          Tokenization t = sentence.getTokenization();
          int n = t.getTokenList().getTokenListSize();
          for (DependencyParse p : t.getDependencyParseList()) {
            printEdges(p, n);
            testTraversals(p, n);
          }
        }
      }
    }
  }

  public static void testTraversals(DependencyParse p, int n) {
    // Print according to Concrete
    for (Dependency d : p.getDependencyList())
      System.out.println(d);

    MultiAlphabet alph = new MultiAlphabet();
    LabeledDirectedGraph g = LabeledDirectedGraph.fromConcrete(p, n, alph);

    // Take random paths up the graph
    Random r = new Random(9001);
    Node node = g.getNode(r.nextInt(g.getNumNodes()));
    while (node.numParents() > 0) {
      System.out.println(node + " UP");
      int pi = r.nextInt(node.numParents());
      System.out.println(showEdge(node.getParentEdge(pi), alph));
      node.gotoParentNode(pi);
    }

    // Take random paths down the graph
    node = g.getNode(r.nextInt(g.getNumNodes()));
    while (node.numChildren() > 0) {
      System.out.println(node + " DOWN");
      int pi = r.nextInt(node.numChildren());
      System.out.println(showEdge(node.getChildEdge(pi), alph));
      node.gotoChildNode(pi);
    }
  }

  /** Shows the edges according to Concrete as well as the ingested form */
  public static void printEdges(DependencyParse p, int n) {
    MultiAlphabet alph = new MultiAlphabet();
    String tool = p.getMetadata().getTool();
    if (tool.toLowerCase().contains("basic"))
      return;
    System.out.println("parse: " + tool);
    // Dependencies according to Concrete
    for (Dependency d : p.getDependencyList())
      System.out.println(d);
    System.out.println();
    // Dependencies according to LabeledDirectedGraph
    LabeledDirectedGraph g = LabeledDirectedGraph.fromConcrete(p, n, alph);
    System.out.println(g.toString(alph));
    System.out.println();
  }

  public static String putDashesEvery(String bin, int n) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < bin.length(); i++) {
      if (i % n == 0 && i > 0)
        sb.append('-');
      sb.append(bin.charAt(i));
    }
    return sb.toString();
  }

  public static String padWithChar(String bin, char c, int fullWidth) {
    StringBuilder sb = new StringBuilder();
    int n = fullWidth - bin.length();
    for (int i = 0; i < n; i++)
      sb.append(c);
    sb.append(bin);
    //return sb.toString();
    return putDashesEvery(sb.toString(), 8);
  }

  public static String pad64(String bin) {
    return padWithChar(bin, '0', 64);
  }

  public static String pad64(long num) {
    return pad64(Long.toBinaryString(num));
  }

  public static void showPacking(int node, int edge, int conode, boolean nodeIsParentOfConode) {
    System.out.println("node    = " + pad64(node));
    System.out.println("edge    = " + pad64(edge));
    System.out.println("conode  = " + pad64(conode));
    System.out.println("dir     = " + nodeIsParentOfConode);
    long e = pack(node, edge, conode, nodeIsParentOfConode);
    System.out.println("encoded = " + pad64(e));
    System.out.println("unode   = " + pad64(unpackNode(e)));
    System.out.println("uedge   = " + pad64(unpackEdge(e)));
    System.out.println("uconode = " + pad64(unpackConode(e)));
    System.out.println("udir    = " + unpackDirection(e));
    assert node == unpackNode(e);
    assert edge == unpackEdge(e);
    assert conode == unpackConode(e);
    assert nodeIsParentOfConode == unpackDirection(e);
  }

  public static void testPacking0() {
    showPacking(1, 1, 1, true);
    showPacking(1, 1, 1, false);
  }

  public static void testPacking1() {
    Random r = new Random(9001);
    for (int i = 0; i < 10; i++) {
      int node = r.nextInt(50);
      int edge = r.nextInt(15);
      int conode = r.nextInt(50);
      boolean nodeIsParentOfConode = r.nextBoolean();
      showPacking(node, edge, conode, nodeIsParentOfConode);
    }
  }
}
