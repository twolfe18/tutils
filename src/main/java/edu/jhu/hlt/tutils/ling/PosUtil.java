package edu.jhu.hlt.tutils.ling;

import java.io.Serializable;
import java.util.BitSet;

import edu.jhu.hlt.tutils.MultiAlphabet;


public class PosUtil implements Serializable {
  private static final long serialVersionUID = -5172427447025542500L;

  public final BitSet verbs, nouns, properNouns, pronouns;
  private MultiAlphabet alph;

  public PosUtil(MultiAlphabet alph) {
    this.alph = alph;
    this.verbs = new BitSet();
    verbs.set(alph.pos("VA"));
    verbs.set(alph.pos("VC"));
    verbs.set(alph.pos("VE"));
    verbs.set(alph.pos("VV"));
    verbs.set(alph.pos("VBD"));
    verbs.set(alph.pos("VBG"));
    verbs.set(alph.pos("VBN"));
    verbs.set(alph.pos("VBP"));
    verbs.set(alph.pos("VBZ"));
    this.nouns = new BitSet();
    nouns.set(alph.pos("NR"));
    nouns.set(alph.pos("NT"));
    nouns.set(alph.pos("NN"));
    nouns.set(alph.pos("NNS"));
    nouns.set(alph.pos("NNP"));
    nouns.set(alph.pos("NNPS"));
    this.properNouns = new BitSet();
    properNouns.set(alph.pos("NR"));
    properNouns.set(alph.pos("NNP"));
    properNouns.set(alph.pos("NNPS"));
    this.pronouns = new BitSet();
    pronouns.set(alph.pos("PN"));
    pronouns.set(alph.pos("WP"));
    pronouns.set(alph.pos("WP$"));
    pronouns.set(alph.pos("PRP"));
    pronouns.set(alph.pos("PRP$"));
  }

  public boolean isNoun(int pos) {
    return nouns.get(pos);
  }

  public boolean isProperNoun(int pos) {
    return properNouns.get(pos);
  }

  public boolean isProNoun(int pos) {
    return pronouns.get(pos);
  }

  public boolean isVerb(int pos) {
    return verbs.get(pos);
  }

  public MultiAlphabet getAlphabet() {
    return alph;
  }
}
