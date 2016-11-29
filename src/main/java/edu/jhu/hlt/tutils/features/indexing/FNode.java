package edu.jhu.hlt.tutils.features.indexing;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import edu.jhu.hlt.tutils.IntPair;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.prim.list.IntArrayList;

/**
 * A trie of features and their indices. You build the trie with strings/T and
 * compute things like feature frequency. This class then assigns those features
 * a two-part int index: (type, feature),
 * e.g. "head/word/John" => {type="head/word", feature="John"} => {type=42, feature=9001}
 *
 * @author travis
 */
public class FNode<T> implements Serializable {
  private static final long serialVersionUID = -984679673466640586L;

  private int globalType = -1;
  private int globalFeature = -1;   // for one-part feature indices
  private int localFeature = -1;    // uniquely identifies this node w.r.t. its parent
  private int nObs = 0;   // how many times did we see this feature while building?
  private T localObj;     // e.g. "John"
  private Map<T, FNode<T>> children;

  // root will have null parent, so need extra boolean to tell whether parents have been built or not
  private FNode<T> parent;
  private boolean parentIsSet = false;
  
  // TODO add weight, embedding, gradient, ssGradient?
  
  public static <T> FNode<T> root() {
    return new FNode<>(null);
  }
  
  public FNode(T obj) {
    this.localObj = obj;
    this.children = new HashMap<>();
  }
  
  /** Only call this on the root of the trie */
  public void buildParents() {
    buildParents(null);
  }
  private void buildParents(FNode<T> parent) {
    this.parent = parent;
    this.parentIsSet = true;
    for (FNode<T> c : children.values())
      c.buildParents(this);
  }
  
  public FNode<T> getParent() {
    if (!parentIsSet)
      throw new IllegalStateException("have to build parent pointers first");
    return parent;
  }
  
//  @SuppressWarnings("unchecked")
//  public T[] getObjectPath() {
  public List<T> getObjectPath() {
    Deque<T> path = new ArrayDeque<>();
    for (FNode<T> cur = this; cur != null; cur = cur.getParent())
      if (cur.getObject() != null)
        path.push(cur.getObject());
//    Object[] t = new Object[path.size()];
//    for (int i = 0; i < t.length; i++)
//      t[i] = path.pop();
//    return (T[]) t;
    List<T> p = new ArrayList<>(path.size());
    while (!path.isEmpty())
      p.add(path.pop());
    return p;
  }
  
  public T getObject() {
    return localObj;
  }
  
  public int getNumObs() {
    return nObs;
  }
  
  public IntPair getTypeFeat() {
    assert isValidFeature();
    int t = getParent() == null ? 0 : getParent().globalType;
    return new IntPair(t, localFeature);
  }
  
  public int getFlatFeat() {
    assert isValidFeature();
    return globalFeature;
  }
  
  public int getNumChildren() {
    return children.size();
  }
  
  public int getNumValidChildren() {
    int n = 0;
    for (FNode<T> c : children.values())
      if (c.isValidFeature())
        n++;
    return n;
  }
  
  public Iterable<FNode<T>> getChildren() {
    return children.values();
  }
  
  @Override
  public String toString() {
    return "(FNode " + localObj + " nObs=" + nObs + " gf=" + globalFeature + " gt=" + globalType + " lf=" + localFeature + ")";
  }
  
  /**
   * Includes all features (nodes) between root and the end of the given path (values).
   */
  public void extract(int offset, T[] values, List<IntPair> addTo) {
    if (offset >= values.length)
      return;
    if (!isValidType())
      return;
    FNode<T> c = children.get(values[offset]);
    if (c != null && c.isValidFeature()) {
      addTo.add(new IntPair(globalType, c.localFeature));
      c.extract(offset+1, values, addTo);
    }
  }
  
  /**
   * Includes all features (nodes) between root and the end of the given path (values).
   */
  public void extract(int offset, T[] values, IntArrayList addTo) {
    if (offset >= values.length)
      return;
    if (!isValidFeature())
      return;
    addTo.add(globalFeature);
    FNode<T> c = children.get(values[offset]);
    if (c != null)
      c.extract(offset+1, values, addTo);
  }
  
  public void add(int offset, T[] values) {
    nObs++;
    if (offset >= values.length)
      return;
    FNode<T> c = children.get(values[offset]);
    if (c == null) {
      c = new FNode<>(values[offset]);
      children.put(values[offset], c);
    }
    c.add(offset+1, values);
  }
  
