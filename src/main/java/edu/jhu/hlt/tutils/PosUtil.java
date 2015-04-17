package edu.jhu.hlt.tutils;


public class PosUtil {

  public final long VERBS, NOUNS;
  private MultiAlphabet alph;

  public PosUtil(MultiAlphabet alph) {
    this.alph = alph;
    VERBS = 0
        | (1l << alph.pos("VBD"))
        | (1l << alph.pos("VBG"))
        | (1l << alph.pos("VBN"))
        | (1l << alph.pos("VBP"))
        | (1l << alph.pos("VBZ"));
    // etc
    NOUNS = 0
        | (1l << alph.pos("NN"))
        | (1l << alph.pos("NNS"))
        | (1l << alph.pos("NNP"))
        | (1l << alph.pos("NNPS"));
    // etc
  }

  public boolean matches(long mask, int pos) {
    assert pos < 64;
    return (mask & (1l << pos)) != 0;
  }

  public boolean isNoun(int pos) {
    return matches(NOUNS, pos);
  }

  public boolean isVerb(int pos) {
    return matches(VERBS, pos);
  }

  public int numPosTags() {
    return alph.numPos();
  }
}
