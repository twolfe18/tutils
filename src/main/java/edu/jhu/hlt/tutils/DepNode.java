package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

import edu.jhu.hlt.tutils.hash.Hash;
import edu.jhu.prim.tuple.Pair;

/**
 * A node in a compact dependency tree with POS tags (12 bytes + sizeof(pointer) per node/word).
 * For example, 300k length-33 sentences would take up 200 MB.
 * Parses are arrays of these nodes (array index == word index).
 * 
 * Includes parent, child, and sibling pointers.
 * 
 * Can read from CoNLL-X (e.g. parsey).
 *
 * @author travis
 */
public class DepNode implements Serializable {
  private static final long serialVersionUID = -4813744691315373711L;

  // 12 bytes = 4 + 1 + 1 + 2 + 2 + 2
  public int word;
  public byte pos;
  public byte depParentLabel;
  public short depParentNode;
  public short depLeftChildNode;
  public short depRightSibNode;

  /*
   * TODO Have a new method which uses 1-indexing with n+1 Token's per sentence?
   * This allows you to have sentence[0] as the root, get children of root easily
   */
  
  public static boolean equals(DepNode[] p1, DepNode[] p2) {
    if (p1.length != p2.length)
      return false;
    for (int i = 0; i < p1.length; i++)
      if (!safeEq(p1[i], p2[i]))
        return false;
    return true;
  }
  
