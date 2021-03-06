package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import edu.jhu.hlt.tutils.ling.Language;

/**
 * More or less the CoNLL format in memory, with some other influences which
 * keep things very tabular.
 *
 * NOTE: These data structures are a bit vague on their own. To get more
 * documentation, you should consult the code that populates them,
 * e.g. {@link ConcreteToDocument}.
 *
 * NOTE: There is a reason that all of the constituency trees are packed into
 * one container. You could imagine having a similar setup (with
 * siblings/parent/children/label/etc) but where each type of tree (e.g.
 * cons_ptb_gold and cons_propbank_auto) are stored in separate arrays. This
 * could even have the additional benefit of being able to free certain columns
 * on a per-parse basis, thus saving memory. The REASON not to do this is that
 * by having them all in one (indexing) "universe", i.e. data structure, they can
 * reference each other. You can have Situation constituents which have children
 * which are PTB nodes or Entity nodes, etc.
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
 * TODO We can get rid of leftSib/rigthSib by placing constraints on the
 * ordering used to store Constituents. Namely: siblings can be laid out next to
 * each other in the arrays that house them. The rightSibling is always at
 * index+1 and the left sibling is always at index-1. You can check if you are
 * the first or last sibling by inspecting the two parents (if they are the same
 * then they're siblings). Loops over siblings used to read rightSib/leftSib and
 * possibly jump around in those arrays, now those loops will only use parent
 * and don't even need to store rightSib/leftSib.
 * THERE is a DOWNSIDE to this approach: it makes building trees harder. You
 * have to build in a depth-first fashion. As such, I think it is best to add
 * this way of storing nodes as an optional data transform. A user would build
 * a tree however they like (likely with rightSib/leftSib) and then call a
 * "packSiblings" method. This method would need to operate over all Constituent
 * trees. After it ran, siblings would be adjacent and the rightSib/leftSib
 * arrays would be freed.
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
  public Language language;
  public final String id;
  public int index;
  private transient MultiAlphabet alph;

  // Whether the forwards method should allow constituent fields to be re-allocated
  private boolean allowExpansion = true;

  /* MEMORY BOOK-KEEPING ******************************************************/
  int tokTop = 0;     // index of first un-used token
  int consTop = 0;    // index of first un-used constituent


  /* TOKEN-INDEXED FIELDS *****************************************************/
  int[] word;
  int[] wordNocase;
  int[] posG;         // gold
  int[] posH;         // hypothesis
  int[] nerG;         // gold
  int[] nerH;         // hypothesis
  int[] lemma;
  int[] wnSynset;
  int[] bc256;
  int[] bc1000;
  int[] shape;
  int[] sense;


  /* CONSTITUENT-INDEXED FIELDS ***********************************************/
  // TODO change "lhs" to "label"
  int[] lhs;          // used for many purposes, see each tree type below for documentation.
  int[] leftChild;    // < 0 for leaf nodes
  int[] rightSib;
  int[] firstToken;   // Document token index, inclusive
  int[] lastToken;    // Document token index, inclusive

  // Things below this line need not be saved (can be re-derived)
  int[] parent;       // Constituent index, -1 for roots
  int[] rightChild;   // < 0 for leaf nodes
  int[] leftSib;
  int[] depth;


  /* LINKED LISTS OF CONSTITUENTS *********************************************/

  // index of constituent corresponding to the first sentence
  public int cons_sentences = NONE;     // lhs = sentence index
  public int cons_paragraph = NONE;     // lhs = paragraph index
  public int cons_section = NONE;       // lhs = section index

  // top level is linked list of cparses for sentences
  // NOTE, these trees do not contain words and their leaves have lhs=POS tag
  public int cons_ptb_gold = NONE;    // e.g. CoNLL/PTB gold parse
  public int cons_ptb_auto = NONE;    // e.g. Stanford cparse

  // top level is linked list of items like (SituationMention ... (Target/predicate lhs=frameType ...) (Argument lhs=roleType ...)*)
  // See above for note about handling Propbank continuation roles, etc.
  public int cons_propbank_gold = NONE;
  public int cons_propbank_auto = NONE;
  
  // Same format as cons_propbank_*, but this holds some arbitrary SituationMention-shaped annotations
  public int cons_situationMentions_gold = NONE;
  public int cons_situationMentions_auto = NONE;

  // top level is linked list of (NER lhs=type firstToken=i lastToken=j)
  public int cons_ner_gold = NONE;
  public int cons_ner_auto = NONE;
  // TODO NER is currently tokens... make this into cons

  // Linked list (use rightSib) of mentions for coref
  public int cons_coref_mention_gold = NONE;
  public int cons_coref_mention_auto = NONE;

  // Depth 2 tree (top level is entities, second level is mentions)
  // Entity level: lhs=Entity.type
  // Mention level: lhs=Entity.type
  public int cons_coref_gold = NONE;
  public int cons_coref_auto = NONE;


  /* TOKEN TO CONSTITUENT MAPPINGS ********************************************/
  // Constituents have spans (firstToken,lastToken), and sometimes you want to
  // go from a token to a constituent dominated by it. These build an index off
  // of the data in constituents and let you do that.

  // Add more as needed

  transient TokenToConstituentIndex t2c_ptb_gold;
  public TokenToConstituentIndex getT2cPtbGold() {
    if (t2c_ptb_gold == null)
      t2c_ptb_gold = new TokenToConstituentIndex(this, cons_ptb_gold);
    return t2c_ptb_gold;
  }

  transient TokenToConstituentIndex t2c_ptb_auto;
  public TokenToConstituentIndex getT2cPtbAuto() {
    if (t2c_ptb_auto == null)
      t2c_ptb_auto = new TokenToConstituentIndex(this, cons_ptb_auto);
    return t2c_ptb_auto;
  }

  public TokenToConstituentIndex getT2cPtb(boolean gold) {
    return gold ? getT2cPtbGold() : getT2cPtbAuto();
  }

  transient TokenToConstituentIndex t2c_sentence;
  public TokenToConstituentIndex getT2cSentence() {
    assert cons_sentences >= 0;
    if (t2c_sentence == null)
      t2c_sentence = new TokenToConstituentIndex(this, cons_sentences);
    return t2c_sentence;
  }

  /* GRAPHS *******************************************************************/
  
  // These graphs all have nodes which correspond to token indices (0-indexed).
  // There are no edges connecting to a ROOT node.

  // See http://nlp.stanford.edu/software/dependencies_manual.pdf
  public LabeledDirectedGraph stanfordDepsBasic;
  public LabeledDirectedGraph stanfordDepsCollapsed;
  public LabeledDirectedGraph stanfordDepsCollapsedCC;

  // See http://universaldependencies.github.io/docs/u/overview/syntax.html
  public LabeledDirectedGraph universalDependencies;

  public LabeledDirectedGraph parseyMcParseFace;

  /* CONVENIENCE METHODS ******************************************************/

  private transient int beforeDoc = -1, afterDoc = -1;
  public int beforeDoc() {
    if (beforeDoc < 0)
      beforeDoc = alph.word("</S>");
    return beforeDoc;
  }
  public int afterDoc() {
    if (afterDoc < 0)
      afterDoc = alph.word("<S>");
    return afterDoc;
  }

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

  /**
   * @param needle is (first,last) tokens to match
   * @param area is the area to search
   * @param d
   * @param addTo may be null, otherwise will be populated by (first,last) values found in area
   * @param ignoreCase says if wordNoCase should be use, else word
   * @return
   */
  public int stringOccurrences(IntPair needle, IntPair area, List<IntPair> addTo, boolean ignoreCase) {
    if (needle.first > needle.second || needle.first < 0)
      throw new IllegalArgumentException();
    if (area.first > area.second || area.first < 0)
      throw new IllegalArgumentException();
    int occ = 0;
    int w = needle.second - needle.first;
    search:
    for (int i = area.first; i <= area.second - w; i++) {
      int matchingToks = 0;
      while (matchingToks < w) {
        int a = needle.first + matchingToks;
        int b = i + matchingToks;
        if (ignoreCase && getWordNocase(a) == getWordNocase(b)) {
          assert getWordNocase(a) >= 0;
          matchingToks++;
        } else if (!ignoreCase && getWord(a) == getWord(b)) {
          assert getWord(a) >= 0;
          matchingToks++;
        } else {
          continue search;
        }
      }
      occ++;
      if (addTo != null)
        addTo.add(new IntPair(i, i + w - 1));
    }
    return occ;
  }

  public int posOccurrences(int i, IntPair area, List<Integer> addTo, boolean gold) {
    int occ = 0;
    int p = gold ? getPosG(i) : getPosH(i);
    for (int j = area.first; j <= area.second; j++) {
      if ((gold && getPosG(j) == p)
          || (!gold && getPosH(j) == p)) {
        occ++;
        if (addTo != null)
          addTo.add(j);
      }
    }
    return occ;
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
    assert consTop <= parent.length;
    for (int c = 0; c < consTop; c++) {
      int depth = 0;
      int ptr = c;
      while (ptr >= 0) {
        depth++;
        ptr = parent[ptr];
        if (depth > this.depth.length) {
          System.out.println("loop:");
          BitSet bs = new BitSet();
          while (true) {
            System.out.println(ptr);
            if (bs.get(ptr))
              break;
            bs.set(ptr);
            ptr = parent[ptr];
          }
          throw new RuntimeException("you have a loop in your constituency "
              + "graph above leaf node " + c + "!");
        }
      }
      this.depth[c] = depth;
    }
  }

  public String getWordStr(int tokenIndex) { return alph.word(word[tokenIndex]); }

  public int getWord(int tokenIndex) { return word[tokenIndex]; }
  public int getWordNocase(int tokenIndex) { return wordNocase[tokenIndex]; }
  public int getPosG(int tokenIndex) { return posG[tokenIndex]; }
  public int getPosH(int tokenIndex) { return posH[tokenIndex]; }
  public int getLemma(int tokenIndex) { return lemma[tokenIndex]; }
  public int getWnSynset(int tokenIndex) { return wnSynset[tokenIndex]; }
  public int getBc256(int tokenIndex) { return bc256[tokenIndex]; }
  public int getBc1000(int tokenIndex) { return bc1000[tokenIndex]; }
  public int getShape(int tokenIndex) { return shape[tokenIndex]; }
  public int getNerG(int tokenIndex) { return nerG[tokenIndex]; }
  public int getNerH(int tokenIndex) { return nerH[tokenIndex]; }
  public int getSense(int tokenIndex) { return sense[tokenIndex]; }

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

    if (LOG_ALLOCATIONS) {
      int l = word == null ? 0 : word.length;
      Log.info(l + " => " + numTokens);
    }
    assert allowExpansion;

    word = copy(word, numTokens, UNINITIALIZED);
    wordNocase = copy(wordNocase, numTokens, UNINITIALIZED);
    posG = copy(posG, numTokens, UNINITIALIZED);
    posH = copy(posH, numTokens, UNINITIALIZED);
    nerG = copy(nerG, numTokens, UNINITIALIZED);
    nerH = copy(nerH, numTokens, UNINITIALIZED);
    lemma = copy(lemma, numTokens, UNINITIALIZED);
    wnSynset = copy(wnSynset, numTokens, UNINITIALIZED);
    bc256 = copy(bc256, numTokens, UNINITIALIZED);
    bc1000 = copy(bc1000, numTokens, UNINITIALIZED);
    shape = copy(shape, numTokens, UNINITIALIZED);
    sense = copy(sense, numTokens, UNINITIALIZED);
  }

  /**
   * Ensures that there are at least numConstituents constituents. If called
   * with 0, constituent fields will be de-allocated.
   */
  public void reserveConstituents(int numConstituents) {
    if (numConstituents < 0)
      throw new IllegalArgumentException();

    if (LOG_ALLOCATIONS) {
      int l = lhs == null ? 0 : lhs.length;
      Log.info(l + " => " + numConstituents);
    }
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

    /** Returns [first,last], both inclusive */
    public IntPair getBoundaries() {
      int f = getStart();
      int l = (f + getWidth()) - 1;
      return new IntPair(f, l);
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
  public class Token extends Slice implements Serializable {
    private static final long serialVersionUID = -5938185870054285983L;
    protected int index;
    protected boolean goldView;   // true => posG, nerG  false => posH, nerH

    public Token(int index) {
      this(index, false);
    }

    public Token(int index, boolean goldView) {
      super(index, 1);
      if (index == UNINITIALIZED)
        throw new IllegalArgumentException();
      this.index = index;
      this.goldView = goldView;
    }

    @Override
    public String toString() {
      return "(Token " + index
          + " word=" + getWord()
          + " posG=" + getPosG()
          + " posH=" + getPosH()
          + ")";
    }

    /** Use sparingly: Not always obvious which alph sub-section to use. */
    public String getWordStr() { return alph.word(word[index]); }

    public int getIndex() { return index; }
    public int getWord() { return word[index]; }
    public int getWordNocase() { return wordNocase[index]; }
    public int getPos() { return goldView ? posG[index] : posH[index]; }
    public int getPosG() { return posG[index]; }
    public int getPosH() { return posH[index]; }
    public int getNer() { return goldView ? nerG[index] : nerH[index]; }
    public int getNerG() { return nerG[index]; }
    public int getNerH() { return nerH[index]; }
    public int getLemma() { return lemma[index]; }
    public int getWnSynset() { return wnSynset[index]; }
    public int getBc256() { return bc256[index]; }
    public int getBc1000() { return bc1000[index]; }
    public int getShape() { return shape[index]; }
    public int getSense() { return sense[index]; }

    public void setWord(int x) { word[index] = x; }
    public void setWordNocase(int x) { wordNocase[index] = x; }
    public void setPosG(int x) { posG[index] = x; }
    public void setPosH(int x) { posH[index] = x; }
    public void setLemma(int x) { lemma[index] = x; }
    public void setWnSynset(int x) { wnSynset[index] = x; }
    public void setBc256(int x) { bc256[index] = x; }
    public void setBc1000(int x) { bc1000[index] = x; }
    public void setShape(int x) { shape[index] = x; }
    public void setNerG(int x) { nerG[index] = x; }
    public void setNerH(int x) { nerH[index] = x; }
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
    private static final long serialVersionUID = 1097136833554339301L;

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
  public class Constituent extends AbstractSlice implements Serializable {
    private static final long serialVersionUID = -9031713161057564855L;
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

    @Override
    public String toString() {
      return "(Constituent " + index
          + " lhs=" + getLhs()
          + " parent=" + getParent()
          + " depth=" + getDepth()
          + " firstToken=" + getFirstToken()
          + " lastToken=" + getLastToken()
          + " leftSib=" + getLeftSib()
          + " rightSib=" + getRightSib()
          + " leftChild=" + getLeftChild()
          + " rightChild=" + getRightChild()
          + ")";
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

    public ConstituentItr getParentCI() { return new ConstituentItr(parent[index]); }
    public ConstituentItr getLeftChildCI() { return new ConstituentItr(leftChild[index]); }
    public ConstituentItr getRightSibCI() { return new ConstituentItr(rightSib[index]); }
    public ConstituentItr getRightChildCI() { return new ConstituentItr(rightChild[index]); }
    public ConstituentItr getLeftSibCI() { return new ConstituentItr(leftSib[index]); }

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
    private static final long serialVersionUID = 3477578265931422237L;

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
