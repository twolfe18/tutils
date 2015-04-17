package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * More or less the CoNLL format in memory, with some other influences which
 * keep things very tabular.
 *
 * @author travis
 */
public final class Document implements Serializable {
  private static final long serialVersionUID = 1L;

  /* GENERAL FIELDS ***********************************************************/
  private final String id;
  private int index;
  private transient MultiAlphabet alph;

  /* TOKEN-INDEXED FIELDS *****************************************************/
  private int[] word;
  private int[] wordNocase;
  private int[] pos;
  private int[] lemma;
  private int[] wnSynset;
  private int[] bc256;
  private int[] bc1000;
  private int[] shape;
  private int[] ner;
  private int[] sense;

  // Dependency parse info
  // NOTE: When this module is expanded to allow multiple dependency parses, we
  // will need to make this a 2-dimensional array where the first index ranges
  // over various parsers. This will require adding a DependencyParse view of
  // a Document which selects one of them.
  private int[] dep_parent;
  private int[] dep_label;

  // Constituency parse info
  // NOTE: Note about multiple dependency parses is true here too: will need to
  // add another index which ranges over parses. Constituent class will add this
  // as another index.
  private int[] cons_parent;

  // First tokens of paragraphs and sentences
  private int[] breaks;

  /* CONSTITUENT-INDEXED FIELDS ***********************************************/
  public int[] lhs;     // left hand side of a CFG rule, e.g. "NP" or "SBAR", NOT a POS tag (that would mean 1 Constituent per word, we want to stay one level higher than that)
  public int[] parent;  // Constituent index, -1 for roots

  public int[] leftChild;   // < 0 for leaf nodes
  public int[] rightSib;

  // Things below this line need not be saved (can be re-derived)
  public int[] rightChild;  // < 0 for leaf nodes
  public int[] leftSib;
  public int[] depth;
  public int[] firstToken;  // Document token index, inclusive
  public int[] lastToken;   // Document token index, inclusive



  // TODO Should the stuff below be folded in with the span/constituent-indexed
  // fields? I.e. have a very rich "type" field which selects constituent/situation/entity
  // e.g.:
  // int[] parent
  // int[] type     cParse      situation/FN      entity/NER    situation/bSRL
  // int[] id       SBAR        Commerce_buy      GPE           lemma/predicate
  // long[] bits    NA          NA                NA            bit-vector
  // TODO Should I just make separate classes for Situation/Entity until I'm sure...
//  /* SITUATION-INDEXED FIELDS *************************************************/
//  public int[] sit_type;        // e.g. FrameNet frame name
//  public int[] sit_parent;      // may be -1 for arguments
//  public int[] sit_role;
//  public int[] sit_tense;
//  public int[] sit_aspect;
//  // SRL bits? Confidence? intensity, polarity?
//  public int[] sit_leftChild;
//  public int[] sit_rightSib;
//  public int[] sit_firstToken;
//  public int[] sit_lastToken;
//
//  /* ENTITY-INDEXED FIELDS ****************************************************/
//  public int[] ent_type;      // e.g. PER/ORG/GPE
//  public int[] ent_parent;    // may be -1
//  // Tree structure:
//  public int[] ent_leftChild;
//  public int[] ent_rightSib;
//  public int[] ent_firstToken;
//  public int[] ent_lastToken;

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
   * Computes depth by looking at parent
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

