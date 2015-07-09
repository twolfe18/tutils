package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * More or less the CoNLL format in memory, with some other influences which
 * keep things very tabular.
 *
 * TODO Dependency parses can be done like constituency parses instead of
 * needing another full array for every type of dependency parse. The cost is
 * that you need another int[] for token (that that label applies to). The
 * benefit is that if you have unparsed sentences or many types of dependency
 * parsers (and only a few will ever be populated at a given time), then you
 * don't need to waste space on them.
 *
 * TODO figure out a scheme by which we can de-allocate some of these token
 * indexed fields (e.g. checking if all the values are -2, then set to null).
 *
 * TODO Add dependency graphs for collapsed dependency representations.
 * @see edu.jhu.hlt.tutils.LabeledDirectedGraph
 *
 * NOTE: Ha! I could decide to go fully crazy and implement a memory allocator
 * in Document: have a free list for tokens and constituents.
 *
 * @author travis
 */
public final class Document implements Serializable {
  private static final long serialVersionUID = 1L;
  public static final int NONE = -1;    // terminator for linked lists (NIL)
  public static final int UNINITIALIZED = -2;

  public static boolean LOG_ALLOCATIONS = false;

  /* GENERAL FIELDS ***********************************************************/
  final String id;
  int index;
  private transient MultiAlphabet alph;

  // Whether the forwards method should allow constituent fields to be re-allocated
  private boolean allowExpansion = true;

  /* GRAPHS *******************************************************************/
  // A note on graphs:
  // One goal was to be able to have a Token which is just an index into the
  // document and be able to do a multi-graph traversal from just that int.
  // Constituency trees force use to have another type of index due to the
  // non-token-indexedness of constituents, but graphs go one step further
  // due to the unknown number of parents. I may try to fold in the adjacency
  // list style representation used in LabeledDirectedGraph into Document to be
  // flat and easy to use, but I have to work out how to do this later. TODO

  // Actually I think this is pretty easy:
  // Only need two fields: long[] edges and int[] splitPoints.
  // Just need to absorb LabeledDirectedGraph.Node into this class as GraphNode or something.
  // Maybe have a single long[] edges and many int[] splitPoints, one for each
  // parse type?

  // See http://nlp.stanford.edu/software/dependencies_manual.pdf
  LabeledDirectedGraph stanfordDepsBasic;
  LabeledDirectedGraph stanfordDepsCollapsed;
  LabeledDirectedGraph stanfordDepsCollapsedCC;

  // See http://universaldependencies.github.io/docs/u/overview/syntax.html
  LabeledDirectedGraph universalDependencies;


  /* MEMORY BOOK-KEEPING ******************************************************/
  int tokTop = 0;     // index of first un-used token
  int consTop = 0;    // index of first un-used constituent


  /* TOKEN-INDEXED FIELDS *****************************************************/

  int[] word;
  int[] wordNocase;
  int[] pos;
  int[] lemma;
  int[] wnSynset;
  int[] bc256;
  int[] bc1000;
  int[] shape;
  int[] ner;
  int[] sense;

  /* Pointers from token -> leaf constituent **********************************/
  // TODO Think about whether these should be kept at all.

  // The index of a constituency tree node immediately dominated by this token.
  // May be -1 for constituency trees that don't cover the entire span.
  // Constituents have an implicit type, which is which one of these fields you
  // got the constituent from
  int[] cons_parent_ptb_gold;
  int[] cons_parent_ptb_auto;

  // Propbank is represented as a list of predicate/argument nodes, each having
  // a `lhs` field specifying if it is e.g. "throw-v-1" (for a predicate) or
  // "ARG1" (for an argument). Each of these nodes has a single child, which is
  // either a real constituency node (which may be a part of another tree and
  // have a different parent) or a dummy span only created for the purpose of
  // specifying a span of text.
  // In this list of pred/arg nodes, the predicates come first. If an argument
  // is split (e.g. "ARG3" and "C-ARG3"), the the continuation argument must
  // immediately follow the continued role. Predicate nodes may also be split
  // (and should appear first and next to each other) in principle, but some
  // code may assume this never happens...
  // The nodes in this list will have a common parent denoting a proposition.
  // TODO Choose the parent/sibling structure of these proposition nodes. Could
  // 1) make them all siblings with a dummy root or 2) make propositions in the
  // same sentence siblings and insert a sentence level parent. I'm leaning
  // towards (1) in the name of remaining flat and leaving the document
  // structure up to the breaks field.
  // THESE ARRAYS are used to serve as a pointer from *predicate tokens* up to
  // predicate nodes. All tokens which do not trigger a predicate will have the
  // value -1. The reason this field is not used for arguments as well is that
  // a token may serve in multiple arguments (probably not in the same
  // proposition, but in different propositions in the same sentence).
  int[] cons_parent_propbank_gold;
  int[] cons_parent_propbank_auto;