  public FNode<T> get(int offset, T[] values) {
    FNode<T> c = this;
    for (int i = offset; c != null && i < values.length; i++)
      c = c.children.get(values[i]);
    return c;
  }
  
  public boolean isValidType() {
    return globalFeature >= 0;
  }
  public boolean isValidFeature() {
    return localFeature >= 0;
  }
  
  /**
   * returns the number of features added
   */
  private int assignFeatureIndices(int minObs, int nextAvailableGlobalFeature) {
    List<FNode<T>> kids = new ArrayList<>();
    for (FNode<T> x : children.values()) {
      assert x.globalFeature < 0;
      assert x.localFeature < 0;
      if (x.nObs >= minObs)
        kids.add(x);
    }
    Collections.sort(kids, new Comparator<FNode<T>>() {
      @Override
      public int compare(FNode<T> o1, FNode<T> o2) {
        return o1.nObs - o2.nObs;
      }
    });
    for (int i = 0; i < kids.size(); i++) {
      FNode<T> k = kids.get(i);
      k.localFeature = i;
      k.globalFeature = i + nextAvailableGlobalFeature;
    }
    return kids.size();
  }
  
  public static Comparator<FNode<?>> BY_COUNT_ASC = new Comparator<FNode<?>>() {
    @Override
    public int compare(FNode<?> o1, FNode<?> o2) {
      if (o1.nObs > o2.nObs)
        return -1;
      if (o1.nObs < o2.nObs)
        return +1;
      return 0;
    }
  };

  public static Comparator<FNode<?>> BY_MEH_ASC = new Comparator<FNode<?>>() {
    @Override
    public int compare(FNode<?> o1, FNode<?> o2) {
      double a = o1.nObs - Math.sqrt(o1.children.size());
      double b = o2.nObs - Math.sqrt(o2.children.size());
      if (a > b)
        return -1;
      if (a < b)
        return +1;
      return 0;
    }
  };
  

  /**
   * After adding to a root trie many times, use this class to assign indices
   * to the nodes in the trie.
   */
  public static class AssignIndices<T> {
    private FNode<T> root;
    public boolean verbose = false;
    
    public AssignIndices(FNode<T> root) {
      if (root.nObs < 1 || root.globalType >= 0) {
        throw new IllegalArgumentException("root must have observations but no indices");
      }
      this.root = root;
    }
    
    public void run() {
      int minObs = 5;
      int maxTypes = 100;
      int maxFeats = 1 << 20;
      run(minObs, maxTypes, maxFeats);
    }
    
    public void run(int minObs, int maxTypes, int maxFeats) {
      Log.info("minObs=" + minObs + " maxTypes=" + maxTypes + " maxFeats=" + maxFeats);
      // Three ways to prune the feature space
      // TODO: No way to merge values,
      // e.g. say n is the last type allowed by maxTypes,
      //  and say all n's children have counts > minObs,
      //  and n even has some grand-children which satisfy minObs...
      //  would be nice to consider the two edges (toChild, toGrandchild) as one node which receives its own feature.
      // This would break local index through! localFeat might be w.r.t. parent.globalType or grandParent.globalType!
      // We could have a localFeatureParent and localFeatureGrandparent, but we would need to ensure that only one of
      // them is valid/assigned for each node.
      // THATS NOT true, they can both be assigned (we still want backoff features), but if localFeatureGrandparent
      // is assigned, its parent must not have globalType set.

      PriorityQueue<FNode<T>> pq = new PriorityQueue<>(16, FNode.BY_MEH_ASC);
      root.globalFeature = 0;
      root.globalType = 0;
      root.localFeature = 0;
      pq.add(root);

      int assignedFeats = 1;
      int assignedTypes = 1;
      while (assignedTypes < maxTypes && assignedFeats < maxFeats && !pq.isEmpty()) {
        FNode<T> n = pq.poll();
        assignedFeats += n.assignFeatureIndices(minObs, assignedTypes);
        n.globalType = assignedTypes++;
        if (verbose)
          Log.info("added " + n);
        for (FNode<T> c : n.children.values())
          if (!c.children.isEmpty())
            pq.add(c);
      }
      Log.info("assigned " + assignedTypes + " types and " + assignedFeats + " features");
    }
  }
 
//  public static void main(String[] args) {
//    if (args.length != 3) {
//      // TODO
//    }
//    File in = new File(args[0]);
//  }
}