  /**
   * Populates cons_parent by looking at depth, firstToken, and lastToken
   */
  public void computeConstituentParents() {
    assert depth.length == firstToken.length;
    assert depth.length == lastToken.length;
    int[] usedDepths = new int[depth.length];
    for (int c = 0; c < firstToken.length; c++) {
      int d = depth[c];
      if (d < 0)
        throw new IllegalStateException("have to compute depths first");
      if (d > usedDepths[c]) {
        usedDepths[c] = d;
        if (firstToken[c] < 0 || lastToken[c] < 0)
          throw new IllegalStateException("have to compute firstToken and lastToken first");
        for (int i = firstToken[c]; i <= lastToken[c]; i++)
          cons_parent[i] = c;
      }
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
  public int getConstituentParent(int tokenIndex) { return cons_parent[tokenIndex]; }
  public int getBreak(int tokenIndex) { return breaks[tokenIndex]; }

  public boolean isRoot(int constitIndex) { return parent[constitIndex] < 0; }
  public boolean isLeaf(int constitIndex) { return leftChild[constitIndex] < 0; }
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

  public void reserve(int numTokens, int numConstituents) {
    if (numTokens <= 0)
      throw new IllegalArgumentException();
    if (numConstituents < 0)
      throw new IllegalArgumentException();

    word = copy(word, numTokens, -1);
    wordNocase = copy(wordNocase, numTokens, -1);
    pos = copy(pos, numTokens, -1);
    lemma = copy(lemma, numTokens, -1);
    wnSynset = copy(wnSynset, numTokens, -1);
    bc256 = copy(bc256, numTokens, -1);
    bc1000 = copy(bc1000, numTokens, -1);
    shape = copy(shape, numTokens, -1);
    ner = copy(ner, numTokens, -1);
    sense = copy(sense, numTokens, -1);
    dep_parent = copy(dep_parent, numTokens, -1);
    dep_label = copy(dep_label, numTokens, -1);
    cons_parent = copy(cons_parent, numTokens, -1);
    breaks = copy(breaks, numTokens, -1);

    lhs = copy(lhs, numConstituents, -1);
    parent = copy(parent, numConstituents, -1);
    leftChild = copy(leftChild, numConstituents, -1);
    rightSib = copy(rightSib, numConstituents, -1);
    rightChild = copy(rightChild, numConstituents, -1);
    leftSib = copy(leftSib, numConstituents, -1);
    depth = copy(depth, numConstituents, -1);
    firstToken = copy(firstToken, numConstituents, -1);
    lastToken = copy(lastToken, numConstituents, -1);
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
      int start = getStart();
      int width = getWidth();
      for (int w = 0; w < width; w++)
        tokens.add(getToken(start + w));
      return tokens;
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
    public int getConstituentParent() { return cons_parent[index]; }
    public int getBreak() { return breaks[index]; }
    public boolean startsSentence() {
      return breaks[index] == Sentence.BREAK_LEVEL;
    }
    public boolean startsParagraph() {
      return breaks[index] == Paragraph.BREAK_LEVEL;
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
    public void setConstituentParent(int x) { cons_parent[index] = x; }
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

    public boolean isRoot() { return parent[index] < 0; }
    public boolean isLeaf() { return leftChild[index] < 0; }
    public String getLhsStr() {
      int cfg = lhs[index];
      if (cfg < 0)
        return "???";
      return alph.cfg(cfg);
    }

    public int getLhs() { return lhs[index]; }
    public int getParent() { return parent[index]; }
    public int getLeftChild() { return leftChild[index]; }
    public int getRightSib() { return rightSib[index]; }
    public int getRightChild() { return rightChild[index]; }
    public int getLeftSib() { return leftSib[index]; }
    public int getDepth() { return depth[index]; }
    public int getFirstToken() { return firstToken[index]; }
    public int getLastToken() { return lastToken[index]; }

    public void setLhs(int x) { lhs[index] = x; }
    public void setParent(int x) { parent[index] = x; }
    public void setLeftChild(int x) { leftChild[index] = x; }
    public void setRightSib(int x) { rightSib[index] = x; }
    public void setRightChild(int x) { rightChild[index] = x; }
    public void setLeftSib(int x) { leftSib[index] = x; }
    public void setDepth(int x) { depth[index] = x; }
    public void setFirstToken(int x) { firstToken[index] = x; }
    public void setLastToken(int x) { lastToken[index] = x; }
  }
  public Constituent getConstituent(int c) {
    return this.new Constituent(c);
  }

  public class ConstituentItr extends Constituent {
    public ConstituentItr(int index) {
      super(index);
    }
    public void forwards() { index++; }
    public void backwards() { index--; }
    public boolean isValid() { return index >= 0; }
    public void gotoParent() { index = getParent(); }
    public void gotoLeftChild() { index = getLeftChild(); }
    public void gotoRightSib() { index = getRightSib(); }
    public void gotoRightChild() { index = getRightChild(); }
    public void gotoLeftSib() { index = getLeftSib(); }
    public void gotoConstituent(int constituentIndex) { index = constituentIndex; }
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
}