  // TODO Clarify these. I had planned to support hierarchical NER.
  int[] cons_parent_ner_gold;
  int[] cons_parent_ner_auto;

  public static enum ConstituentType {
    PTB_GOLD,
    PTB_AUTO,
    PROPBANK_GOLD,
    PROPBANK_AUTO,
    NER_GOLD,
    NER_AUTO,
  }
  // Sort of arbitrary that this is flat vs a 2d array...
  public int[] getConsParent(ConstituentType consType) {
    switch (consType) {
      case PTB_GOLD:
        return cons_parent_ptb_gold;
      case PTB_AUTO:
        return cons_parent_ptb_auto;
      case PROPBANK_GOLD:
        return cons_parent_propbank_gold;
      case PROPBANK_AUTO:
        return cons_parent_propbank_auto;
      case NER_GOLD:
        return cons_parent_ner_gold;
      case NER_AUTO:
        return cons_parent_ner_auto;
      default:
        throw new RuntimeException("consType=" + consType);
    }
  }


  /* LINKED LISTS OF CONSTITUENTS *********************************************/

  // index of constituent corresponding to the first sentence
  public int cons_sentences = NONE;
  public int cons_paragraph = NONE;
  public int cons_section = NONE;

  // top level is linked list of cparses for sentences
  public int cons_ptb_gold = NONE;
  public int cons_ptb_auto = NONE;

  // top level is linked list of items like (PROP ... (ARG ...) (ARG ...) ...)
  // See above for note about handling Propbank continuation roles, etc.
  public int cons_propbank_gold = NONE;
  public int cons_propbank_auto = NONE;

  // top level is linked list of (NER lhs=type firstToken=i lastToken=j)
  public int cons_ner_gold = NONE;
  public int cons_ner_auto = NONE;

  // Linked list (use rightSib) of mentions for coref
  public int cons_coref_mention_gold = NONE;
  public int cons_coref_mention_auto = NONE;

  // Depth 2 tree (top level is entities, second level is mentions)
  // Entity level: lhs=Entity.type
  // Mention level: lhs=Entity.type
  public int cons_coref_gold = NONE;
  public int cons_coref_auto = NONE;

  /* END OF LINKED LIST OF CONSTITUENTS ***************************************/


  /* CONSTITUENT-INDEXED FIELDS ***********************************************/
  int[] lhs;          // left hand side of a CFG rule, e.g. "NP" or "SBAR", NOT a POS tag (that would mean 1 Constituent per word, we want to stay one level higher than that)

  int[] leftChild;    // < 0 for leaf nodes
  int[] rightSib;

  int[] firstToken;   // Document token index, inclusive
  int[] lastToken;    // Document token index, inclusive

  // Things below this line need not be saved (can be re-derived)
  int[] parent;       // Constituent index, -1 for roots
  int[] rightChild;   // < 0 for leaf nodes
  int[] leftSib;
  int[] depth;


  // The working plan for {@link Situation}s and {@link Entity}s is to put
  // them in as constituency trees. See the description of how Propbank is
  // encoded for an example of how this can work. The only issue that may come
  // up is how to fit all of the needed information into `lhs`. Maybe adding
  // a field (int or long) would solve the problem. If we need a lot more bits
  // than that, then I may have to give up on the simplicity of uniform
  // constituency trees.

  // TODO Concerning the problem of fitting enough information into `lhs` and
  // the need for features: I think I'm going to have to add
  // constituent -> FeatureVector and token -> FeatureVector
  // mappings.

  /* END OF FIELDS ************************************************************/


  /* CONVENIENCE METHODS ******************************************************/

