package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * More or less the CoNLL format in memory, with some other influences which
 * keep things very tabular.
 *
 * TODO figure out a scheme by which we can de-allocate some of these token
 * indexed fields (e.g. checking if all the values are -2, then set to null).
 *
 * TODO Add dependency graphs for collapsed dependency representations.
 * @see edu.jhu.hlt.tutils.LabeledDirectedGraph
 *
 * @author travis
 */
public final class Document implements Serializable {
  private static final long serialVersionUID = 1L;
  public static final int NONE = -1;
  public static final int UNINITIALIZED = -2;

  /* GENERAL FIELDS ***********************************************************/
  final String id;
  int index;
  private transient MultiAlphabet alph;

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
  LabeledDirectedGraph stanfordDepsCollapsed;
  LabeledDirectedGraph stanfordDepsCollapsedCC;

  // See http://universaldependencies.github.io/docs/u/overview/syntax.html
  LabeledDirectedGraph universalDependencies;

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

  // Dependency parse info
  // NOTE: When this module is expanded to allow multiple dependency parses, we
  // will need to make this a 2-dimensional array where the first index ranges
  // over various parsers. This will require adding a DependencyParse view of
  // a Document which selects one of them.
  // TODO Crap: Stanford col and colcc dependencies are only guaranteed to be
  // directed graphs, which can'be be represented this way...
  int[] dep_parent;   // value is a token index
  int[] dep_label;    // value is an edge/label type

  // Constituency parse info: value is constituent index of the deepest
  // constituent which dominates this token.
  // NOTE: Note about multiple dependency parses is true here too: will need to
  // add another index which ranges over parses. Constituent class will add this
  // as another index.
//  int[] cons_parent;

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


  // AHHHH!!!
  // breaks is not necessary and clumsy!
  // Use constituents instead!
  // space used by breaks = sizeof(int) * #tokens
  // space used by constituents = sizeof(int) * 9 * #sentences
  //      (with reconstruction) = sizeof(int) * 5 * #sentences
  // TODO once this is setup, then I can get rid of the parent stuff
  public int cons_sentences = NONE;  // index of constituent corresponding to the first sentence
  public int cons_paragraph = NONE;
  public int cons_section = NONE;
  public int cons_ptb_gold = NONE;
  public int cons_ptb_auto = NONE;
  public int cons_propbank_gold = NONE;
  public int cons_propbank_auto = NONE;
  public int cons_ner_gold = NONE;
  public int cons_ner_auto = NONE;

  // First tokens of paragraphs and sentences
  int[] breaks;

  /* CONSTITUENT-INDEXED FIELDS ***********************************************/
  // NEW: type is implicit from whether you got this constituent from
  // cons_parent_ptb_auto vs cons_parent_propbank_gold, etc.
//  int[] cons_type;    // Used to say things like what parser generated this.
  // TODO maybe rename this to "tag"
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


  // TODO The working plan for {@link Situation}s and {@link Entity}s is to put
  // them in as constituency trees. See the description of how Propbank is
  // encoded for an example of how this can work. The only issue that may come
  // up is how to fit all of the needed information into `lhs`. Maybe adding
  // a field (int or long) would solve the problem. If we need a lot more bits
  // than that, then I may have to give up on the simplicity of uniform
  // constituency trees.


  /* END OF FIELDS ************************************************************/

  public Document(String id, int index, MultiAlphabet alph) {
    this.id = id;
    this.index = index;
    this.alph = alph;
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
    return word.length;
  }

