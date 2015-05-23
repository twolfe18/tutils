package edu.jhu.hlt.tutils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.ConstituentRef;
import edu.jhu.hlt.concrete.MentionArgument;
import edu.jhu.hlt.concrete.Parse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.SituationMention;
import edu.jhu.hlt.concrete.SituationMentionSet;
import edu.jhu.hlt.concrete.TokenRefSequence;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.tutils.Document.ConstituentItr;
import edu.mit.jwi.IRAMDictionary;
import edu.mit.jwi.RAMDictionary;
import edu.mit.jwi.data.ILoadPolicy;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWordID;

/**
 * Ingests {@link Communication} into {@link Document}. You can specify tools
 * by name which you would like to ingest as well as extras like whether to
 * lookup/compute/add things like Brown clusters and WordNet synsets.
 *
 * @author travis
 */
public class ConcreteIO {

  public boolean debug = false;
  public boolean debug_cons = false;

  /** Only supports a single POS {@link TokenTagging} for now */
  private String posToolName =
      edu.jhu.hlt.concrete.ingest.Conll2011.META_POS.getTool();

  /** Only supports {@link TokenTagging} style NER for now */
  private String nerToolName =
      edu.jhu.hlt.concrete.ingest.Conll2011.META_NER.getTool();

  /** Only supports a single {@link Parse} for now */
  private String consParseToolName =
      edu.jhu.hlt.concrete.ingest.Conll2011.META_PARSE.getTool();

  /**
   * Assumes Propbank SRL is stored as a {@link SituationMention} using
   * {@link ConstituentRef}. See the documentation in {@link Document} for how
   * they are added to the {@link Document}.
   */
  private String propbankSrlToolName = null;

  private BrownClusters bc256, bc1000;
  private IRAMDictionary wnDict;

  /**
   * If you provide null for any of these args it will work but just not set
   * these fields
   */
  public ConcreteIO(BrownClusters bc256, BrownClusters bc1000, IRAMDictionary wordNet) {
    this.bc256 = bc256;
    this.bc1000 = bc1000;
    this.wnDict = wordNet;
  }

  // TODO Split these into separate methods
  public void setTools(String posToolName, String nerToolName, String consParseToolName) {
    this.posToolName = posToolName;
    this.nerToolName = nerToolName;
    this.consParseToolName = consParseToolName;
  }

  public void setPropbankToolname(String toolName) {
    this.propbankSrlToolName = toolName;
  }

  /** Returns a Token with word, pos, and ner initialized */
  public void setToken(
      Document.Token token,
      Communication c,
      Tokenization t,
      TokenTagging pos,
      TokenTagging ner,
      int tokenOffset,
      int tokenIdx,
      MultiAlphabet alph) {
    edu.jhu.hlt.concrete.Token ct = t.getTokenList().getTokenList().get(tokenIdx);
    assert ct.isSetText();
    String w = ct.getText();
    token.setWord(alph.word(w));
    token.setPos(alph.pos(pos.getTaggedTokenList().get(tokenIdx).getTag()));
    token.setNer(alph.ner(ner.getTaggedTokenList().get(tokenIdx).getTag()));
    token.setWordNocase(alph.word(w.toLowerCase()));
    token.setShape(alph.shape(WordShape.wordShape(w)));

    // WordNet synset id
    if (wnDict != null) {
      token.setWnSynset(alph.wnSynset(MultiAlphabet.UNKNOWN));
      edu.mit.jwi.item.POS wnPos = WordNetPosUtil.ptb2wordNet(token.getPosStr());
      if (wnPos != null) {
        String wd = token.getWordStr();
        IIndexWord wnWord = wnDict.getIndexWord(wd, wnPos);
        if (wnWord != null) {
          IWordID wnWordId = wnWord.getWordIDs().get(0);
          ISynsetID ssid = wnWordId.getSynsetID();
          int ss = alph.wnSynset(ssid.toString());
          token.setWnSynset(ss);
        }
      }
    }
  }