  /**
   * Returns a map from (firstToken,lastToken) to constituentIndex for all
   * constituents in the link list pointed to by firstConsIdx. This assumes that
   * the Constituents are from a cparse, and therefore everything under the
   * items in the linked list are cparse constituents with first and last tokens
   * set.
   */
  public Map<IntPair, Integer> getSpanToConstituentMapping(int firstConsIdx) {
    Map<IntPair, Integer> cons = new HashMap<>();
    for (ConstituentItr ci = getConstituentItr(firstConsIdx); ci.isValid(); ci.gotoRightSib())
      for (ConstituentItr root = getConstituentItr(ci.getLeftChild()); root.isValid(); root.gotoRightSib())
        addCparseSpans(cons, root);
    return cons;
  }
  /** Keeps the shallowest (closest to root) span if they're not all unique */
  private static void addCparseSpans(Map<IntPair, Integer> m, Constituent c) {
    // Add this span
    int f = c.getFirstToken();
    int l = c.getLastToken();
    if (f < 0 || l < 0 || l < f)
      throw new IllegalArgumentException("f=" + f + " l=" + l);
    IntPair k = new IntPair(f, l);
    int v = c.getIndex();
    Integer old = m.put(k, v);
    if (old != null) {
      int oldDepth = c.getDocument().getConstituent(old).getDepth();
      assert c.getDepth() < 0 || oldDepth < 0 || oldDepth < c.getDepth();
      m.put(k, old);  // put back the old (shallower) constituent)
    }
    // Recurse
    Document doc = c.getDocument();
    for (ConstituentItr child = doc.getConstituentItr(c.getLeftChild());
        child.isValid(); child.gotoRightSib()) {
      addCparseSpans(m, child);
    }
  }

  /**
   * Traverses a linked list of constituents (using gotoRightSib()) and extracts
   * the result of the provided function.
   * @param consIdx is the index of the first constituent.
   * @param f extracts a value from a {@link Constituent}
   */
  public BitSet extractAllConstituents(int consIdx, ToIntFunction<Constituent> f) {
    BitSet bs = new BitSet(lhs.length);
    for (ConstituentItr i = getConstituentItr(consIdx); i.isValid(); i.gotoRightSib()) {
      int v = f.applyAsInt(i);
      bs.set(v);
    }
    return bs;
  }

  /* END OF CONVENIENCE METHODS ***********************************************/


  public Document(String id, int index, MultiAlphabet alph) {
    this.id = id;
    this.index = index;
    this.alph = alph;
    int initSize = 64;
    reserveTokens(initSize);
    reserveConstituents(initSize);
  }

  /** May be slow: uses java serialization (for safety) */
  public Document copy() {
    return SerializationUtils.cloneViaSerialization(this);
  }

  public String getId() {
    return id;
  }

  public int getIndex() {
    return index;
  }

  public MultiAlphabet getAlphabet() {
    return alph;
  }

  public void setAlphabet(MultiAlphabet alph) {
    this.alph = alph;
  }

  public int numTokens() {
    return tokTop;
  }

  public int numConstituents() {
    return consTop;
  }

  public boolean allowExpansion() {
    return allowExpansion;
  }

  public void allowExpansion(boolean flag) {
    this.allowExpansion = flag;
  }

  /**
   * Computes depth by traversing the parent pointer to root for every
   * constituent node.
   */
  public void computeDepths() {
    assert depth.length == parent.length;
    for (int c = 0; c < depth.length; c++) {
      int depth = 0;
      int ptr = c;
      while (ptr >= 0) {
        depth++;
        ptr = parent[ptr];
      }
      this.depth[c] = depth;
    }
  }

  public String getWordStr(int tokenIndex) { return alph.word(word[tokenIndex]); }
  public String getPosStr(int tokenIndex) { return alph.pos(pos[tokenIndex]); }

  public int getWord(int tokenIndex) { return word[tokenIndex]; }
  public int getWordNocase(int tokenIndex) { return wordNocase[tokenIndex]; }
  public int getPos(int tokenIndex) { return pos[tokenIndex]; }
  public int getLemma(int tokenIndex) { return lemma[tokenIndex]; }
  public int getWnSynset(int tokenIndex) { return wnSynset[tokenIndex]; }
  public int getBc256(int tokenIndex) { return bc256[tokenIndex]; }
  public int getBc1000(int tokenIndex) { return bc1000[tokenIndex]; }
  public int getShape(int tokenIndex) { return shape[tokenIndex]; }
  public int getNer(int tokenIndex) { return ner[tokenIndex]; }
  public int getSense(int tokenIndex) { return sense[tokenIndex]; }

