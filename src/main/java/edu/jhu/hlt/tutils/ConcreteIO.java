package edu.jhu.hlt.tutils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Parse;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.TokenTagging;
import edu.jhu.hlt.concrete.Tokenization;
import edu.jhu.hlt.concrete.UUID;
import edu.mit.jwi.IRAMDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWordID;

/**
 * Ingests Concrete Communications into edu.jhu.hlt.tutils.Document and sets
 * things like Brown clusters and WordNet synsets along the way.
 *
 * @author travis
 */
public class ConcreteIO {

  public boolean debug = false;
  public boolean debug_cons = false;

  private String posToolName =
      edu.jhu.hlt.concrete.ingest.Conll2011.META_POS.getTool();
  private String nerToolName =
      edu.jhu.hlt.concrete.ingest.Conll2011.META_NER.getTool();
  private String consParseToolName =
      edu.jhu.hlt.concrete.ingest.Conll2011.META_PARSE.getTool();

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

  public void setTools(String posToolName, String nerToolName, String consParseToolName) {
    this.posToolName = posToolName;
    this.nerToolName = nerToolName;
    this.consParseToolName = consParseToolName;
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

  /**
   * Concrete child indices are sentence-relative but Document.Constituent's are
   * document-relative. tokenOffset says how many tokens have come before this
   * parse/sentence to allow conversion.
   */
  public void addConstituents(
      Parse p,
      int tokenOffset,
      Document.ConstituentItr cons,
      MultiAlphabet alph) {
    // Build the constituents and mapping between them
    int nCons = p.getConstituentListSize();
    int[] ccon2dcon = new int[nCons];
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
      cons.forwards();
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
    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {
        Tokenization tkz = ss.getTokenization();
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
        addConstituents(p, tokenOffset, constituent, alph);
      }
    }

    // Compute some derived values in Document
    doc.computeDepths();
    doc.computeConstituentParents(Document.ConstituentType.PTB_GOLD);
    if (bc256 != null || bc1000 != null)
      BrownClusters.setClusters(doc, bc256, bc1000);

    return doc;
  }
}
