package edu.jhu.hlt.tutils.ling;

import java.util.ArrayList;
import java.util.List;

import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.MultiAlphabet;
import edu.jhu.hlt.tutils.TokenToConstituentIndex;

/**
 * A good head finder.
 */
public class DrofnatsHeadFinder implements HeadFinder {

  /*
   * Directions for Rules
   * LEFT vs RIGHT means the direction to scan children in.
   * EAGER means try all values before shifting
   * PATIENT means try all children before trying a new value
   * EXCLUDE is EAGER on the complement of the given values.
   */
  public static final int LEFT_EAGER = 0;
  public static final int LEFT_PATIENT = 1;
  public static final int LEFT_EXCLUDE = 2;
  public static final int RIGHT_EAGER = 3;
  public static final int RIGHT_PATIENT = 4;
  public static final int RIGHT_EXCLUDE = 5;

  public boolean debug = false;

  // Sometimes there is no clear head to parsing errors. For development, it
  // makes sense to throw an Exception, but at some point you need your code
  // to just run.
  public boolean returnHeadAtAnyCost = true;

  public boolean useGoldParse = true;

  /**
   * Specifies a direction to search through a list of POS/CFG tags as well as
   * a set of goal tags, see match function.
   */
  public final class Rule {

    public final int direction;
    public final int[] values;

    public Rule(int direction, int... values) {
      this.direction = direction;
      this.values = values;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      if (direction == LEFT_EAGER) sb.append("LEFT_EAGER");
      if (direction == LEFT_PATIENT) sb.append("LEFT_PATIENT");
      if (direction == LEFT_EXCLUDE) sb.append("LEFT_EXCLUDE");
      if (direction == RIGHT_EAGER) sb.append("RIGHT_EAGER");
      if (direction == RIGHT_PATIENT) sb.append("RIGHT_PATIENT");
      if (direction == RIGHT_EXCLUDE) sb.append("RIGHT_EXCLUDE");
      for (int v : values)
        sb.append(" " + alph.cfg(v));
      return sb.toString();
    }

    /** Sets node to point at the head or an invalid node if none is found */
    public void match(Document.ConstituentItr node) {
      assert node.isValid();
      int c;
      switch (direction) {
        case LEFT_EAGER:
          for (node.gotoLeftChild(); node.isValid(); node.gotoRightSib())
            for (int v : values)
              if (node.getLhs() == v)
                return;
          break;
        case LEFT_PATIENT:
          c = node.getIndex();
          for (int v : values) {
            node.gotoConstituent(c); // Reset to initial config
            for (node.gotoLeftChild(); node.isValid(); node.gotoRightSib())
              if (node.getLhs() == v)
                return;
          }
          node.gotoConstituent(Document.NONE);
          break;
        case LEFT_EXCLUDE:
          outer:
            for (node.gotoLeftChild(); node.isValid(); node.gotoRightChild()) {
              for (int v : values)
                if (node.getLhs() == v)
                  continue outer;
              return;
            }
        break;
        case RIGHT_EAGER:
          for (node.gotoRightChild(); node.isValid(); node.gotoLeftSib()) {
            for (int v : values)
              if (node.getLhs() == v)
                return;
          }
          break;
        case RIGHT_PATIENT:
          c = node.getIndex();
          for (int v : values) {
            node.gotoConstituent(c); // Reset to initial config
            for (node.gotoRightChild(); node.isValid(); node.gotoLeftSib())
              if (node.getLhs() == v)
                return;
          }
          node.gotoConstituent(Document.NONE);
          break;
        case RIGHT_EXCLUDE:
          outer:
            for (node.gotoRightChild(); node.isValid(); node.gotoLeftSib()) {
              for (int v : values)
                if (node.getLhs() == v)
                  continue outer;
              return;
            }
        break;
        default:
          break;
      }
      assert !node.isValid();
    }
  }

