package edu.jhu.hlt.tutils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Utility for parsing S-expressions or Penn Treebank parses.
 *
 * @author travis
 */
public class PennTreeReader {

  /**
   * The Node class (basic tree) does not provide indexing of leaf nodes by
   * their order. This class builds a map from "token position" => "leaf node".
   * This is useful for Propbank-style node references, which are a pair (t,h)
   * where t is the terminal index (or "token position") and h is the height up
   * the tree.
   */
  public static class Indexer {
    private Node[] leaves;
    private Node[] byId;
    private int leafId;

    public Indexer(Node root) {
      this.leafId = 0;
      this.leaves = new Node[16];
      this.byId = new Node[16];
      preorder(root);
    }

    private void preorder(Node n) {
      visit(n);
      for (Node c : n.getChildren())
        preorder(c);
    }

    private void visit(Node n) {
      double growth = 1.6;

      // Update leaves
      if (n.isLeaf()) {
        if (leafId >= leaves.length) {
          // Grow
          int size = (int) (leaves.length * growth + 1);
          leaves = Arrays.copyOf(leaves, size);
        }
        leaves[leafId] = n;
        leafId++;
      }

      // Update byId
      if (n.id >= byId.length) {
        // Grow
        int size = (int) (Math.max(n.id, byId.length * growth) + 1);
        byId = Arrays.copyOf(byId, size);
      }
      byId[n.id] = n;
    }

    public Node getById(int id) {
      return byId[id];
    }

    public Node get(int terminal, int height) {
      Node n = leaves[terminal];
      for (int i = 0; i < height; i++)
        n = byId[n.getParent()];
      return n;
    }

    public List<Node> getLeaves(boolean includeTraces) {
      List<Node> leaves = new ArrayList<>();
      boolean sawNull = false;
      for (int i = 0; i < this.leaves.length; i++) {
        Node n = this.leaves[i];
        if (sawNull)
          assert n == null;
        if (n == null) {
          sawNull = true;
        } else if (includeTraces || !n.getCategory().equals("-NONE-")) {
          leaves.add(n);
        }
      }
      return leaves;
    }
  }

  /**
   * Wraps a segment of an S-expression (glorified string slice which knows
   * that its contents are another S-expression).
   */
  public static final class Node {

    public final int id;
    public final String source;
    private int parent;
    private int start, end;
    private List<Node> children;

    public Node(int id, int parent, String source) {
      if (id < 0)
        throw new IllegalArgumentException();
      this.id = id;
      this.parent = parent;
      this.source = source;
      this.children = null;
      this.start = -1;
      this.end = -2;
    }

    /**
     * Get character index into source string of start of this S-expression.
     * Must be set first.
     */
    public int getStart() {
      return start;
    }
    public void setStart(int start) {
      this.start = start;
    }

    /**
     * Get character index into source string of end of this S-expression.
     * Must be set first.
     */
    public int getEnd() {
      return end;
    }
    public void setEnd(int end) {
      this.end = end;
    }

    /**
     * Get the (int) id of the parent node.
     */
    public int getParent() {
      return parent;
    }
    public void setParent(int parent) {
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "<Node id=" + id + " parent=" + parent + " start=" + start + " end=" + end + ">";
    }

    public String getContents() {
      return source.substring(start + 1, end);
    }

    public boolean isRoot() {
      return parent < 0;
    }

    public boolean isLeaf() {
      return children == null;
    }

    public void addChild(Node n) {
      if (children == null)
        children = new ArrayList<>();
      children.add(n);
    }

    /**
     * Must have called addChild first.
     */
    public List<Node> getChildren() {
      if (children == null)
        return Collections.emptyList();
      return children;
    }

    /**
     * Assumes that this is a PTB-style node and strips the category off the
     * front, e.g. "(VP (V loves) (NP Mary))" => "VP".
     */
    public String getCategory() {
      int i = source.indexOf(' ', start);
      if (i <= start + 1 || i >= end)
        throw new IllegalStateException();
      return source.substring(start + 1, i);
    }

    /** Checks if this is a trace node by seeing if the category is "-NONE-" */
    public boolean isTrace() {
      return getCategory().equals("-NONE-");
    }

    /** If this is a leaf token, returns the word at this node */
    public String getWord() {
      if (!isLeaf())
        throw new RuntimeException("you can only call this on leaves");
      int i = source.indexOf(' ', start);
      return source.substring(i + 1, end);
    }
  }


  /**
   * Parses an S-expression and returns the root.
   */
  public static Node parse(String sexp) {
    return parse(sexp, 0, null);
  }

  /**
   * Parses an S-expression and returns the root.
   *
   * @param sexp must have a matched number of parens.
   * @param addTo may be null (in which case will be ignored)
   * @return the root node.
   */
  public static Node parse(String sexp, int startingId, List<Node> addTo) {
    if (sexp.charAt(0) != '(' || sexp.charAt(sexp.length() - 1) != ')')
      throw new IllegalArgumentException();
    if (startingId < 0)
      throw new IllegalArgumentException();

    int id = startingId;
    List<Node> complete = (addTo == null ? new ArrayList<>() : addTo);
    Deque<Node> open = new ArrayDeque<>();
    Node node;
    int n = sexp.length();
    for (int i = 0; i < n; i++) {
      switch (sexp.charAt(i)) {
        case '(':
          node = new Node(id++, -1, sexp);
          node.setStart(i);
          open.push(node);
          break;
        case ')':
          node = open.pop();
          node.setEnd(i);
          if (open.size() > 0) {
            Node parent = open.peek();
            node.setParent(parent.id);
            parent.addChild(node);
          }
          complete.add(node);
          break;
        default:
          break;
      }
    }
    // Root is the last node completed
    return complete.get(complete.size() - 1);
  }

  // Sanity check
  public static void main(String[] args) {
    // head -n 20 ontonotes-release-4.0/data/files/data/english/annotations/bc/cnn/00/cnn_0000.parse | tr '\n' ' ' | perl -pe 's/\s+/ /g'
    String example = "(TOP (FRAG (NP (DT A) (ADJP (ADVP (RB much) (RBR better)) (VBG looking)) (NNP News) (NNP Night)) (PRN (S (NP-SBJ (PRP I)) (VP (MD might) (VP (VB add) (FRAG (-NONE- *?*)))))) (SBAR-TMP (IN as) (S (NP-SBJ (NNP Paula) (NNP Zahn)) (VP (VBZ sits) (PRT (RP in)) (PP-CLR (IN for) (NP (NNP Anderson) (CC and) (NNP Aaron)))))) (. /.)))";
    List<Node> nodes = new ArrayList<>();
    System.out.println("root = " + parse(example, 0, nodes));
    for (int i = 0; i < nodes.size(); i++) {
      Node n = nodes.get(i);
      System.out.println("\t" + i + "\t" + n + " \"" + n.getContents() + "\"");
      for (Node child : n.getChildren())
        System.out.println("\t\t" + child + " " + child.getCategory());
    }
  }
}
