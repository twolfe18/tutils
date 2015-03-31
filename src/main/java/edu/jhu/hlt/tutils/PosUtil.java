package edu.jhu.hlt.tutils;


public class PosUtil {

  public final long VERBS, NOUNS;

  public PosUtil(MultiAlphabet alph) {
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
}