  public static boolean safeEq(DepNode a, DepNode b) {
    if (a == null || b == null)
      return a == b;
    return a.equals(b);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other instanceof DepNode) {
      DepNode d = (DepNode) other;
      return word == d.word
          && pos == d.pos
          && depParentLabel == d.depParentLabel
          && depParentNode == d.depParentNode
          && depLeftChildNode == d.depLeftChildNode
          && depRightSibNode == d.depRightSibNode;
    }
    return false;
  }

  public static long hash64(DepNode[] p, long seed, boolean hashWord, boolean hashPos, boolean hashHead, boolean hashLabel) {
    assert !(hashLabel && !hashHead);
    assert hashWord || hashPos || hashHead || hashLabel;
    long h = seed;
    if (hashPos) {
      for (int i = 0; i < p.length; i++)
        h = Hash.mix64(h, p[i].pos);
    }
    if (hashLabel) {
      for (int i = 0; i < p.length; i++)
        h = Hash.mix64(h, p[i].depParentLabel);
    }
    if (hashHead) {
      for (int i = 0; i < p.length; i++)
        h = Hash.mix64(h, p[i].depParentNode);
    }
    if (hashWord) {
      for (int i = 0; i < p.length; i++)
        h = Hash.mix64(h, p[i].word);
    }
    return h;
  }
  
  public static int getShallowest(Span span, DepNode[] parse) {
    int[] dp = depths(parse);
    return getShallowest(span, dp);
  }

  public static int getShallowest(Span span, int[] dp) {
    int s = span.start;
    for (int i = span.start+1; i < span.end; i++)
      if (dp[i] < dp[s])
        s = i;
    return s;
  }
  
  public static Span getExtent(int node, DepNode[] parse) {
    int left = node;
    int right = node;
    for (int cur = parse[node].depLeftChildNode; cur >= 0; cur = parse[cur].depLeftChildNode) {
      left = Math.min(left, cur);
      int rm = rightmostSibling(cur, parse);
      right = Math.max(right, rm);
    }
    return Span.getSpan(left, right+1);
  }
  
  public static int rightmostSibling(int node, DepNode[] parse) {
    int prev = node;
    for (int i = node; i >= 0; i = parse[i].depRightSibNode)
      prev = i;
    return prev;
  }
  
  public static List<Edge> getEdgesLeavingSpan(Span span, DepNode[] parse, MultiAlphabet a) {
    return getEdgesLeavingSpan(span.start, span.end, parse, a);
  }
  public static List<Edge> getEdgesLeavingSpan(int start, int end, DepNode[] parse, MultiAlphabet a) {
    List<Edge> out = new ArrayList<>();
    for (int i = 0; i < parse.length; i++) {
      int h = parse[i].depParentNode;
      boolean ic = start <= i && i < end;
      boolean hc = start <= h && h < end;
      if (hc != ic) {
        String lab = a.dep(parse[i].depParentLabel);
        String head = hc ? "SOURCE" : (h < 0 ? "ROOT" : a.word(parse[h].word));
        String mod = ic ? "SOURCE" : (i < 0 ? "ROOT" : a.word(parse[i].word));
        out.add(new Edge(lab, head, mod, h, i));
      }
    }
    return out;
  }
  
  public static class ShortestPath {
    public final int source, target;
    public final DepNode[] parse;
    private int commonParent;
    
    public ShortestPath(int source, int target, DepNode[] parse) {
      if (source < 0 || target < 0 || source >= parse.length || target >= parse.length)
        throw new IllegalArgumentException("source=" + source + " target=" + target + " n=" + parse.length);
      this.source = source;
      this.target = target;
      this.parse = parse;
      this.commonParent = -1;
      BitSet parentOfFirst = new BitSet(parse.length);
      for (int p = source; p >= 0; p = parse[p].depParentNode)
        parentOfFirst.set(p);
      for (int p = target; p >= 0; p = parse[p].depParentNode) {
        if (parentOfFirst.get(p)) {
          commonParent = p;
          break;
        }
      }
    }
    
    public int getCommonParent() {
      return commonParent;
    }
    
    /** t must be a grand*-parent of s */
    private List<Edge> pathHelper(int s, int t, MultiAlphabet a, IntFunction<String> showNode, boolean starTheEdges) {
      List<Edge> e = new ArrayList<>();
      if (s == t)
        return e;
      for (int p = s; p >= 0; p = parse[p].depParentNode) {
        int h = parse[p].depParentNode;
        int m = p;
        String hs = showNode.apply(h);
        String ms = showNode.apply(m);
        String es = a.dep(parse[m].depParentLabel);
        if (starTheEdges)
          es = es + "*";
        e.add(new Edge(es, hs, ms, h, m));
        if (h == t)
          break;
      }
      return e;
    }
    
    // TODO take a show function for nodes, put the returned value in head/mod for Edge
    public List<Edge> buildPath(MultiAlphabet a, boolean hideEndpoints, boolean usePosInsteadOfWords, boolean lowercaseWords) {
      IntFunction<String> showNode = i -> {
        if (i < 0)
          return "ROOT";
        if (hideEndpoints) {
          if (i == source)
            return "SOURCE";
          if (i == target)
            return "TARGET";
        }
        if (usePosInsteadOfWords)
          return a.pos(parse[i].pos);
        if (lowercaseWords)
          return a.word(parse[i].word).toLowerCase();
        return a.word(parse[i].word);
      };
      // s -> c
      List<Edge> path = pathHelper(source, commonParent, a, showNode, true);
      // c -> t
      List<Edge> rev = pathHelper(target, commonParent, a, showNode, false);
      Collections.reverse(rev);
      path.addAll(rev);
      return path;
    }
    
    public static List<Edge> replaceDigits(List<Edge> in) {
      int n = in.size();
      List<Edge> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        Edge e = in.get(i);
        String h = e.head.replaceAll("\\d", "0");
        String m = e.mod.replaceAll("\\d", "0");
        out.add(new Edge(e.label, h, m, e.headIdx, e.modIdx));
      }
      return out;
    }

    public static List<Edge> mapHeadsAndMods(List<Edge> in, Function<String, String> f) {
      int n = in.size();
      List<Edge> out = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        Edge e = in.get(i);
        String h = f.apply(e.head);
        String m = f.apply(e.mod);
        out.add(new Edge(e.label, h, m, e.headIdx, e.modIdx));
      }
      return out;
    }
    
    /*
     * Research idea: not all ngrams are created equal. Some are very frequent and
     * should basically be memorized.
     * e.g. prt(gave,up) should basically not be counted as an edge in the same sense
     * as dobj(loves,walnuts).
     * This has to do with information content and representation in memory.
     * prt(gave,up) may be so small that there may be other things which extend it
     * which could even be memorized, such as "give up smoking".
     * In checking the parses that syntaxnet produces, it is not easy to make this
     * phenomenon happen with dependency paths because the produced representation
     * is so flat. Particles, modals, and auxiliary verbs are all children of the
     * "main" verb. In this sort of representation you need to look at the node to
     * determine that an edge hanging off should be memorized.
     * 
     * Easy example is for news, you would memorize all of the ccomp(said,VB*) edges.
     * Why is this good?
     * If you take the view of "contracting the parse tree by collapsing edges", then
     * what you are doing is enforcing some sense of homogeneity among nodes in the graph.
     * On its own this seems like a OK pre-processing/pre-conditioning step.
     * But I believe this has consequences for SRL as well:
     * Imagine that there really is a function from dep-path => role which can be learned
     * as long as you know the fine grain sense (which I believe is how linguists describe it).
     * The domain of this function is determined by the syntax representation, which changes
     * if you base it on counting.
     */
    public static List<Edge[]> ngrams(int n, List<Edge> path) {
//      return ngrams(n, path, true);
      return ngrams(n, path, false);
    }
    public static List<Edge[]> ngrams(int n, List<Edge> path, boolean includePadding) {
      int x = path.size();
      int pad = includePadding ? n-1 : 0;
      Edge[] all = new Edge[x + 2*pad];
      for (int i = 0; i < pad; i++) {
        all[i] = Edge.BEFORE;
        all[all.length-(i+1)] = Edge.AFTER;
      }
      for (int i = 0; i < x; i++)
        all[i+pad] = path.get(i);
      List<Edge[]> ng = new ArrayList<>();
      for (int i = 0; i < all.length-(n-1); i++) {
        Edge[] nn = Arrays.copyOfRange(all, i, i+n);
        ng.add(nn);
      }
      return ng;
    }
    
    /**
     * e.g. "appos(SOURCE,graduate)-prep(graduate,from)-pobj(from,TARGET)"
     *   => "appos(SOURCE,#1)-prep(#1,#2)-pobj(#2,TARGET)"
     */
    public static String stripLexPath(String path) {
      String[] a = path.split("\\)-");
      StringBuilder sb = new StringBuilder();
      List<String> vars = new ArrayList<>();
      for (int i = 0; i < a.length; i++) {
        if (i > 0)
          sb.append("-");
        sb.append(stripLexTerm(a[i] + ")", vars));
      }
      return sb.toString();
    }
    public static String stripLexTerm(String term, List<String> vars) {
      int open = term.indexOf('(');
      int sep = term.indexOf(',');
      int close = term.indexOf(')');
      String pre = term.substring(0, open);
      String left = term.substring(open+1, sep);
      String right = term.substring(sep+1, close);
      
      boolean ls = left.matches("[A-Z]{2,}");
      boolean rs = right.matches("[A-Z]{2,}");
      
      // Find (left|right) which matches the head of the stack
      int n = vars.size();
      if (n > 0 && !ls && left.equals(vars.get(n-1))) {
        left = "#" + n;
      } else if (n > 0 && !rs && right.equals(vars.get(n-1))) {
        right = "#" + n;
      }
      
      boolean lr = left.startsWith("#") || left.matches("[A-Z]{2,}");
      boolean rr = right.startsWith("#") || right.matches("[A-Z]{2,}");
      if (!lr) {
        vars.add(left);
        left = "#" + vars.size();
      } else if (!rr) {
        vars.add(right);
        right = "#" + vars.size();
      }

//      if (n > 0 && !ls && left.equals(vars.get(n-1))) {
//        left = "#" + n;
//        if (rs)
//          right = "#" + (n+1);
//      } else if (n > 0 && !rs && right.equals(vars.get(n-1))) {
//        right = "#" + n;
//        if (ls)
//          left = "#" + (n+1);
//      } else {
//        // Either left or right should be SOURCE
//        assert ls || rs;
//        if (ls) {
//          vars.add(right);
//          right = "#1";
//        } else {
//          vars.add(left);
//          left = "#1";
//        }
//      }


//      if (!left.matches("[A-Z]{2,}")) {
//        int n = vars.size();
//        if (n > 0 && vars.get(n-1).equals(left)) {
//          left = "#" + n;
//        } else {
//          vars.add(left);
//          left = "#" + (n+1);
//        }
//      }
//
//      if (!right.matches("[A-Z]{2,}")) {
//        int n = vars.size();
//        if (n > 0 && vars.get(n-1).equals(right)) {
//          right = "#" + n;
//        } else {
//          vars.add(right);
//          right = "#" + (n+1);
//        }
//      }

      return pre + "(" + left + "," + right + ")";
    }

    public static void debugStripLexPath() {
      List<Pair<String, String>> xys = new ArrayList<>();
      xys.add(new Pair<>(
          "appos(SOURCE,graduate)-prep(graduate,from)-pobj(from,TARGET)",
          "appos(SOURCE,#1)-prep(#1,#2)-pobj(#2,TARGET)"));
      xys.add(new Pair<>(
          "foo(SOURCE,0000-0000)",
          "foo(SOURCE,#1)"));
      xys.add(new Pair<>(
          "appos*(president,SOURCE)-conj*(minister,president)-nn(minister,TARGET)",
          "appos*(#1,SOURCE)-conj*(#2,#1)-nn(#2,TARGET)"));
      for (Pair<String, String> xy : xys) {
        String yhat = DepNode.ShortestPath.stripLexPath(xy.get1());
        System.out.println(xy.get1());
        System.out.println(xy.get2());
        System.out.println(yhat);
        assert xy.get2().equals(yhat);
      }
    }
    
    // (Assembly.NNP decides.VBZ send.VB question.NN to.IN convention.NN held.VBN Augusta.NNP)
    static final String[] foo = new String[] {
        "1	The	_	DET	DT	_	4	det	_	_",
        "2	Georgia	_	NOUN	NNP	_	4	nn	_	_",
        "3	General	_	NOUN	NNP	_	4	nn	_	_",
        "4	Assembly	_	NOUN	NNP	_	5	nsubj	_	_",
        "5	decides	_	VERB	VBZ	_	0	ROOT	_	_",
        "6	to	_	PRT	TO	_	7	aux	_	_",
        "7	send	_	VERB	VB	_	5	xcomp	_	_",
        "8	the	_	DET	DT	_	9	det	_	_",
        "9	question	_	NOUN	NN	_	7	dobj	_	_",
        "10	of	_	ADP	IN	_	9	prep	_	_",
        "11	ratification	_	NOUN	NN	_	10	pobj	_	_",
        "12	to	_	ADP	IN	_	7	prep	_	_",
        "13	a	_	DET	DT	_	15	det	_	_",
        "14	special	_	ADJ	JJ	_	15	amod	_	_",
        "15	convention	_	NOUN	NN	_	12	pobj	_	_",
        "16	to	_	PRT	TO	_	18	aux	_	_",
        "17	be	_	VERB	VB	_	18	auxpass	_	_",
        "18	held	_	VERB	VBN	_	15	infmod	_	_",
        "19	in	_	ADP	IN	_	18	prep	_	_",
        "20	Augusta	_	NOUN	NNP	_	19	pobj	_	_",
        "21	,	_	.	,	_	20	punct	_	_",
        "22	Georgia	_	NOUN	NNP	_	20	appos	_	_",
        "23	.	_	.	.	_	5	punct	_	_",
    };
  }
    
  /**
   * Dependency edge labels either look like "nsubj" or "nsubj*",
   * where the latter means traversing the edge from modifier to head,
   * and the former means traversing from head to modifier.
   */
  public static class Edge {
    public final String label;
    public final String head, mod;
    public final int headIdx, modIdx;

    public Edge(int head, int mod, DepNode[] parse, MultiAlphabet a) {
      this.headIdx = head;
      this.modIdx = mod;
      if (head < 0)
        this.head = "ROOT";
      else
        this.head = a.word(parse[head].word);
      this.mod = a.word(parse[mod].word);
      this.label = a.dep(parse[mod].depParentLabel);
    }

    public Edge(String label, String head, String mod) {
      this(label, head, mod, -1, -1);
    }

    public Edge(String label, String head, String mod, int headIdx, int modIdx) {
      this.label = label;
      this.head = head;
      this.mod = mod;
      this.headIdx = headIdx;
      this.modIdx = modIdx;
    }
    
    public Edge mapHeadAndMod(Function<String, String> f) {
      return new Edge(label, f.apply(head), f.apply(mod), headIdx, modIdx);
    }

    @Override
    public String toString() {
      return label + "(" + head + "," + mod + ")";
    }

    /** returns strings like "nsubj*(loves,John)-dobj(loves,Mary)" */
    public static String ngramStr(Edge[] ngram) {
      StringBuilder sb = new StringBuilder();
      sb.append(ngram[0].toString());
      for (int i = 1; i < ngram.length; i++) {
        sb.append('-');
        sb.append(ngram[i].toString());
      }
      return sb.toString();
    }

    public static final Edge BEFORE = new Edge("offpath*", "h", "m");
    public static final Edge AFTER = new Edge("offpath", "h", "m");
  }
  
  public static int[] depths(DepNode[] sentence) {
    // Just do O(n*depth) algorithm of walking up from every token
    int[] d = new int[sentence.length];
    for (int i = 0; i < d.length; i++) {
      for (int p = i; p >= 0; p = sentence[p].depParentNode)
        d[i]++;
    }
    return d;
  }
  
  public static int[] distances(int source, DepNode[] sentence) {
    int n = sentence.length;
    int[] minDist = new int[n];
    Arrays.fill(minDist, n);
    
    // BFS from source
    // Entries are (node, distFromSource)
    BitSet seen = new BitSet(n);
    Deque<IntPair> q = new ArrayDeque<>();
    q.addLast(new IntPair(source, 0));
    seen.set(source);
    while (q.isEmpty()) {
      IntPair nd = q.pollFirst();
      int token = nd.first;
      int dist = nd.second;
      DepNode t = sentence[token];
      
      // Process
      minDist[token] = Math.min(minDist[token], dist);
      
      // Neighbors
      if (!seen.get(t.depParentNode)) {
        seen.set(t.depParentNode);
        q.addLast(new IntPair(t.depParentNode, dist+1));
      }
      for (int c = t.depLeftChildNode; c >= 0; c = sentence[c].depRightSibNode) {
        if (!seen.get(c)) {
          seen.set(c);
          q.addLast(new IntPair(c, dist+1));
        }
      }
    }
    
    return minDist;
  }
  
