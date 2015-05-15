package edu.jhu.hlt.tutils;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
   * Wraps a segment of an S-expression (glorified start and end).
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

    public int getStart() {
      return start;
    }
    public void setStart(int start) {
      this.start = start;
    }

    public int getEnd() {
      return end;
    }
    public void setEnd(int end) {
      this.end = end;
    }

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

    return addTo.get(addTo.size() - 1);
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