  public int getConstituentParentIndex(int tokenIndex, ConstituentType consType) {
    return getConsParent(consType)[tokenIndex];
  }
  public Constituent getConstituentParent(int tokenIndex, ConstituentType consType) {
    return this.new Constituent(getConstituentParentIndex(tokenIndex, consType));
  }

  public boolean isRoot(int constitIndex) {
    assert parent[constitIndex] != UNINITIALIZED;
    return parent[constitIndex] == NONE;
  }
  public boolean isLeaf(int constitIndex) {
    assert leftChild[constitIndex] != UNINITIALIZED;
    boolean leaf = leftChild[constitIndex] == NONE;
    if (leaf) assert firstToken[constitIndex] == lastToken[constitIndex];
    return leaf;
  }

  public int getWidth(int constitIndex) {
    return (lastToken[constitIndex] - firstToken[constitIndex]) + 1;
  }
  public int getLhs(int constitIndex) { return lhs[constitIndex]; }
  public int getParent(int constitIndex) { return parent[constitIndex]; }
  public int getLeftChild(int constitIndex) { return leftChild[constitIndex]; }
  public int getRightSib(int constitIndex) { return rightSib[constitIndex]; }
  public int getRightChild(int constitIndex) { return rightChild[constitIndex]; }
  public int getLeftSib(int constitIndex) { return leftSib[constitIndex]; }
  public int getDepth(int constitIndex) { return depth[constitIndex]; }
  public int getFirstToken(int constitIndex) { return firstToken[constitIndex]; }
  public int getLastToken(int constitIndex) { return lastToken[constitIndex]; }

  /**
   * Ensures that there are at least numTokens tokens. Only call this with
   * numTokens > the current number of tokens.
   */
  public void reserveTokens(int numTokens) {
    if (numTokens <= 0)
      throw new IllegalArgumentException();

    if (LOG_ALLOCATIONS)
      Log.info(word.length + " => " + numTokens);
    assert allowExpansion;

    word = copy(word, numTokens, UNINITIALIZED);
    wordNocase = copy(wordNocase, numTokens, UNINITIALIZED);
    pos = copy(pos, numTokens, UNINITIALIZED);
    lemma = copy(lemma, numTokens, UNINITIALIZED);
    wnSynset = copy(wnSynset, numTokens, UNINITIALIZED);
    bc256 = copy(bc256, numTokens, UNINITIALIZED);
    bc1000 = copy(bc1000, numTokens, UNINITIALIZED);
    shape = copy(shape, numTokens, UNINITIALIZED);
    ner = copy(ner, numTokens, UNINITIALIZED);
    sense = copy(sense, numTokens, UNINITIALIZED);

    cons_parent_ptb_gold = copy(cons_parent_ptb_gold, numTokens, UNINITIALIZED);
    cons_parent_ptb_auto = copy(cons_parent_ptb_auto, numTokens, UNINITIALIZED);
    cons_parent_propbank_gold = copy(cons_parent_propbank_gold, numTokens, UNINITIALIZED);
    cons_parent_propbank_auto = copy(cons_parent_propbank_auto, numTokens, UNINITIALIZED);
    cons_parent_ner_gold = copy(cons_parent_ner_gold, numTokens, UNINITIALIZED);
    cons_parent_ner_auto = copy(cons_parent_ner_auto, numTokens, UNINITIALIZED);
  }

  /**
   * Ensures that there are at least numConstituents constituents. If called
   * with 0, constituent fields will be de-allocated.
   */
  public void reserveConstituents(int numConstituents) {
    if (numConstituents < 0)
      throw new IllegalArgumentException();

    if (LOG_ALLOCATIONS)
      Log.info(lhs.length + " => " + numConstituents);
    assert allowExpansion;

    lhs = copy(lhs, numConstituents, UNINITIALIZED);
    parent = copy(parent, numConstituents, UNINITIALIZED);
    leftChild = copy(leftChild, numConstituents, UNINITIALIZED);
    rightSib = copy(rightSib, numConstituents, UNINITIALIZED);
    rightChild = copy(rightChild, numConstituents, UNINITIALIZED);
    leftSib = copy(leftSib, numConstituents, UNINITIALIZED);
    depth = copy(depth, numConstituents, UNINITIALIZED);
    firstToken = copy(firstToken, numConstituents, UNINITIALIZED);
    lastToken = copy(lastToken, numConstituents, UNINITIALIZED);
  }