  private List<Rule>[] category2rules; // indexed by "lhs" or parent category
  private MultiAlphabet alph;

  @SuppressWarnings("unchecked")
  public DrofnatsHeadFinder(MultiAlphabet alph) {
    this.alph = alph;
    int k = 160; // TODO dynamic
    category2rules = new List[k];
    for (int i = 0; i < k; i++)
      category2rules[i] = new ArrayList<>();
  }

  public DrofnatsHeadFinder useGoldParse(boolean useGoldParse) {
    this.useGoldParse = useGoldParse;
    return this;
  }

  // f=src/edu/stanford/nlp/trees/ModCollinsHeadFinder.java
  // tail -n+55 $f | head -n 87 | perl -pe 's/nonTerminalInfo.put/addRule/' | perl -pe 's/},/);\n    addRule("TODO", /g' | perl -pe 's/new String[^"]+//g' | tr '}{' ' ' | perl -pe 's/"right"/RIGHT_PATIENT/g' | perl -pe 's/"left"/LEFT_PATIENT/g' | perl -pe 's/"rightdis"/RIGHT_EAGER/g' | perl -pe 's/"leftdis"/LEFT_EAGER/g'
  public DrofnatsHeadFinder initBasicRules() {
    // This version from Collins' diss (1999: 236-238)
    // NNS, NN is actually sensible (money, etc.)!
    // QP early isn't; should prefer JJR NN RB
    // remove ADVP; it just shouldn't be there.
    // if two JJ, should take right one (e.g. South Korean)
    // addRule("ADJP", LEFT_PATIENT, "NNS", "NN", "$", "QP");
    addRule("ADJP", RIGHT_PATIENT, "JJ");
    addRule("ADJP", LEFT_PATIENT, "VBN", "VBG", "ADJP", "JJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB");
    addRule("ADJP", LEFT_PATIENT, "$");
    addRule("ADJP", RIGHT_EAGER, "NNS", "NN", "JJ", "QP", "VBN", "VBG");
    addRule("ADJP", LEFT_PATIENT, "ADJP");
    addRule("ADJP", RIGHT_EAGER, "JJP", "JJR", "JJS", "DT", "RB", "RBR", "CD", "IN", "VBD");
    addRule("ADJP", LEFT_PATIENT, "ADVP", "NP");
    addRule("JJP", LEFT_PATIENT, "NNS", "NN", "$", "QP", "JJ", "VBN", "VBG", "ADJP", "JJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"); // JJP is introduced for NML-like adjective phrases in Vadas' treebank; Chris wishes he hadn't used JJP which should be a POS-tag.

    // ADVP rule rewritten by Chris in Nov 2010 to be rightdis. This is right! JJ.* is often head and rightmost.
    addRule("ADVP", LEFT_PATIENT, "ADVP", "IN");
    addRule("ADVP", RIGHT_EAGER, "RB", "RBR", "RBS", "JJ", "JJR", "JJS");
    addRule("ADVP", RIGHT_EAGER, "RP", "DT", "NN", "CD", "NP", "VBN", "NNP", "CC", "FW", "NNS", "ADJP", "NML");
    addRule("CONJP", RIGHT_PATIENT, "CC", "RB", "IN");
    addRule("FRAG", RIGHT_PATIENT); // crap
    addRule("INTJ", LEFT_PATIENT);
    addRule("LST", RIGHT_PATIENT, "LS", ":");

    // NML is head in: (NAC-LOC (NML San Antonio) (, ,) (NNP Texas))
    // TODO: NNP should be head (rare cases, could be ignored):
    //  (NAC (NML New York) (NNP Court) (PP of Appeals))
    //  (NAC (NML Prudential Insurance) (NNP Co.) (PP Of America))
    // Chris: This could maybe still do with more thought, but NAC is rare.
    addRule("NAC", LEFT_PATIENT, "NN", "NNS", "NML", "NNP", "NNPS", "NP", "NAC", "EX", "$", "CD", "QP", "PRP", "VBG", "JJ", "JJS", "JJR", "ADJP", "JJP", "FW");

    // Added JJ to PP head table, since it is a head in several cases, e.g.:
    // (PP (JJ next) (PP to them))
    // When you have both JJ and IN daughters, it is invariably "such as" -- not so clear which should be head, but leave as IN
    // should prefer JJ? (PP (JJ such) (IN as) (NP (NN crocidolite))) Michel thinks we should make JJ a head of PP
    // added SYM as used in new treebanks for symbols filling role of IN
    // Changed PP search to left -- just what you want for conjunction (and consistent with SemanticHeadFinder)
    addRule("PP", RIGHT_PATIENT, "IN", "TO", "VBG", "VBN", "RP", "FW", "JJ", "SYM");
    addRule("PP",  LEFT_PATIENT, "PP");

    addRule("PRN", LEFT_PATIENT, "VP", "NP", "PP", "SQ", "S", "SINV", "SBAR", "ADJP", "JJP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "NNP");
    addRule("PRT", RIGHT_PATIENT, "RP");
    // add '#' for pounds!!
    addRule("QP", LEFT_PATIENT, "$", "IN", "NNS", "NN", "JJ", "CD", "PDT", "DT", "RB", "NCD", "QP", "JJR", "JJS");
    // reduced relative clause can be any predicate VP, ADJP, NP, PP.
    // For choosing between NP and PP, really need to know which one is temporal and to choose the other.
    // It's not clear ADVP needs to be in the list at all (delete?).
    addRule("RRC", LEFT_PATIENT, "RRC");
    addRule("RRC",  RIGHT_PATIENT, "VP", "ADJP", "JJP", "NP", "PP", "ADVP");

    // delete IN -- go for main part of sentence; add FRAG

    addRule("S", LEFT_PATIENT, "TO", "VP", "S", "FRAG", "SBAR", "ADJP", "JJP", "UCP", "NP");
    addRule("SBAR", LEFT_PATIENT, "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT", "S", "SQ", "SINV", "SBAR", "FRAG");
    addRule("SBARQ", LEFT_PATIENT, "SQ", "S", "SINV", "SBARQ", "FRAG", "SBAR");
    // cdm: if you have 2 VP under an SINV, you should really take the 2nd as syntactic head, because the first is a topicalized VP complement of the second, but for now I didn't change this, since it didn't help parsing. (If it were changed, it'd need to be also changed to the opposite in SemanticHeadFinder.)
    addRule("SINV", LEFT_PATIENT, "VBZ", "VBD", "VBP", "VB", "MD", "VBN", "VP", "S", "SINV", "ADJP", "JJP", "NP");
    addRule("SQ", LEFT_PATIENT, "VBZ", "VBD", "VBP", "VB", "MD", "AUX", "AUXG", "VP", "SQ"); // TODO: Should maybe put S before SQ for tag questions. Check.
    addRule("UCP", RIGHT_PATIENT);
    // below is weird!! Make 2 lists, one for good and one for bad heads??
    // VP: added AUX and AUXG to work with Charniak tags
    addRule("VP", LEFT_PATIENT, "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "AUX", "AUXG", "ADJP", "JJP", "NN", "NNS", "JJ", "NP", "NNP");
    addRule("WHADJP", LEFT_PATIENT, "WRB", "WHADVP", "RB", "JJ", "ADJP", "JJP", "JJR");
    addRule("WHADVP", RIGHT_PATIENT, "WRB", "WHADVP");
    addRule("WHNP", LEFT_PATIENT, "WDT", "WP", "WP$", "WHADJP", "WHPP", "WHNP");
    addRule("WHPP", RIGHT_PATIENT, "IN", "TO", "FW");
    addRule("X", RIGHT_PATIENT, "S", "VP", "ADJP", "JJP", "NP", "SBAR", "PP", "X");
    addRule("NP", RIGHT_EAGER, "NN", "NNP", "NNPS", "NNS", "NML", "NX", "POS", "JJR");
    addRule("NP", LEFT_PATIENT, "NP", "PRP");
    addRule("NP", RIGHT_EAGER, "$", "ADJP", "JJP", "PRN", "FW");
    addRule("NP", RIGHT_PATIENT, "CD");
    addRule("NP", RIGHT_EAGER, "JJ", "JJS", "RB", "QP", "DT", "WDT", "RBR", "ADVP");

    copyRule("NX", "NP");
    // TODO: seems JJ should be head of NML in this case:
    // (NP (NML (JJ former) (NML Red Sox) (JJ great)) (NNP Luis) (NNP Tiant)),
    // (although JJ great is tagged wrong)
    copyRule("NML", "NP");

    addRule("POSSP", RIGHT_PATIENT, "POS");

    /* HJT: Adding the following to deal with oddly formed data in (for example) the Brown corpus */
    addRule("ROOT", LEFT_PATIENT, "S", "SQ", "SINV", "SBAR", "FRAG");
    // Just to handle trees which have TOP instead of ROOT at the root
    copyRule("TOP", "ROOT");
    addRule("TYPO", LEFT_PATIENT, "NN", "NP", "NML", "NNP", "NNPS", "TO",
        "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "ADJP", "JJP", "FRAG"); // for Brown (Roger)
    addRule("ADV", RIGHT_PATIENT, "RB", "RBR", "RBS", "FW",
        "ADVP", "TO", "CD", "JJR", "JJ", "IN", "NP", "NML", "JJS", "NN");

    // SWBD
    addRule("EDITED", LEFT_PATIENT); // crap rule for Switchboard (if don't delete EDITED nodes)
    // in sw2756, a "VB". (copy "VP" to handle this problem, though should really fix it on reading)
    addRule("VB", LEFT_PATIENT, "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "AUX", "AUXG", "ADJP", "JJP", "NN", "NNS", "JJ", "NP", "NNP");

    addRule("META", LEFT_PATIENT); // rule for OntoNotes, but maybe should just be deleted in TreeReader??
    addRule("XS", RIGHT_PATIENT, "IN"); // rule for new structure in QP, introduced by Stanford in QPTreeTransformer
    // addRule(null, LEFT_PATIENT); // rule for OntoNotes from Michel, but it would be better to fix this in TreeReader or to use a default rule?

    return this;
  }

  // f=src/edu/stanford/nlp/trees/SemanticHeadFinder.java
  // tail -n+132 $f | head -n 45 | perl -pe 's/nonTerminalInfo.put/addRule/' | perl -pe 's/},/);\n  addRule("TODO", /g' | perl -pe 's/new String[^"]+//g' | tr '}{' ' ' | perl -pe 's/"right"/RIGHT_PATIENT/g' | perl -pe 's/"left"/LEFT_PATIENT/g' | perl -pe 's/"rightdis"/RIGHT_EAGER/g' | perl -pe 's/"leftdis"/LEFT_EAGER/g'
  public DrofnatsHeadFinder initSemanticHeadRules() {
    // NP: don't want a POS to be the head
    // verbs are here so that POS isn't favored in the case of bad parses
    clearRules("NP");
    addRule("NP", RIGHT_EAGER, "NN", "NNP", "NNPS", "NNS", "NX", "NML", "JJR", "WP" );
    addRule("NP", LEFT_PATIENT, "NP", "PRP");
    addRule("NP", RIGHT_EAGER, "$", "ADJP", "FW");
    addRule("NP", RIGHT_PATIENT, "CD");
    addRule("NP", RIGHT_EAGER, "JJ", "JJS", "QP", "DT", "WDT", "NML", "PRN", "RB", "RBR", "ADVP");
    addRule("NP", RIGHT_EAGER, "VP", "VB", "VBZ", "VBD", "VBP");
    addRule("NP", LEFT_PATIENT, "POS");

    copyRules("NX", "NP");
    copyRules("NML", "NP");

    // WHNP clauses should have the same sort of head as an NP
    // but it a WHNP has a NP and a WHNP under it, the WHNP should be the head. E.g., (WHNP (WHNP (WP$ whose) (JJ chief) (JJ executive) (NN officer))(, ,) (NP (NNP James) (NNP Gatward))(, ,))
    clearRules("WHNP");
    addRule("WHNP", RIGHT_EAGER, "NN", "NNP", "NNPS", "NNS", "NX", "NML", "JJR", "WP");
    addRule("WHNP", LEFT_PATIENT, "WHNP", "NP");
    addRule("WHNP", RIGHT_EAGER, "$", "ADJP", "PRN", "FW");
    addRule("WHNP", RIGHT_PATIENT, "CD");
    addRule("WHNP", RIGHT_EAGER, "JJ", "JJS", "RB", "QP");
    addRule("WHNP", LEFT_PATIENT, "WHPP", "WHADJP", "WP$", "WDT");

    //WHADJP
    clearRules("WHADJP");
    addRule("WHADJP", LEFT_PATIENT, "ADJP", "JJ", "JJR", "WP");
    addRule("WHADJP", RIGHT_PATIENT, "RB");
    addRule("WHADJP", RIGHT_PATIENT);

    //WHADJP
    clearRules("WHADVP");
    addRule("WHADVP", RIGHT_EAGER, "WRB", "WHADVP", "RB", "JJ"); // if not WRB or WHADVP, probably has flat NP structure, allow JJ for "how long" constructions

    // QP: we don't want the first CD to be the semantic head (e.g., "three billion": head should be "billion"), so we go from right to left
    clearRules("QP");
    addRule("QP", RIGHT_PATIENT, "$", "NNS", "NN", "CD", "JJ", "PDT", "DT", "IN", "RB", "NCD", "QP", "JJR", "JJS");

    // S, SBAR and SQ clauses should prefer the main verb as the head
    // S: "He considered him a friend" -> we want a friend to be the head
    clearRules("S");
    addRule("S", LEFT_PATIENT, "VP", "S", "FRAG", "SBAR", "ADJP", "UCP", "TO");
    addRule("S", RIGHT_PATIENT, "NP");

    clearRules("SBAR");
    addRule("SBAR", LEFT_PATIENT, "S", "SQ", "SINV", "SBAR", "FRAG", "VP", "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT");
    // VP shouldn't be needed in SBAR, but occurs in one buggy tree in PTB3 wsj_1457 and otherwise does no harm

    clearRules("SQ");
    addRule("SQ", LEFT_PATIENT, "VP", "SQ", "ADJP", "VB", "VBZ", "VBD", "VBP", "MD", "AUX", "AUXG");

    // UCP take the first element as head
    clearRules("UCP");
    addRule("UCP", LEFT_PATIENT);

    // CONJP: we want different heads for "but also" and "but not" and we don't want "not" to be the head in "not to mention"; now make "mention" head of "not to mention"
    clearRules("CONJP");
    addRule("CONJP", RIGHT_PATIENT, "CC", "VB", "JJ", "RB", "IN" );

    // FRAG: crap rule needs to be change if you want to parse
    // glosses; but it is correct to have ADJP and ADVP before S
    // because of weird parses of reduced sentences.
    clearRules("FRAG");
    addRule("FRAG", LEFT_PATIENT, "IN");
    addRule("FRAG", RIGHT_PATIENT, "RB");
    addRule("FRAG", LEFT_PATIENT, "NP");
    addRule("FRAG", LEFT_PATIENT, "ADJP", "ADVP", "FRAG", "S", "SBAR", "VP");

    // PRN: sentence first
    clearRules("PRN");
    addRule("PRN", LEFT_PATIENT, "VP", "SQ", "S", "SINV", "SBAR", "NP", "ADJP", "PP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "NNP");

    // add the constituent XS (special node to add a layer in a QP tree introduced in our QPTreeTransformer)
    clearRules("XS");
    addRule("XS", RIGHT_PATIENT, "IN");

    // add a rule to deal with the CoNLL data
    clearRules("EMBED");
    addRule("EMBED", RIGHT_PATIENT, "INTJ");

    extras();
    return this;
  }

  private void extras() {
    // ADJP => much/RB @ 1 better/RBR @ 2 looking/VBG @ 3
    addRule("ADJP", RIGHT_EAGER, "VBG");

    // TOP => [FRAG] :: A/DT @ 0 much/RB @ 1 better/RBR @ 2 looking/VBG @ 3 News/NNP @ 4 Night/NNP @ 5 I/PRP @ 6 might/MD @ 7 add/VB @ 8 as/IN @ 9 Paula/NNP @ 10 Zahn/NNP @ 11 sits/VBZ @ 12 in/RP @ 13 for/IN @ 14 Anderson/NNP @ 15 and/CC @ 16 Aaron/NNP @ 17 /./. @ 18
    addRule("TOP", RIGHT_EAGER, "FRAG");

    // PP => [RRC] :: for/IN @ 14 Anderson/NNP @ 15 and/CC @ 16 Aaron/NNP @ 17
    addRule("PP", RIGHT_EAGER, "RRC");

    /*
(NP
 (S
  (VP
   (VBG trying)
   (S
    (VP
     (TO to)
     (VP
      (VP
       (VB pick)
       (NP
  (NNS women))
       (PRT
  (RP up)))
      (CC and)
      (VP
       (VB take)
       (NP
  (PRP them))
       (PP
  (IN from)
  (NP
   (DT the)
   (NN bar)))))))))
 (CC and)
 (SBAR
  (WHNP
   (WDT whatever))
  (S
   (NP
    (PRP he))
   (VP
    (VBZ 's)
    (VP
     (VBG doing)
     (PP
      (IN with)
      (NP
       (PRP them)))
     (PP
      (IN after)
      (NP
       (DT that))))))))
     */
    addRule("NP", LEFT_EAGER, "S");
  }

  @Override
  public int head(Document d, int first, int last) {
    if (first < 0 || last < 0 || first > last)
      throw new IllegalArgumentException("first=" + first + " last=" + last);

    if (debug) {
      String ms = d.sliceL(first, last).show(alph);
      Log.info("finding head for " + ms);
    }

    if (first == last)
      return first;
    assert first < last;

    // Get the constituent immediately dominating this token
    TokenToConstituentIndex parse = d.getT2cPtb(useGoldParse);
    int consIdx = parse.getParent(last);
    if (consIdx < 0)
      throw new RuntimeException("no node dominating token " + last);

    // Find the smallest constituent that spans the entire mention
    Document.ConstituentItr root = d.getConstituentItr(consIdx);
    assert root.getLastToken() >= 0 : "first/last token not set?";
    if (debug)
      Log.info("root=" + root);
    int biggestConsThatFitsInsideSpan = -1;
    while (root.getFirstToken() > first) {
      if (first <= root.getFirstToken() && root.getLastToken() <= last)
        biggestConsThatFitsInsideSpan = root.getIndex();
      if (debug)
        Log.info("going up from: " + root.show(alph));
      root.gotoParent();
    }
    if (debug)
      Log.info("finding head for " + root.showSubtree(alph));

    // Search downward to find the head
    int originalRoot = root.getIndex();
    int head = -1;
    while (root != null) {
      if (debug)
        Log.info("inspecting: " + root.show(alph));
      if (root.isLeaf() || root.getWidth() == 1) {
        if (debug) Log.info("leaf => returning only token");
        assert root.getFirstToken() == root.getLastToken();
        head = root.getFirstToken();
        break;
      }
      if (!head(root))
        break;
    }

    if (debug)
      Log.info("tentative head=" + head);

    // TODO A slightly better solution to this would be to "do surgery" on the
    // parse: create a new constituent to stand in for problematic constituent
    // (the smallest constituent which covers the span but has a head outside)
    // with the same category and assign each of the children of the problematic
    // constituent to be children of the new constituent.
    // E.g. (NP [[ (PRP$ his) (NN wife) ]] (CC and) (NNS kids))
    // => (NP' (PRP$ his) (NN wife))

    // The previous search may yield a head outside of the mention, in which
    // case we should try fall back on the biggest constituent that fit 
    if (first <= head && head <= last) {
      return head;
    } else {
      if (debug)
        Log.info("found head outside of the given span: " + head);
      if (originalRoot == biggestConsThatFitsInsideSpan) {
        // Nothing we can do to recover, would just repeat the same search as above
        if (returnHeadAtAnyCost) {
          String ms = d.sliceL(first, last).show(alph);
          Log.warn("could not derive head for " + ms);
          return last;
        }
      } else {
        // Start at the biggest constituent and go downwards
        root.gotoConstituent(biggestConsThatFitsInsideSpan);
        while (root != null) {
          if (debug)
            Log.info("inspecting: " + root.show(alph));
          if (root.isLeaf() || root.getWidth() == 1) {
            if (debug) Log.info("leaf => returning only token");
            assert root.getFirstToken() == root.getLastToken();
            head = root.getFirstToken();
            break;
          }
          if (!head(root))
            break;
        }
      }
    }

    if (returnHeadAtAnyCost) {
      String ms = d.sliceL(first, last).show(alph);
      Log.warn("could not derive head for " + ms);
      return last;
    }

    throw new RuntimeException("couldn't find head");
  }

  /** Returns false if a rules is not applied */
  public boolean head(Document.ConstituentItr cons) {
    if (!cons.isValid())
      throw new IllegalArgumentException();
    int initC = cons.getIndex();
    List<Rule> relevant = category2rules[cons.getLhs()];
    for (Rule r : relevant) {
      if (debug) {
        String lhs = alph.cfg(cons.getLhs());
        Log.info("trying " + lhs + " rule: " + r);
      }
      r.match(cons);
      if (cons.isValid()) {
        if (debug) Log.info("match!");
        return true;
      }
      cons.gotoConstituent(initC);
    }
    String subtree = cons.showSubtree(alph);
    //throw new RuntimeException("no head rule for " + subtree);
    Log.warn("no head rule for " + subtree);
    return false;
  }

  public void clearRules() {
    for (List<Rule> r : category2rules)
      r.clear();
  }

  public void clearRules(int lhs) {
    category2rules[lhs].clear();
  }

  public void clearRules(String lhs) {
    clearRules(alph.cfg(lhs));
  }

  public void copyRule(String to, String from) {
    copyRule(alph.cfg(to), alph.cfg(from));
  }

  public void copyRule(int to, int from) {
    category2rules[to].clear();
    category2rules[to].addAll(category2rules[from]);
  }

  public void copyRules(String to, String from) {
    copyRule(alph.cfg(to), alph.cfg(from));
  }

  public void addRule(int lhs, int direction, int... values) {
    if (lhs >= category2rules.length) {
      throw new RuntimeException("there should not be large values of LHS="
          + lhs + ", did you call alph.cfg when you shouldn't have?");
    }
    category2rules[lhs].add(new Rule(direction, values));
  }

  public void addRule(String lhs, int direction, String... values) {
    int lhsi = alph.cfg(lhs);
    int[] valuesi = new int[values.length];
    for (int i = 0; i < values.length; i++)
      valuesi[i] = alph.cfg(values[i]);
    addRule(lhsi, direction, valuesi);
  }
}
