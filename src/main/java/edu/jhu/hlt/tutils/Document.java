package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class Document implements Serializable {
  private static final long serialVersionUID = 1L;

  public static enum TokenIndexed {
    WORD,
    WORD_NOCASE,
    POS,
    LEMMA,
    SHAPE,
    NER,
    SENSE,

    // Dependency parse info
    DEP_PARENT,
    DEP_LABEL,

    // Constituency parse info
    CONS_PARENT,

    // First tokens of paragraphs and sentences
    BREAKS,
  }

  public static enum ConstituentIndexed {
    LHS,
    PARENT,
    LEFT_CHILD,
    RIGHT_SIBLING,

    // Things below this line need not be saved (can be re-derived)
    RIGHT_CHILD,
    LEFT_SIBLING,
    DEPTH,
    FIRST_TOKEN,
    LAST_TOKEN,
  }

  private final String id;
  private MultiAlphabet alph;

  // TODO maybe make these a single index?
  private int[][] tokenIndexed;
  private int[][] constituentIndexed;

  public Document(String id, MultiAlphabet alph) {
    this.id = id;
    this.alph = alph;
  }

  public String getId() {
    return id;
  }

  public MultiAlphabet getAlphabet() {
    return alph;
  }

  public void reserve(int numTokens, int numConstituents) {
    if (numTokens <= 0)
      throw new IllegalArgumentException();
    if (numConstituents < 0)
      throw new IllegalArgumentException();
    tokenIndexed = new int[TokenIndexed.values().length][numTokens];
    constituentIndexed = new int[ConstituentIndexed.values().length][numConstituents];
  }

  public int getWord(int index) {
    return getTokenIndexed(TokenIndexed.WORD.ordinal(), index);
  }
  public int getPos(int index) {
    return getTokenIndexed(TokenIndexed.POS.ordinal(), index);
  }
  public int getNer(int index) {
    return getTokenIndexed(TokenIndexed.NER.ordinal(), index);
  }

  // can be used to switch between row major, column major, or even 1-dimensional
  protected int getTokenIndexed(int field, int index) {
    return tokenIndexed[field][index];
  }

  // can be used to switch between row major, column major, or even 1-dimensional
  protected int getConstituentIndexed(int field, int index) {
    return constituentIndexed[field][index];
  }

  // Ways to slice up a document
  public abstract class Slice {
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
    public Token getTokenRelative(int relativeIndex) {
      assert relativeIndex < getWidth();
      return getToken(getStart() + relativeIndex);
    }
  }

  // Pointer to a token
  public final class Token extends Slice {
    private final int index;
    public Token(int index) {
      this.index = index;
    }
    public int getWord() {
      return getTokenIndexed(TokenIndexed.WORD.ordinal(), index);
    }
    // TODO write the remaining
    public int getIndex() {
      return index;
    }
    @Override
    public int getStart() {
      return index;
    }
    @Override
    public int getWidth() {
      return 1;
    }
  }
  public Token getToken(int i) {
    return this.new Token(i);
  }

  // Pointer to a constituent
  public final class Constituent extends Slice {
    private final int index;
    public Constituent(int index) {
      this.index = index;
    }
    public int getIndex() {
      return index;
    }
    @Override
    public int getStart() {
      throw new RuntimeException("implement me");
    }
    @Override
    public int getWidth() {
      throw new RuntimeException("implement me");
    }
  }
  public Constituent getConstituent(int c) {
    return this.new Constituent(c);
  }

  public class Sentence extends Slice {
    public static final int BREAK_LEVEL = 1;
    public final int breakLevel = BREAK_LEVEL;
    public final int start, width;
    public Sentence(int start, int length) {
      this.start = start;
      this.width = length;
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

  public class Paragraph extends Slice {
    public static final int BREAK_LEVEL = 2;
    public final int breakLevel = BREAK_LEVEL;
    public final int start, width;
    public Paragraph(int start, int length) {
      this.start = start;
      this.width = length;
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
}
