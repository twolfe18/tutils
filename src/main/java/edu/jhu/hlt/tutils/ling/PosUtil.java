package edu.jhu.hlt.tutils.ling;

import edu.jhu.hlt.tutils.MultiAlphabet;


public class PosUtil {

  public final long VERBS, NOUNS, PROPER_NOUNS;
  private MultiAlphabet alph;

  public PosUtil(MultiAlphabet alph) {
    this.alph = alph;
    VERBS = 0
        | (1l << alph.pos("VA"))      // Chinese
        | (1l << alph.pos("VC"))      // Chinese
        | (1l << alph.pos("VE"))      // Chinese
        | (1l << alph.pos("VV"))      // Chinese
        | (1l << alph.pos("VBD"))
        | (1l << alph.pos("VBG"))
        | (1l << alph.pos("VBN"))
        | (1l << alph.pos("VBP"))
        | (1l << alph.pos("VBZ"));
    // etc
    NOUNS = 0
        | (1l << alph.pos("NR"))      // Chinese
        | (1l << alph.pos("NT"))      // Chinese
        | (1l << alph.pos("NN"))
        | (1l << alph.pos("NNS"))
        | (1l << alph.pos("NNP"))
        | (1l << alph.pos("NNPS"));
    // etc
    PROPER_NOUNS = 0
        | (1l << alph.pos("NR"))      // Chinese
        | (1l << alph.pos("NNP"))
        | (1l << alph.pos("NNPS"));
  }

  public boolean matches(long mask, int pos) {
    assert pos < 64;
    return (mask & (1l << pos)) != 0;
  }

  public boolean isNoun(int pos) {
    return matches(NOUNS, pos);
  }

  public boolean isProperNoun(int pos) {
    return matches(PROPER_NOUNS, pos);
  }

  public boolean isVerb(int pos) {
    return matches(VERBS, pos);
  }

  public int numPosTags() {
    return alph.numPos();
  }
}
