package edu.jhu.hlt.tutils;

import java.util.Arrays;

import edu.jhu.hlt.tutils.Document.ConstituentItr;

public class TokenToConstituentIndex {

  private int firstCons;
  private int[] tok2cons;
  private Document doc;

  public TokenToConstituentIndex(Document doc) {
    this(doc, Document.NONE);
  }

  public TokenToConstituentIndex(Document doc, int firstCons) {
    this.firstCons = firstCons;
    this.doc = doc;
    this.tok2cons = new int[doc.tokTop];
    Arrays.fill(this.tok2cons, Document.NONE);
    if (firstCons >= 0) {
      for (ConstituentItr c = doc.getConstituentItr(firstCons);
          c.isValid(); c.gotoRightSib()) {
        addConstituent(c.getIndex());
      }
    }
  }

  /**
   * Recursively add a token->constituent mapping for all leaf constituents
   * under c.
   */
  public void addConstituent(int c) {
    int lc = doc.getLeftChild(c);
    if (lc == Document.NONE) {
      // Leaf, add mappings
      int f = doc.getFirstToken(c);
      int l = doc.getLastToken(c);
      if (f < 0 || l < 0 || f > l) {
        throw new RuntimeException("malformed constituent,"
            + " index=" + c + " firstToken=" + f + " lastToken=" + l);
      }
      for (int i = f; i <= l; i++) {
        if (tok2cons[i] != Document.NONE) {
          throw new RuntimeException("constituent " + c + " overlaps with"
              + " constituent " + tok2cons[i] + " at token " + i);
        }
        tok2cons[i] = c;
      }
    } else {
      // Internal, recurse
      for (ConstituentItr ci = doc.getConstituentItr(lc); ci.isValid(); ci.gotoRightSib())
        addConstituent(ci.getIndex());
    }
  }

  /**
   * @return the index of the first constituent in the provided linked list of
   * constituents which are indexed by this instance (if there was one; if you
   * just called addConstituent, then will return Document.NONE).
   */
  public int getFirstConstituent() {
    return firstCons;
  }

  public Document getDocument() {
    return doc;
  }

  /**
   * @return the index of a constituent that immediately dominates tokenIndex.
   */
  public int getParent(int tokenIndex) {
    if (tokenIndex >= tok2cons.length || tokenIndex < 0) {
      throw new IllegalArgumentException(
          "tokenIndex=" + tokenIndex + " tok2cons.length=" + tok2cons.length);
    }
    return tok2cons[tokenIndex];
  }
}