  public static int[] copy(int[] in, int newLength, int pad) {
    if (newLength == 0)
      return null;
    if (in == null) {
      int[] out = new int[newLength];
      if (pad != 0) Arrays.fill(out, pad);
      return out;
    }
    if (newLength < in.length)
      throw new IllegalArgumentException();
    int off = in.length;
    int[] out = Arrays.copyOf(in, newLength);
    for (int i = off; i < out.length; i++)
      out[i] = pad;
    return out;
  }


  /** Ways to slice up a document */
  public abstract class AbstractSlice implements MultiAlphabet.Showable {

    public abstract int getStart();
    public abstract int getWidth();

    public int[] getWords() {
      int width = getWidth();
      int[] words = new int[width];
      int s = getStart();
      for (int w = 0; w < width; w++)
        words[w] = Document.this.getWord(s + w);
      return words;
    }

    public Token[] getTokens() {
      int width = getWidth();
      Token[] tokens = new Token[width];
      for (int w = 0; w < width; w++)
        tokens[w] = getToken(w);
      return tokens;
    }

    /**
     * Give a RELATIVE index rather than document-wide index.
     *
     * For example, if this slice is from tokens [5,10) and you call getToken(2)
     * you will get the 7th token.
     */
    public Token getToken(int i) {
      return new Token(getStart() + i);
    }

    public String show() {
      return show(alph);
    }

    @Override
    public String show(MultiAlphabet alph) {
      StringBuilder sb = new StringBuilder();
      for (Token t : getTokens()) {
        if (sb.length() > 0)
          sb.append("  ");
        sb.append(t.show(alph));
      }
      return sb.toString();
    }

    public String showWords(MultiAlphabet alph) {
      StringBuilder sb = new StringBuilder();
      for (Token t : getTokens()) {
        if (sb.length() > 0)
          sb.append("  ");
        sb.append(alph.word(t.getWord()));
      }
      return sb.toString();
    }

    public Document getDocument() {
      return Document.this;
    }
  }


  /** Concrete implementation based on int start, width */
  public class Slice extends AbstractSlice {
    private int start, width;
    public Slice(int start, int width) {
      if (width <= 0)
        throw new IllegalArgumentException("width=" + width);
      this.start = start;
      this.width = width;
    }
    public MultiAlphabet getAlphabet() {
      return Document.this.alph;
    }
    @Override
    public int getStart() {
      return start;
    }
    @Override
    public int getWidth() {
      return width;
    }
  }
  /** Slice with [W]idth */
  public Slice sliceW(int start, int width) {
    return this.new Slice(start, width);
  }
  /** Slice with [L]ast */
  public Slice sliceL(int first, int last) {
    int width = (last - first) + 1;
    return this.new Slice(first, width);
  }


  /** Pointer to a token */
  public class Token extends Slice {
    protected int index;
    public Token(int index) {
      super(index, 1);
      if (index == UNINITIALIZED)
        throw new IllegalArgumentException();
      this.index = index;
    }

    /** Use sparingly: Not always obvious which alph sub-section to use. */
    public String getWordStr() { return alph.word(word[index]); }
    public String getPosStr() { return alph.pos(pos[index]); }

    public int getIndex() { return index; }
    public int getWord() { return word[index]; }
    public int getWordNocase() { return wordNocase[index]; }
    public int getPos() { return pos[index]; }
    public int getLemma() { return lemma[index]; }
    public int getWnSynset() { return wnSynset[index]; }
    public int getBc256() { return bc256[index]; }
    public int getBc1000() { return bc1000[index]; }
    public int getShape() { return shape[index]; }
    public int getNer() { return ner[index]; }
    public int getSense() { return sense[index]; }