  /**
   * Concrete child indices are sentence-relative but Document.Constituent's are
   * document-relative. tokenOffset says how many tokens have come before this
   * parse/sentence to allow conversion.
   */
  public void addConstituents(
      Parse p,
      int tokenOffset,
      Document.ConstituentItr cons,
      Map<ConstituentRef, Integer> constituentIndices,
      MultiAlphabet alph) {

    // Build the constituents and mapping between them
    int nCons = p.getConstituentListSize();
    int[] ccon2dcon = new int[nCons];
    int pl = 0; // index into parse.constituentList
    for (edu.jhu.hlt.concrete.Constituent ccon : p.getConstituentList()) {
      if (debug_cons)
        Log.info("ccon=" + ccon);
      cons.setLhs(alph.cfg(ccon.getTag()));
      assert ccon.isSetStart() == ccon.isSetEnding();
      if (ccon.isSetStart()) {
        if (debug_cons)
          Log.info("setting lastToken[" + cons.getIndex() + "]=" + (tokenOffset + ccon.getEnding() - 1));
        cons.setFirstToken(tokenOffset + ccon.getStart());
        cons.setLastToken(tokenOffset + ccon.getEnding() - 1);
      }
      ccon2dcon[ccon.getId()] = cons.getIndex();

      // Store this Constituents index
      int i = cons.forwards();
      ConstituentRef cr = new ConstituentRef(p.getUuid(), pl++);
      Integer old = constituentIndices.put(cr, i);
      if (old != null) throw new RuntimeException();
    }

    // Set the children and sibling links
    Document d = cons.getDocument();
    for (edu.jhu.hlt.concrete.Constituent ccon : p.getConstituentList()) {
      int parentIdx = ccon2dcon[ccon.getId()];
      Document.Constituent parent = d.getConstituent(parentIdx);
      int prevChildIdx = -1;
      for (int child : ccon.getChildList()) {
        int childIdx = ccon2dcon[p.getConstituentList().get(child).getId()];
        Document.Constituent c = d.getConstituent(childIdx);
        c.setParent(parentIdx);
        c.setLeftSib(prevChildIdx);
        if (prevChildIdx >= 0)
          d.getConstituent(prevChildIdx).setRightSib(childIdx);
        if (parent.getLeftChild() < 0)
          parent.setLeftChild(childIdx);
        parent.setRightChild(childIdx);
        prevChildIdx = childIdx;
      }
      if (prevChildIdx >= 0)
        d.getConstituent(prevChildIdx).setRightSib(-1);
    }
  }

  public Document communication2Document(
      Communication c, int docIndex, MultiAlphabet alph) {

    // Count the number of tokens and constituents
    int numToks = 0, numCons = 0;
    Map<UUID, Integer> tokenizationUUID_to_tokenOffset = new HashMap<>();
    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {

        Tokenization tkz = ss.getTokenization();
        Integer i = tokenizationUUID_to_tokenOffset.put(tkz.getUuid(), numToks);
        if (i != null) throw new RuntimeException();

        Parse p = findByTool(tkz.getParseList(), consParseToolName);
        numToks += tkz.getTokenList().getTokenListSize();
        numCons += p.getConstituentListSize();
      }
    }

    // Build the Document
    Document doc = new Document(c.getId(), docIndex, alph);
    doc.reserve(numToks, numCons);
    Document.TokenItr token = doc.getTokenItr(0);
    Document.ConstituentItr constituent = doc.getConstituentItr(0);
    Map<ConstituentRef, Integer> constituentIndices = new HashMap<>();
    for (Section s : c.getSectionList()) {
      token.setBreakSafe(Document.Paragraph.BREAK_LEVEL);
      for (Sentence ss : s.getSentenceList()) {
        token.setBreakSafe(Document.Sentence.BREAK_LEVEL);

        int tokenOffset = token.getIndex();
        if (debug) Log.info("tokenOffset=" + tokenOffset);
        Tokenization tkz = ss.getTokenization();
        TokenTagging pos = findByTool(tkz.getTokenTaggingList(), posToolName);
        TokenTagging ner = findByTool(tkz.getTokenTaggingList(), nerToolName);

        int n = tkz.getTokenList().getTokenListSize();
        for (int i = 0; i < n; i++) {
          setToken(token, c, tkz, pos, ner, tokenOffset, i, alph);
          token.forwards();
        }

        Parse p = findByTool(tkz.getParseList(), consParseToolName);
        addConstituents(p, tokenOffset, constituent, constituentIndices, alph);
      }
    }

    // Add Propbank SRL
    if (this.propbankSrlToolName != null) {
      SituationMentionSet sms = findByTool(c.getSituationMentionSetList(), propbankSrlToolName);
      if (sms == null) {
        System.err.println("failed to find Propbank SRL: " + propbankSrlToolName);
      } else {
        for (SituationMention sm : sms.getMentionList()) {

          // Set the predicate
          setupConstituent(sm.getConstituent(), sm.getTokens(), constituent,
              constituentIndices, tokenizationUUID_to_tokenOffset, doc);
          int pred = alph.srl(sm.getSituationKind());
          constituent.setLhs(pred);
          constituent.setLeftSib(-1);
          constituent.setRightSib(-1);
          constituent.setParent(-1);
          int predConsIndex = constituent.forwards();

          // Set the arguments
          int prevArgConsIdx = -1;
          int numArgs = sm.getArgumentListSize();
          for (int ai = 0; ai < numArgs; ai++) {
            MentionArgument arg = sm.getArgumentList().get(ai);

            // Set first/last token and left/right child
            // Make sure this comes before other operations.
            setupConstituent(arg.getConstituent(), arg.getTokens(), constituent,
                constituentIndices, tokenizationUUID_to_tokenOffset, doc);
            int role = alph.srl(arg.getRole());
            constituent.setLhs(role);

            // Set parent
            constituent.setParent(predConsIndex);

            // Set left/right child (of the predicate)
            if (ai == 0) {
              int cur = constituent.gotoConstituent(predConsIndex);
              constituent.setLeftChild(cur);
              constituent.gotoConstituent(cur);
            } else if (ai == numArgs - 1) {
              int cur = constituent.gotoConstituent(predConsIndex);
              constituent.setRightChild(cur);
              constituent.gotoConstituent(cur);
            }

            // Set left/right sibling
            constituent.setLeftSib(prevArgConsIdx);
            if (ai > 0) {
              int cur = constituent.gotoConstituent(prevArgConsIdx);
              constituent.setRightSib(cur);
              constituent.gotoConstituent(cur);
            } else if (ai == numArgs - 1) {
              constituent.setRightSib(-1);
            }

            prevArgConsIdx = constituent.forwards();
          }
        }
      }
    }