//  public static List<Integer> children(int node, Token[] sentence) {
//    List<Integer> children = new ArrayList<>();
//    for (int c = sentence[node].depLeftChildNode; c >= 0; c = sentence[c].depRightSibNode)
//      children.add(c);
//    return children;
//  }
  
  public static DepNode[] readConllx_0IndexedNoRoot(List<String> lines, MultiAlphabet alph) {
    int n = lines.size();
    DepNode[] sent = new DepNode[n];
//    MultiMap<Integer, Integer> p2c = new MultiMap<>();
    Map<Integer, List<Integer>> p2c = new HashMap<>();
    int[] leftChild = new int[n];
    Arrays.fill(leftChild, -1);
    for (int i = 0; i < n; i++) {
      String[] c = lines.get(i).split("\t");

      int ii = Integer.parseInt(c[0]);
      assert i+1 == ii;
      assert ii < Short.MAX_VALUE;
      assert ii >= 0;

      sent[i] = new DepNode();
      sent[i].word = alph.word(c[1]);
      sent[i].pos = i2b(alph.pos(c[4]));
      sent[i].depParentNode = i2s(Integer.parseInt(c[6])-1);
      sent[i].depParentLabel = i2b(alph.dep(c[7]));
      sent[i].depLeftChildNode = -1;
      sent[i].depRightSibNode = -1;
      
      int p = sent[i].depParentNode;
      if (p >= 0) {
//        p2c.add(p, i);
        List<Integer> li = p2c.get(p);
        if (li == null) {
          li = new ArrayList<>();
          p2c.put(p, li);
        }
        li.add(i);
        if (leftChild[p] < 0)
          leftChild[p] = i;   // i is increasing, so set once/first, aka left-most
      }
    }
    
    // Set left child
    for (int i = 0; i < n; i++)
      if (leftChild[i] >= 0)
        sent[i].depLeftChildNode = i2s(leftChild[i]);
    
    // Set right sib
    for (int p : p2c.keySet()) {
      List<Integer> cs = p2c.get(p);
      for (int i = 1; i < cs.size(); i++) {
        int c1 = cs.get(i-1);
        int c2 = cs.get(i);
        sent[c1].depRightSibNode = i2s(c2);
      }
    }

    return sent;
  }
  
  public static void show(DepNode[] parse, MultiAlphabet a) {
    show(parse, Span.getSpan(0, parse.length), a);
  }
  public static void show(DepNode[] parse, int head, MultiAlphabet a) {
    show(parse, Span.getSpan(head, head+1), a);
  }
  public static void show(DepNode[] parse, Span s, MultiAlphabet a) {
    int[] d = depths(parse);
    for (int i = s.start; i < s.end; i++) {
      int h = parse[i].depParentNode;
      String w = a.word(parse[i].word);
      String p = a.pos(parse[i].pos);
      String e = new DepNode.Edge(h, i, parse, a).toString();
//      System.out.printf("% 4d   %-20s   %-5s   % 4d   %s\n", i, w, p, h, e);
      System.out.printf("% 4d   %-20s   %-5s   d=% 3d  % 4d   %s\n", i, w, p, d[i], h, e);
    }
  }
  
  public static class ConllxFileReader implements Iterator<DepNode[]>, AutoCloseable {
    
    private MultiAlphabet a;
    private BufferedReader r;
    private List<String> curLines;
    
    public final boolean hideDeprels;
    
    public ConllxFileReader(File f, MultiAlphabet a, boolean hideDeprels) throws IOException {
      this.hideDeprels = hideDeprels;
      this.a = a;
      r = FileUtil.getReader(f);
      curLines = new ArrayList<>();
      advance();
    }
    
    public ConllxFileReader(InputStream is, MultiAlphabet a, boolean hideDeprels) throws IOException {
      this.hideDeprels = hideDeprels;
      this.a = a;
      r = new BufferedReader(new InputStreamReader(is));
      curLines = new ArrayList<>();
      advance();
    }
    
    public List<DepNode[]> readAll() {
      List<DepNode[]> a = new ArrayList<>();
      while (hasNext())
        a.add(next());
      return a;
    }
    
    public MultiAlphabet getAlphabet() {
      return a;
    }
    
    private void advance() throws IOException {
      curLines.clear();
      for (String line = r.readLine(); true; line = r.readLine()) {
        if (line == null) {
          curLines = null;    // EOF
          return;
        } else if (line.isEmpty()) {
          return;
        } else {
          curLines.add(line);
        }
      }
//      assert false : "malformed";
    }

    @Override
    public boolean hasNext() {
//      return !curLines.isEmpty();
      return curLines != null;
    }

    @Override
    public DepNode[] next() {
      DepNode[] t = readConllx_0IndexedNoRoot(curLines, a);
      if (hideDeprels) {
        int edge = a.dep("unk");
        assert edge <= Byte.MAX_VALUE && edge >= 0;
        for (int i = 0; i < t.length; i++)
          t[i].depParentLabel = (byte) edge;
      }
      try {
        advance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return t;
    }

    @Override
    public void close() throws IOException {
      r.close();
    }
  }
  
  static byte i2b(int i) {
    if (i > Byte.MAX_VALUE || i < Byte.MIN_VALUE)
      throw new IllegalArgumentException();
    return (byte) i;
  }

  static short i2s(int i) {
    if (i > Short.MAX_VALUE || i < Short.MIN_VALUE)
      throw new IllegalArgumentException();
    return (short) i;
  }
  
  public static void shortestPathDebug() {
    String[] conll = new String[] {
        "1	002:029	_	X	LS	_	0	ROOT	_	_",
        "2	-LRB-	_	.	-LRB-	_	1	punct	_	_",
        "3	As	_	ADP	IN	_	1	prep	_	_",
        "4	the	_	DET	DT	_	5	det	_	_",
        "5	children	_	NOUN	NNS	_	3	pobj	_	_",
        "6	of	_	ADP	IN	_	5	prep	_	_",
        "7	Esau	_	NOUN	NNP	_	6	pobj	_	_",
        "8	which	_	DET	WDT	_	9	nsubj	_	_",
        "9	dwell	_	VERB	VBP	_	5	rcmod	_	_",
        "10	in	_	ADP	IN	_	9	prep	_	_",
        "11	Seir	_	NOUN	NNP	_	10	pobj	_	_",
        "12	,	_	.	,	_	1	punct	_	_",
        "13	and	_	CONJ	CC	_	1	cc	_	_",
        "14	the	_	DET	DT	_	15	det	_	_",
        "15	Moabites	_	NOUN	NNPS	_	1	conj	_	_",
    };
    MultiAlphabet a = new MultiAlphabet();
    DepNode[] parse = readConllx_0IndexedNoRoot(Arrays.asList(conll), a);
    ShortestPath path = new ShortestPath(6, 10, parse);
    System.out.println("source: " + a.word(parse[path.source].word));
    System.out.println("target: " + a.word(parse[path.target].word));
    System.out.println("commonParent: " + a.word(parse[path.getCommonParent()].word));
    boolean hideEndpoints = false;
    boolean usePosInsteadOfWord = false;
    boolean lowercaseWords = false;
    List<Edge> p = path.buildPath(a, hideEndpoints, usePosInsteadOfWord, lowercaseWords);
    System.out.println("path: " + p);

    for (int n = 1; n <= 3; n++) {
      
//      List<Edge[]> ngOld = ShortestPath.ngrams(n, p);
//      List<Edge[]> ngNew = ShortestPath.ngrams(n, p, true);
//      assert ngOld.size() == ngNew.size();
//      for (int i = 0; i < ngOld.size(); i++)
//        assert Arrays.equals(ngOld.get(i), ngNew.get(i));
      
      for (Edge[] ng : ShortestPath.ngrams(n, p, false))
        System.out.println(n + "g: " + Edge.ngramStr(ng));
      System.out.println();
    }

    show(parse, a);
  }
  
  public static void test(ExperimentProperties config) throws Exception {
    File p = new File("/home/travis/code/data/clueweb09-freebase-annotation/gen-for-entsum");
    File f = new File(p, "parsed-sentences-rare4/parsed.conll");
    MultiAlphabet a = new MultiAlphabet();
    List<DepNode[]> ts = new ArrayList<>();
    TimeMarker tm = new TimeMarker();
    long toks = 0;
    boolean show = config.getBoolean("show", false);
    try (ConllxFileReader iter = new ConllxFileReader(f, a, false)) {
      while (iter.hasNext()) {
        DepNode[] parse = iter.next();
        toks += parse.length;
        ts.add(parse);
        if (show) {
          for (int i = 0; i < parse.length; i++) {
            DepNode t = parse[i];
            String w = a.word(t.word);
            String pos = a.pos(t.pos);
            String parent = "NA";
            String edge = "NA";
            if (t.depParentNode >= 0) {
              parent = a.word(parse[t.depParentNode].word);
              edge = a.dep(t.depParentLabel);
            }
            List<String> children = new ArrayList<>();
            for (int c = t.depLeftChildNode; c >= 0; c = parse[c].depRightSibNode)
              children.add(a.word(parse[c].word));
            System.out.printf("% 3d\t%-24s %-12s %-24s %-12s %s\n", i, w, pos, parent, edge, children);
          }
          System.out.println();
        }
        if (tm.enoughTimePassed(2))
          Log.info("nSent=" + ts.size() + "\tnTok=" + toks + "\t" + memoryUsage());
      }
    }
    Log.info("done\tnSent=" + ts.size() + "\tnTok=" + toks + "\t" + memoryUsage());
    System.out.println(a);
  }

  public static String memoryUsage() {
    Runtime r = Runtime.getRuntime();
    return String.format("MemoryUsage used=%.1fG free=%.1fG limit=%.1fG",
        r.totalMemory() / (1024 * 1024 * 1024d),
        r.freeMemory() / (1024 * 1024 * 1024d),
        r.maxMemory() / (1024 * 1024 * 1024d));
  }
  
  public static void testConllxReader(ExperimentProperties config) throws Exception {
    File p = new File("/home/travis/code/data/dbpedia/distsup-aligned-conll");
    File f = new File(p, "almaMater/pos/wikidata_economist-http:__dbpedia.org_ontology_Organisation/batch00005/parse.conll");
    MultiAlphabet a = new MultiAlphabet();
    try (ConllxFileReader r1 = new ConllxFileReader(f, a, false);
        ConllxFileReader r2 = new ConllxFileReader(FileUtil.getInputStream(f), a, false)) {
      List<DepNode[]> a1 = r1.readAll();
      List<DepNode[]> a2 = r2.readAll();
      assert a1.size() == a2.size();
      
      for (int i = 0; i < a1.size(); i++) {
        int seed = i;
        boolean hashWord = true;
        boolean hashPos = true;
        boolean hashHead = true;
        boolean hashLabel = true;
        long h1 = DepNode.hash64(a1.get(i), seed, hashWord, hashPos, hashHead, hashLabel);
        long h2 = DepNode.hash64(a2.get(i), seed, hashWord, hashPos, hashHead, hashLabel);
        assert h1 == h2;
      }
      Log.info("successfully checked " + a1.size() + " parses");
    }
    
  }
  
  public static void main(String[] args) throws Exception {
    ExperimentProperties config = ExperimentProperties.init(args);
//    test(config);
//    shortestPathDebug();
    testConllxReader(config);
  }
}