    public int getConstituentParentIndex(ConstituentType consType) {
      return Document.this.getConstituentParentIndex(index, consType);
    }
    public Constituent getConstituentParent(ConstituentType consType) {
      return Document.this.getConstituentParent(index, consType);
    }

    public void setConstituentParent(int x, ConstituentType consType) {
      Document.this.getConsParent(consType)[index] = x;
    }

    public void setWord(int x) { word[index] = x; }
    public void setWordNocase(int x) { wordNocase[index] = x; }
    public void setPos(int x) { pos[index] = x; }
    public void setLemma(int x) { lemma[index] = x; }
    public void setWnSynset(int x) { wnSynset[index] = x; }
    public void setBc256(int x) { bc256[index] = x; }
    public void setBc1000(int x) { bc1000[index] = x; }
    public void setShape(int x) { shape[index] = x; }
    public void setNer(int x) { ner[index] = x; }
    public void setSense(int x) { sense[index] = x; }

    @Override
    public String show(MultiAlphabet alph) {
      String w = alph.word(getWord());
      String p = alph.pos(getPos());
      return w + "/" + p + " @ " + getIndex();
    }
  }
  public Token getToken(int i) {
    return this.new Token(i);
  }
  public Token newToken() {
    Token t = this.new Token(tokTop++);
    if (allowExpansion && t.getIndex() >= word.length) {
      double rate = 1.6;
      int newSize = (int) (rate * word.length + 1);
      Document.this.reserveTokens(newSize);
    }
    return t;
  }
  public TokenItr newTokenItr() {
    TokenItr t = this.new TokenItr(tokTop++);
    if (allowExpansion && t.getIndex() >= word.length) {
      double rate = 1.6;
      int newSize = (int) (rate * word.length + 1);
      Document.this.reserveTokens(newSize);
    }
    return t;
  }


  /** For using Token like an iterator (be careful!) */
  public class TokenItr extends Token {

    public TokenItr(int index) {
      super(index);
    }

    public boolean isValid() {
      return index >= 0 && index < tokTop;
    }

    public int forwards() {
      int old = index;
      index++;
      return old;
    }

    public int backwards() {
      int old = index;
      index--;
      return old;
    }

    public int gotoToken(int tokenIndex) {
      int old = index;
      this.index = tokenIndex;
      return old;
    }
  }
  public TokenItr getTokenItr(int i) {
    return this.new TokenItr(i);
  }


  /** Pointer to a constituent */
  public class Constituent extends AbstractSlice {
    protected int index;
    // NOTE: When this module is expanded to allow multiple constituency parses,
    // we will need to add a int[] constituents here.
    public Constituent(int index) {
      if (index == UNINITIALIZED)
        throw new IllegalArgumentException();
      this.index = index;
    }
    public int getIndex() {
      return index;
    }
    @Override
    public int getStart() {
      return firstToken[index];
    }
    @Override
    public int getWidth() {
      return (lastToken[index] - firstToken[index]) + 1;
    }
    @Override
    public int hashCode() {
      return Document.this.id.hashCode() ^ index;
    }
    @Override
    public boolean equals(Object other) {
      if (other instanceof Constituent) {
        Constituent c = (Constituent) other;
        return index == c.index && Document.this == c.getDocument();
      }
      return false;
    }
    public String showSubtree(MultiAlphabet alph) {
      ConstituentItr ci = new ConstituentItr(getIndex());

      /*
       * The problem is that not all children may be a mix of terminals and non-terminals!
       * I have a case of:
       * NP -> (ADJP NNP NNP)
       *
       * in my representation, the one and only child of NP is ADJP, and the
       * NNPs are not reified.
       *
       * The problem is that it seems clear that if you have
       * NP -> (NNP NNP)
       * then you don't want to make Constituent nodes for the two children...
       *
       * I don't want to have to process trees by checking both their children
       * as well as their text span!
       *
       * Solution: need to include pre-terminals like NNP if there are any
       * siblings that are not POS tags.
       * The problem with this is that you still don't receive a uniform treatment
       * of POS tags... they could be a part of the tree or they could not be...
       * If I always made them a part of the tree, then I would have a lot of
       * constituents... but maybe I need to do that.
       *
       * => POS tags are the leaves of the tree.
       */

      StringBuilder sb = new StringBuilder();
      sb.append('(');
      sb.append(alph.cfg(ci.getLhs()));
      if (ci.isLeaf()) {
        //sb.append('*');
        for (Token t : ci.getTokens()) {
          sb.append(' ');
          sb.append(alph.word(t.getWord()));
        }
      } else {
        //int nc = 0;
        for (ci.gotoLeftChild(); ci.isValid(); ci.gotoRightSib()) {
          //Log.info("index2=" + ci.getIndex());
          sb.append(' ');
          sb.append(ci.showSubtree(alph));
          //nc++;
        }
        //Log.info(lhs + " has " + nc + " children");
      }
      sb.append(')');
      return sb.toString();
    }