    // Compute some derived values in Document
    doc.computeDepths();
    doc.computeConstituentParents(Document.ConstituentType.PTB_GOLD);
    if (bc256 != null || bc1000 != null)
      BrownClusters.setClusters(doc, bc256, bc1000);

    return doc;
  }

  /**
   * Given a {@link ConstituentRef} and a {@link TokenRefSequence}, each of
   * which may be null, set the left/right children fields as well as the
   * first/last token fields for the {@link ConstituentItr}. If the
   * {@link ConstituentRef} is null, this will insert a dummy constituent
   * corresponding the to {@link TokenRefSequence} and advance the
   * {@link ConstituentItr}, meaning that anything that fields that were set
   * before calling this method will be lost.
   */
  private static void setupConstituent(
      ConstituentRef cr,
      TokenRefSequence trs,
      ConstituentItr constituent,
      Map<ConstituentRef, Integer> constituentIndices,
      Map<UUID, Integer> tokenizationUUID_to_tokenOffset,
      Document doc) {
    if (cr != null) {
      int i = constituentIndices.get(cr);
      constituent.setOnlyChild(i);
    } else {
      if (trs == null)
        throw new IllegalArgumentException();
      int tokenOffset = tokenizationUUID_to_tokenOffset.get(trs.getTokenizationId());
      int s = tokenOffset + min(trs.getTokenIndexList());
      int e = tokenOffset + max(trs.getTokenIndexList());
      // Insert dummy constituent/span?
      throw new RuntimeException("implement me");
    }
    assert constituent.getLeftChild() == constituent.getRightChild();
    constituent.setFirstToken(doc.getFirstToken(constituent.getLeftChild()));
    constituent.setLastToken(doc.getLastToken(constituent.getRightChild()));
  }

  public static int min(List<Integer> numbers) {
    int min = numbers.get(0);
    for (int i : numbers)
      if (i < min) min = i;
    return min;
  }

  public static int max(List<Integer> numbers) {
    int max = numbers.get(0);
    for (int i : numbers)
      if (i > max) max = i;
    return max;
  }

  public static ConcreteIO makeInstance() {
    File bcParent = new File("/home/travis/code/fnparse/data/embeddings");
    BrownClusters bc256 = new BrownClusters(BrownClusters.bc256dir(bcParent));
    BrownClusters bc1000 = new BrownClusters(BrownClusters.bc1000dir(bcParent));
    File wnDictDir = new File("data/wordnet/dict/");
    IRAMDictionary wnDict = new RAMDictionary(wnDictDir, ILoadPolicy.IMMEDIATE_LOAD);
    try {
      wnDict.open();
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
    ConcreteIO io = new ConcreteIO(bc256, bc1000, wnDict);
    return io;
  }

  public static <T> T findByTool(List<T> items, String toolname) {
    T match = null;
    for (T t : items) {
      try {
        Method m = t.getClass().getMethod("getMetadata");
        AnnotationMetadata am = (AnnotationMetadata) m.invoke(t);
        if (toolname.equals(am.getTool())) {
          assert match == null;
          match = t;
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    assert match != null;
    return match;
  }

  public static <T> Map<UUID, T> indexByUUID(List<T> items) {
    Map<UUID, T> map = new HashMap<>();
    for (T t : items) {
      try {
        Method m = t.getClass().getMethod("getUuid");
        if (m == null)
          m = t.getClass().getMethod("getId");
        UUID id = (UUID) m.invoke(t);
        T old = map.put(id, t);
        assert old == null;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return map;
  }

  public static <T> List<T> join(List<UUID> keys, Map<UUID, T> values) {
    List<T> l = new ArrayList<>();
    for (UUID id : keys) {
      T v = values.get(id);
      assert v != null;
      l.add(v);
    }
    return l;
  }

}