  public int numConstituents() {
    return lhs.length;
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
  public int getDepParent(int tokenIndex) { return dep_parent[tokenIndex]; }
  public int getDepLabel(int tokenIndex) { return dep_label[tokenIndex]; }
  public int getBreak(int tokenIndex) { return breaks[tokenIndex]; }

  public int getConstituentParentIndex(int tokenIndex, ConstituentType consType) {
    return getConsParent(consType)[tokenIndex];
  }
  public Constituent getConstituentParent(int tokenIndex, ConstituentType consType) {
    return this.new Constituent(getConstituentParentIndex(tokenIndex, consType));
  }

  public boolean isRoot(int constitIndex) {
    assert parent[constitIndex] != UNINITIALIZED;
    return parent[constitIndex] < 0;
  }
  public boolean isLeaf(int constitIndex) {
    assert leftChild[constitIndex] != UNINITIALIZED;
    return leftChild[constitIndex] < 0;
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
    dep_parent = copy(dep_parent, numTokens, UNINITIALIZED);
    dep_label = copy(dep_label, numTokens, UNINITIALIZED);
    breaks = copy(breaks, numTokens, UNINITIALIZED);

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

  // Ways to slice up a document
  public abstract class AbstractSlice implements MultiAlphabet.Showable {

    public abstract int getStart();
    public abstract int getWidth();

    public List<Token> getTokens() {
      List<Token> tokens = new ArrayList<>();
      int width = getWidth();
      for (int w = 0; w < width; w++)
        tokens.add(getToken(w));
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
  public class Slice extends AbstractSlice {
    private int start, width;
    public Slice(int start, int width) {
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
  public Slice slice(int start, int width) {
    return this.new Slice(start, width);
  }

  // Pointer to a token
  public class Token extends Slice {
    protected int index;
    public Token(int index) {
      super(index, 1);
      if (index == UNINITIALIZED)
        throw new IllegalArgumentException();
      this.index = index;
    }

    /** Use sparingly */
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
    public int getDepParent() { return dep_parent[index]; }
    public int getDepLabel() { return dep_label[index]; }
    public int getBreak() { return breaks[index]; }

    public int getConstituentParentIndex(ConstituentType consType) {
      return Document.this.getConstituentParentIndex(index, consType);
    }
    public Constituent getConstituentParent(ConstituentType consType) {
      return Document.this.getConstituentParent(index, consType);
    }

    public boolean startsSentence() {
      return breaks[index] >= Sentence.BREAK_LEVEL;
    }
    public boolean startsParagraph() {
      return breaks[index] >= Paragraph.BREAK_LEVEL;
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
    public void setDepParent(int x) { dep_parent[index] = x; }
    public void setDepLabel(int x) { dep_label[index] = x; }

    public void setBreak(int x) { breaks[index] = x; }
    public void setBreakSafe(int x) {
      if (x > breaks[index])
        breaks[index] = x;
    }
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

  // For using Token like an iterator (be careful!)
  public class TokenItr extends Token {
    private int sentence = -2;
    private int paragraph = -2;

    public TokenItr(int index) {
      super(index);
    }

    public boolean isValid() {
      return index < word.length;
    }

    public void forwards() {
      index++;
      update(1);
    }
    public void backwards() {
      index--;
      update(-1);
    }

    public void setIndex(int tokenIndex) {
      this.index = tokenIndex;
      this.sentence = -2;
      this.paragraph = -2;
    }

    private void update(int delta) {
      if (!isValid())
        return;
      if (sentence >= -1 && startsSentence())
        sentence += delta;
      if (paragraph >= -1 && startsParagraph())
        paragraph += delta;
    }
    private void updateFromStart() {
      int idx = index;
      index = -1;
      sentence = -1;
      paragraph = -1;
      while (index < idx) {
        index++;
        update(1);
      }
    }

    /**
     * Will return n-1 where n is the number of sentence breaks seen between the
     * start of the document and this index, inclusive. This means -1 if there
     * are no sentence breaks, 0 if this is the first sentence, etc.
     */
    public int getSentence() {
      if (sentence < -1)
        updateFromStart();
      return sentence;
    }

    /**
     * Will return n-1 where n is the number of paragraph breaks seen between the
     * start of the document and this index, inclusive. This means -1 if there
     * are no paragraph breaks, 0 if this is the first paragraph, etc.
     */
    public int getParagraph() {
      if (paragraph < -1)
        updateFromStart();
      return paragraph;
    }
  }
  public TokenItr getTokenItr(int i) {
    return this.new TokenItr(i);
  }

  // Pointer to a constituent
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

    public String showSubtree(MultiAlphabet alph) {
      ConstituentItr ci = new ConstituentItr(getIndex());

      //String lhs = alph.cfg(ci.getLhs());
      //Log.info("ci=" + lhs);

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

    // NOTE: If `lhs` may be used for more than one thing: then don't allow
    // this type of method!
//    public String getLhsStr() {
//      int cfg = lhs[index];
//      if (cfg < 0)
//        return "???";
//      return alph.cfg(cfg);
//    }

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

  /**
   * A class for traversing and adding constituents.
   */
  public class ConstituentItr extends Constituent {
    // Whether the forwards method should allow constituent fields to be re-allocated
    private boolean allowExpansion;

    public ConstituentItr(int index) {
      super(index);
      allowExpansion = false;
    }

    public boolean allowExpansion() {
      return allowExpansion;
    }

    public void allowExpansion(boolean flag) {
      this.allowExpansion = flag;
    }

    /** Returns the constituent index before the update is applied */
    public int forwards() {
      int old = index;
      index++;
      if (allowExpansion && index >= lhs.length) {
        double rate = 1.6;
        int newSize = (int) (rate * lhs.length + 1);
        Document.this.reserveConstituents(newSize);
      }
      return old;
    }

    /** Returns the constituent before the update is applied */
    public Constituent forwardsC() {
      return Document.this.getConstituent(forwards());
    }

    /** Returns the constituent index before the update is applied */
    public int backwards() {
      int old = index;
      index--;
      return old;
    }

    /** Returns the constituent before the update is applied */
    public Constituent backwardsC() {
      return Document.this.getConstituent(backwards());
    }

    public boolean isValid() { return index >= 0; }

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

  public class Sentence extends Slice {
    public static final int BREAK_LEVEL = 1;
    public final int breakLevel = BREAK_LEVEL;
    public Sentence(int start, int length) {
      super(start, length);
    }
  }

  public class Paragraph extends Slice {
    public static final int BREAK_LEVEL = 2;
    public final int breakLevel = BREAK_LEVEL;
    public Paragraph(int start, int length) {
      super(start, length);
    }
  }

  public Iterator<Sentence> getSentences() {
    return this.new SentenceItr();
  }
  public class SentenceItr implements Iterator<Sentence> {
    // Index into breaks corresponding to the start of a sentence.
    // If there are no remaining sentences it will be -1.
    private int next = 0;
    @Override
    public boolean hasNext() {
      return next >= 0;
    }
    @Override
    public Sentence next() {
      int r = next;
      int length = 0;
      for (int i = next + 1; i < breaks.length; i++) {
        length++;
        if (breaks[i] >= Sentence.BREAK_LEVEL) {
          next = i;
          break;
        }
      }
      return Document.this.new Sentence(r, length);
    }
  }
}