    public boolean isRoot() {
      assert parent[index] != UNINITIALIZED;
      return parent[index] < 0;
    }
    public boolean isLeaf() {
      assert leftChild[index] != UNINITIALIZED;
      return leftChild[index] < 0;
    }

    public int getParent() { return parent[index]; }
    public int getLeftChild() { return leftChild[index]; }
    public int getRightSib() { return rightSib[index]; }
    public int getRightChild() { return rightChild[index]; }
    public int getLeftSib() { return leftSib[index]; }

    public Constituent getParentC() { return new Constituent(parent[index]); }
    public Constituent getLeftChildC() { return new Constituent(leftChild[index]); }
    public Constituent getRightSibC() { return new Constituent(rightSib[index]); }
    public Constituent getRightChildC() { return new Constituent(rightChild[index]); }
    public Constituent getLeftSibC() { return new Constituent(leftSib[index]); }

    public int getLhs() { return lhs[index]; }
    public int getDepth() { return depth[index]; }
    public int getFirstToken() { return firstToken[index]; }
    public int getLastToken() { return lastToken[index]; }

    public void setParent(int x) { parent[index] = x; }
    public void setLeftChild(int x) { leftChild[index] = x; }
    public void setRightSib(int x) { rightSib[index] = x; }
    public void setRightChild(int x) { rightChild[index] = x; }
    public void setLeftSib(int x) { leftSib[index] = x; }

    public void setLhs(int x) { lhs[index] = x; }
    public void setDepth(int x) { depth[index] = x; }
    public void setFirstToken(int x) { firstToken[index] = x; }
    public void setLastToken(int x) { lastToken[index] = x; }

    public void setOnlyChild(int x) {
      leftChild[index] = x;
      rightChild[index] = x;
    }
  }
  public Constituent getConstituent(int c) {
    return this.new Constituent(c);
  }
  public Constituent newConstituent() {
    Constituent c = this.new Constituent(consTop++);
    if (allowExpansion && c.getIndex() >= lhs.length) {
      double rate = 1.6;
      int newSize = (int) (rate * lhs.length + 1);
      Document.this.reserveConstituents(newSize);
    }
    return c;
  }
  public ConstituentItr newConstituentItr() {
    ConstituentItr c = this.new ConstituentItr(consTop++);
    if (allowExpansion && c.getIndex() >= lhs.length) {
      double rate = 1.6;
      int newSize = (int) (rate * lhs.length + 1);
      Document.this.reserveConstituents(newSize);
    }
    return c;
  }


  /** A class for traversing and adding constituents. */
  public class ConstituentItr extends Constituent {

    public ConstituentItr(int index) {
      super(index);
    }

    public boolean isValid() {
      return index >= 0 && index < consTop;
    }

    /** Returns the constituent index before the update is applied */
    public int gotoParent() {
      return gotoConstituent(getParent());
    }

    /** Returns the constituent index before the update is applied */
    public int gotoLeftChild() {
      return gotoConstituent(getLeftChild());
    }

    /** Returns the constituent index before the update is applied */
    public int gotoRightSib() {
      return gotoConstituent(getRightSib());
    }

    /** Returns the constituent index before the update is applied */
    public int gotoRightChild() {
      return gotoConstituent(getRightChild());
    }

    /** Returns the constituent index before the update is applied */
    public int gotoLeftSib() {
      return gotoConstituent(getLeftSib());
    }

    /** Returns the constituent index before the update is applied */
    public int gotoConstituent(int constituentIndex) {
      int old = index;
      index = constituentIndex;
      return old;
    }
  }
  public ConstituentItr getConstituentItr(int c) {
    return this.new ConstituentItr(c);
  }

}
