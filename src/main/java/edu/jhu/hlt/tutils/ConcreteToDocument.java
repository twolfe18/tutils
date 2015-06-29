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
import edu.jhu.hlt.concrete.Entity;
import edu.jhu.hlt.concrete.EntityMention;
import edu.jhu.hlt.concrete.EntityMentionSet;
import edu.jhu.hlt.concrete.EntitySet;
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
import edu.jhu.hlt.concrete.ingest.conll.Conll2011;
import edu.jhu.hlt.tutils.Document.Constituent;
import edu.jhu.hlt.tutils.Document.ConstituentItr;
import edu.jhu.hlt.tutils.Document.ConstituentType;
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
 * Sentences are represented as constituent nodes with the LHS equal to the
 * {@link Section} index;
 *
 * @author travis
 */
public class ConcreteToDocument {

  public boolean debug = false;
  public boolean debug_cons = false;

  /** Only supports a single POS {@link TokenTagging} for now */
  protected String posToolName = Conll2011.META_POS.getTool();

  /** Only supports {@link TokenTagging} style NER for now */
  protected String nerToolName = Conll2011.META_NER.getTool();

  /** Only supports a single {@link Parse} for now */
  protected String consParseToolName = Conll2011.META_PARSE.getTool();
  // TODO allow this to be set set separately
  protected ConstituentType consParseToolType = ConstituentType.PTB_GOLD;

  /**
   * Assumes Propbank SRL is stored as a {@link SituationMention} using
   * {@link ConstituentRef}. See the documentation in {@link Document} for how
   * they are added to the {@link Document}.
   */
  protected String propbankSrlToolName = null;

  /**
   * Looks for an {@link EntitySet} matching this tool name and adds them to
   * {@link Document#cons_coref_gold}.
   */
  protected String entitySetToolName = null;

  protected BrownClusters bc256, bc1000;
  protected IRAMDictionary wnDict;

  protected Language lang;

  /**
   * If you provide null for any of these args it will work but just not set
   * these fields
   */
  public ConcreteToDocument(
      BrownClusters bc256, BrownClusters bc1000,
      IRAMDictionary wordNet,
      Language lang) {
    this.bc256 = bc256;
    this.bc1000 = bc1000;
    this.wnDict = wordNet;
    this.lang = lang;
  }

  public Language getLanguage() {
    return lang;
  }

  public void setNerToolName(String toolName) {
    this.nerToolName = toolName;
  }

  public void setPosToolName(String toolName) {
    this.posToolName = toolName;
  }

  public void setConstituencyParseToolname(String toolName) {
    this.consParseToolName = toolName;
  }

  public void setPropbankToolname(String toolName) {
    this.propbankSrlToolName = toolName;
  }

  public void setCorefToolname(String toolName) {
    this.entitySetToolName = toolName;
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
    if (pos != null)
      token.setPos(alph.pos(pos.getTaggedTokenList().get(tokenIdx).getTag()));
    if (ner != null)
      token.setNer(alph.ner(ner.getTaggedTokenList().get(tokenIdx).getTag()));

    if (lang.isRoman()) {
      token.setWordNocase(alph.word(w.toLowerCase()));
      token.setShape(alph.shape(WordShape.wordShape(w)));
    }

    // WordNet synset id
    if (wnDict != null && pos != null && lang == Language.EN) {
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
   * Returns the coreference clustering represented by coref as constituents in
   * the document (see {@link Document#cons_coref_gold} for documentation on
   * the representation).
   * @param mapping may be null
   * @return the first Constituent (which is a pointer to an {@link Entity} and
   * also a linked list representing a {@link EntitySet}) added or null if coref
   * is empty.
   */
  public static Constituent addCorefConstituents(
      EntitySet coref,
      EntityMentionSet mentions,
      Document.ConstituentItr cons,
      ConcreteDocumentMapping mapping,
      MultiAlphabet alph) {

    int added = 0;
    Document doc = cons.getDocument();
    Constituent start = doc.getConstituent(cons.getIndex());

    // In the event that there are no entities in this coreference set
    // (this is allowed, there must be 1+ mentions in each entity, but there
    // can be 0 entities in a EntitySet).
    start.setLhs(alph.word("NullEntity"));  // more for debugging than processing
    start.setParent(Document.NONE);
    start.setOnlyChild(Document.NONE);
    start.setLeftSib(Document.NONE);
    start.setRightSib(Document.NONE);

    Map<UUID, EntityMention> ems = indexByUUID(mentions.getMentionList());

    int prevEnt = Document.NONE;
    for (Entity ent : coref.getEntityList()) {          // LOOP over Entities

      if (ent.getMentionIdListSize() == 0)
        throw new RuntimeException("empty Entity?");

      Constituent entC = cons.forwardsC();
      entC.setParent(Document.NONE);
      entC.setLhs(alph.word(ent.getType()));
      entC.setOnlyChild(cons.getIndex());
      entC.setLeftSib(prevEnt);
      entC.setRightSib(Document.NONE);
      if (prevEnt != Document.NONE)
        doc.getConstituent(prevEnt).setRightSib(entC.getIndex());
      prevEnt = entC.getIndex();

      // Update mapping: Entity.UUID
      mapping.put(entC, ent.getUuid());

      int prevMention = Document.NONE;
      for (UUID emId : ent.getMentionIdList()) {        // LOOP over Mentions

        // Update mapping: EntityMention.UUID
        mapping.put(doc.getConstituent(cons.getIndex()), emId);

        EntityMention em = ems.get(emId);
        TokenRefSequence trs = em.getTokens();
        List<Integer> tokens = trs.getTokenIndexList();
        if (!ascending(tokens))
          throw new RuntimeException("can't handle split sequences");
        // Convert from sentence-relative to document-relative
        Document.Constituent sentence = mapping.get(trs.getTokenizationId());
        int first = sentence.getFirstToken() + tokens.get(0);
        int last = sentence.getFirstToken() + tokens.get(tokens.size() - 1);

        entC.setRightChild(cons.getIndex());
        cons.setLhs(entC.getLhs());
        cons.setParent(entC.getIndex());
        cons.setOnlyChild(Document.NONE);
        cons.setFirstToken(first);
        cons.setLastToken(last);
        cons.setLeftSib(prevMention);
        cons.setRightSib(Document.NONE);
        if (prevMention != Document.NONE)
          doc.getConstituent(prevMention).setRightSib(cons.getIndex());
        prevMention = cons.getIndex();
        cons.forwards();
        added++;
      }
    }

    if (added == 0)
      Log.warn("empty EntitySet? coref=" + coref);
    return start;
  }

  public static boolean ascending(List<Integer> numbers) {
    if (numbers.isEmpty())
      return false;
    int first = numbers.get(0);
    for (int i = 1; i < numbers.size(); i++)
      if (numbers.get(i) != first + i)
        return false;
    return true;
  }

  /**
   * Add the constituents in the given {@link Parse} to the {@link Document}
   * using the {@link ConstituentItr}.
   *
   * Concrete child indices are sentence-relative but Document.Constituent's are
   * document-relative. tokenOffset says how many tokens have come before this
   * parse/sentence to allow conversion.
   */
  public void addParseConstituents(
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
      assert ccon.isSetStart() == ccon.isSetEnding();

      cons.setLhs(alph.cfg(ccon.getTag()));
      cons.setParent(Document.NONE);      // Will be over-written later
      cons.setOnlyChild(Document.NONE);   // Will be over-written later
      if (ccon.isSetStart()) {
        if (debug_cons)
          Log.info("setting lastToken[" + cons.getIndex() + "]=" + (tokenOffset + ccon.getEnding() - 1));
        cons.setFirstToken(tokenOffset + ccon.getStart());
        cons.setLastToken(tokenOffset + ccon.getEnding() - 1);
      }
      ccon2dcon[ccon.getId()] = cons.getIndex();

      // Set parent pointer (for leaf tokens)
      if (ccon.getChildListSize() == 0) {
        if (cons.getFirstToken() < 0 || cons.getLastToken() < 0)
          throw new RuntimeException();
        Document doc = cons.getDocument();
        int[] cons_parent = doc.getConsParent(consParseToolType);
        for (int i = cons.getFirstToken(); i <= cons.getLastToken(); i++) {
          if (cons_parent[i] != Document.UNINITIALIZED)
            throw new RuntimeException();
          cons_parent[i] = cons.getIndex();
        }
      }

      // Store this Constituents index
      int i = cons.forwards();
      ConstituentRef cr = new ConstituentRef(p.getUuid(), pl++);
      Integer old = constituentIndices.put(cr, i);
      if (old != null) throw new RuntimeException();
    }

    // Set parent, left/right children, and left/right sibling
    Document d = cons.getDocument();
    for (edu.jhu.hlt.concrete.Constituent ccon : p.getConstituentList()) {
      int parentIdx = ccon2dcon[ccon.getId()];
      Document.Constituent parent = d.getConstituent(parentIdx);
      int prevChildIdx = Document.NONE;
      for (int child : ccon.getChildList()) {
        int childIdx = ccon2dcon[p.getConstituentList().get(child).getId()];
        Document.Constituent c = d.getConstituent(childIdx);
        c.setParent(parentIdx);
        c.setLeftSib(prevChildIdx);
        if (prevChildIdx >= 0)
          d.getConstituent(prevChildIdx).setRightSib(childIdx);
        if (parent.getLeftChild() < 0) {
          if (debug_cons) {
            Log.info("setting " + parent.show(alph) + ".firstChild=" + c.show(alph));
          }
          parent.setLeftChild(childIdx);
        }
        parent.setRightChild(childIdx);
        if (debug_cons) {
          Log.info("setting " + parent.show(alph) + ".rightChild=" + c.show(alph));
        }
        prevChildIdx = childIdx;
      }
      if (prevChildIdx >= 0)
        d.getConstituent(prevChildIdx).setRightSib(Document.NONE);
    }
  }

  public Constituent addPropbankSrl(
      Communication c,
      String situationMentionSetToolName,
      Map<ConstituentRef, Integer> constituentIndices,  // TODO fold into mapping
      ConcreteDocumentMapping mapping,
      Document.ConstituentItr constituent,
      MultiAlphabet alph) {

    Document doc = constituent.getDocument();
    assert doc == mapping.getDocument();

    Constituent first = null;
    SituationMentionSet sms = findByTool(c.getSituationMentionSetList(), situationMentionSetToolName);
    if (sms == null) {
      System.err.println("failed to find SituationMentionSet by tool: " + situationMentionSetToolName);
    } else {
      // What if there are no situations?
      first = doc.getConstituent(constituent.getIndex());
      constituent.setParent(Document.NONE);
      constituent.setOnlyChild(Document.NONE);
      constituent.setLhs(alph.srl("EMPTY-SRL"));
      constituent.setFirstToken(Document.NONE);
      constituent.setLastToken(Document.NONE);
      constituent.setLeftSib(Document.NONE);
      constituent.setRightSib(Document.NONE);
      int prevSit = Document.NONE;
      for (SituationMention sm : sms.getMentionList()) {

        // Make a situation node which is the parent of all the pred/args
        Constituent sit = constituent.forwardsC();
        sit.setLhs(alph.srl("propbank"));
        sit.setLeftSib(prevSit);
        sit.setRightSib(Document.NONE); // will be over-written
        sit.setParent(Document.NONE);
        if (prevSit != Document.NONE)
          doc.getConstituent(prevSit).setRightSib(sit.getIndex());
        prevSit = sit.getIndex();

        // Constituent index of previous pred/arg
        int prev = Document.NONE;

        // Set the predicate
        // first/last tokens + left/right children
        setupConstituent(sm.getConstituent(), sm.getTokens(), constituent,
            constituentIndices, mapping, doc);
        // Everything else
        constituent.setLhs(alph.srl(sm.getSituationKind()));
        constituent.setParent(sit.getIndex());
        constituent.setLeftSib(Document.NONE);
        constituent.setRightSib(Document.NONE);

        // Set sit -> pred
        sit.setLeftChild(constituent.getIndex());
        sit.setRightChild(constituent.getIndex());
        if (debug) {
          Log.info("adding Situation text=\"" + sm.getText()
              + "\" first=" + constituent.getFirstToken()
              + " last=" + constituent.getLastToken());
        }

        // Set the parent links (verb token -> propbank cons node)
        assert constituent.getFirstToken() >= 0;
        assert constituent.getLastToken() >= 0;
        int[] parents = doc.getConsParent(ConstituentType.PROPBANK_GOLD);
        for (int tokI = constituent.getFirstToken(); tokI <= constituent.getLastToken(); tokI++) {
          parents[tokI] = constituent.getIndex();
        }

        if (debug) {
          Log.info("pred.firstToken=" + constituent.getFirstToken()
              + " pred.lastToken=" + constituent.getLastToken());
        }

        // Store the bounds of the entire situation
        int firstT = constituent.getFirstToken();
        int lastT = constituent.getLastToken();

        prev = constituent.forwards();

        // Set the arguments
        int numArgs = sm.getArgumentListSize();
        for (int ai = 0; ai < numArgs; ai++) {
          MentionArgument arg = sm.getArgumentList().get(ai);
          // first/last tokens + left/right children
          setupConstituent(arg.getConstituent(), arg.getTokens(), constituent,
              constituentIndices, mapping, doc);
          // Everything else
          constituent.setLhs(alph.srl(arg.getRole()));
          constituent.setParent(sit.getIndex());
          constituent.setLeftSib(prev);
          constituent.setRightSib(Document.NONE);

          doc.getConstituent(prev).setRightSib(constituent.getIndex());

          // Update bounds of entire situation
          assert constituent.getFirstToken() >= 0;
          if (constituent.getFirstToken() < firstT)
            firstT = constituent.getFirstToken();
          assert constituent.getLastToken() >= 0;
          if (constituent.getLastToken() > lastT)
            lastT = constituent.getLastToken();

          if (debug) {
            Log.info("setting arg, role=" + arg.getRole()
                + " first=" + constituent.getFirstToken()
                + " last=" + constituent.getLastToken()
                + " leftChild=" + constituent.getLeftChild()
                + " rightChild=" + constituent.getRightChild());
          }

          // Update right child of situation
          sit.setRightChild(constituent.getIndex());

          prev = constituent.forwards();
        }

        // Set the first and last token for the entire situation
        sit.setFirstToken(firstT);
        sit.setLastToken(lastT);

        if (debug) {
          Log.info("sit.firstToken=" + sit.getFirstToken()
              + " sit.lastToken=" + sit.getLastToken()
              + " numArgs=" + numArgs);
        }
      }
    }
    return first;
  }

  /**
   * Convert a {@link Communication} into a {@link Document}.
   */
  public ConcreteDocumentMapping communication2Document(
      Communication c, int docIndex, MultiAlphabet alph) {

    Document doc = new Document(c.getId(), docIndex, alph);
    ConcreteDocumentMapping mapping = new ConcreteDocumentMapping(c, doc);
    Document.ConstituentItr constituent = doc.getConstituentItr(0);
    constituent.allowExpansion(true);
    doc.reserveConstituents(64);

    // Count the number of tokens and add the sentence sectioning
    doc.cons_sentences = constituent.getIndex();
    int sectionIdx = 0;
    int numToks = 0;
    int prevSent = Document.NONE;
    // TODO add section segmentation as separate constituency parse?
    // Note that this info is already captured in the LHS of the sentence segmentation/cparse
    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {

        Tokenization tkz = ss.getTokenization();
        int start = numToks;
        numToks += tkz.getTokenList().getTokenListSize();

        // Update mapping: Document.Constituent <=> Tokenization.UUID
        mapping.put(doc.getConstituent(constituent.getIndex()), tkz.getUuid());

        constituent.setLhs(sectionIdx);
        constituent.setFirstToken(start);
        constituent.setLastToken(numToks - 1);
        constituent.setOnlyChild(Document.NONE);
        constituent.setParent(Document.NONE);
        constituent.setRightSib(Document.NONE);
        constituent.setLeftSib(prevSent);
        if (prevSent != Document.NONE)
          doc.getConstituent(prevSent).setRightSib(constituent.getIndex());
        prevSent = constituent.forwards();
      }
      sectionIdx++;
    }

    // Build the Document
    DocumentTester test = new DocumentTester(doc, true);
    doc.reserveTokens(numToks);
    Document.TokenItr token = doc.getTokenItr(0);
    Map<ConstituentRef, Integer> constituentIndices = new HashMap<>();
    int prevParse = Document.NONE;
    for (Section s : c.getSectionList()) {
      token.setBreakSafe(Document.Paragraph.BREAK_LEVEL);
      for (Sentence ss : s.getSentenceList()) {
        token.setBreakSafe(Document.Sentence.BREAK_LEVEL);

        int tokenOffset = token.getIndex();
        if (debug) Log.info("tokenOffset=" + tokenOffset);
        Tokenization tkz = ss.getTokenization();

        TokenTagging pos = null;
        if (posToolName != null)
          pos = findByTool(tkz.getTokenTaggingList(), posToolName);
        TokenTagging ner = null;
        if (nerToolName != null)
          findByTool(tkz.getTokenTaggingList(), nerToolName);

        int n = tkz.getTokenList().getTokenListSize();
        for (int i = 0; i < n; i++) {
          setToken(token, c, tkz, pos, ner, tokenOffset, i, alph);
          if (debug) {
            Log.info("just set token[" + token.index + "]=" + token.getWordStr());
          }
          token.forwards();
        }

        Parse p = findByTool(tkz.getParseList(), consParseToolName);
        int start = constituent.getIndex();

        if (doc.cons_ptb_gold == Document.NONE)
          doc.cons_ptb_gold = start;
        constituent.setLeftSib(prevParse);
        constituent.setRightSib(Document.NONE);
        if (prevParse != Document.NONE)
          doc.getConstituent(prevParse).setRightSib(constituent.getIndex());
        prevParse = constituent.getIndex();

        addParseConstituents(p, tokenOffset, constituent, constituentIndices, alph);
        int end = constituent.getIndex() - 1;
        if (start == end) {
          // NOTE: There is at least one sentence in Ontonotes5 which has a
          // parse which is something like (TOP (S (NP (-NONE- PRO)))), which
          // has no leaf tokens. In the skel/conll (tabular format) this still
          // has a row with no annotations. This will be as if we skipped that
          // sentence.
          // /home/travis/code/fnparse/data/ontonotes-release-5.0/LDC2013T19/data/files/data/english/annotations/nw/p2.5_c2e/00/p2.5_c2e_0034.parse
          // (TOP (S (NP-SBJ (-NONE- *PRO*))))
          Log.warn("there is an empty tree for this sentence:"
              + " communication=" + c.getId()
              + " section=" + s.getUuid()
              + " sentence=" + ss.getUuid()
              + " tokenization=" + tkz.getUuid());
        } else {
          assert end > start : "start=" + start + " end=" + end;
          assert test.checkConstituencyTree(start, end);
        }
      }
    }

    // Compute some derived values in Document
    doc.computeDepths();

//    assert test.firstAndLastTokensValid();

    // Add Propbank SRL
    if (this.propbankSrlToolName != null) {
      Constituent propbankSrl = addPropbankSrl(c, this.propbankSrlToolName,
          constituentIndices, mapping, constituent, alph);
      if (propbankSrl == null)
        Log.warn("Failed to get Propbank SRL");
      else
        doc.cons_propbank_gold = propbankSrl.getIndex();
    }

    // Add coref
    if (entitySetToolName != null) {
      if (debug)
        Log.info("adding EntityMentionSet: " + entitySetToolName);
      EntitySet es = findByTool(c.getEntitySetList(), entitySetToolName);
      if (!es.isSetMentionSetId())
        throw new RuntimeException("implement EntityMentionSet finder for when EntitySet doesn't provide one");
      EntityMentionSet ems = findByUUID(c.getEntityMentionSetList(), es.getMentionSetId());
      Constituent coref = addCorefConstituents(es, ems, constituent, mapping, alph);
      if (coref == null)
        throw new RuntimeException("emtpy EntitySet? " + es.getUuid());
      else
        doc.cons_coref_gold = coref.getIndex();
    }

    // Compute BrownClusters
    if (bc256 != null || bc1000 != null)
      BrownClusters.setClusters(doc, bc256, bc1000);

    return mapping;
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
  private void setupConstituent(
      ConstituentRef cr,
      TokenRefSequence trs,
      ConstituentItr constituent,
      Map<ConstituentRef, Integer> constituentIndices,
      ConcreteDocumentMapping mapping,  // used for Tokenization.UUID => Constituent => firstToken
      Document doc) {
    if (cr != null) {
      if (debug)
        Log.info("setting constituent");
      int i = constituentIndices.get(cr);
      constituent.setOnlyChild(i);
    } else {
      if (debug)
        Log.info("making constituent from TokenRefSequence");
      if (trs == null)
        throw new IllegalArgumentException();
      int tokenOffset = mapping.get(trs.getTokenizationId()).getFirstToken();
      int s = tokenOffset + min(trs.getTokenIndexList());
      int e = tokenOffset + max(trs.getTokenIndexList());
      // Insert dummy constituent/span
      constituent.setLhs(doc.getAlphabet().cfg("NOT-A-CONSTITUENT"));
      constituent.setFirstToken(s);
      constituent.setLastToken(e);
      constituent.setLeftSib(Document.NONE);
      constituent.setRightSib(Document.NONE);
      constituent.setOnlyChild(Document.NONE);
      int dummySpan = constituent.forwards();
      constituent.setOnlyChild(dummySpan);
      int parent = constituent.gotoConstituent(dummySpan);
      constituent.setParent(parent);
      constituent.gotoConstituent(parent);
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

  public static ConcreteToDocument makeInstance(Language lang) {
    IRAMDictionary wnDict = null;
    BrownClusters bc256 = null;
    BrownClusters bc1000 = null;
    if (lang == Language.EN) {
      File bcParent = new File("/home/travis/code/fnparse/data/embeddings");
      Log.info("loading BrownClusters (256)");
      bc256 = new BrownClusters(BrownClusters.bc256dir(bcParent));
      Log.info("loading BrownClusters (1000)");
      bc1000 = new BrownClusters(BrownClusters.bc1000dir(bcParent));
      File wnDictDir = new File("/home/travis/code/coref/data/wordnet/dict/");
      wnDict = new RAMDictionary(wnDictDir, ILoadPolicy.IMMEDIATE_LOAD);
      try {
        Log.info("loading WordNet");
        wnDict.open();
      } catch(Exception e) {
        throw new RuntimeException(e);
      }
    }
    ConcreteToDocument io = new ConcreteToDocument(bc256, bc1000, wnDict, lang);
    return io;
  }

  public static <T> T findByUUID(List<T> items, UUID id) {
    T match = null;
    List<UUID> possible = new ArrayList<>();
    for (T t : items) {

      UUID am;
      try {
        am = (UUID) t.getClass().getMethod("getId").invoke(t);
      } catch (Exception nsm1) {
        try {
          am = (UUID) t.getClass().getMethod("getUuid").invoke(t);
        } catch (Exception nsm2) {
          try {
            am = (UUID) t.getClass().getMethod("getUUID").invoke(t);
          } catch (Exception nsm3) {
            throw new RuntimeException("couldn't figure out id for: " + t);
          }
        }
      }

      possible.add(am);
      if (id.equals(am)) {
        if (match != null) {
          throw new RuntimeException("non-unique UUID \""
              + id + "\" in " + possible);
        }
        match = t;
      }
    }
    if (match == null) {
      throw new RuntimeException("couldn't find tool named \""
          + id + "\" in " + possible);
    }
    return match;
  }

  public static <T> T findByTool(List<T> items, String toolname) {
    T match = null;
    List<String> possible = new ArrayList<>();
    for (T t : items) {
      try {
        Method m = t.getClass().getMethod("getMetadata");
        AnnotationMetadata am = (AnnotationMetadata) m.invoke(t);
        possible.add(am.getTool());
        if (toolname.equals(am.getTool())) {
          if (match != null) {
            throw new RuntimeException("non-unique toolname \""
                + toolname + "\" in " + possible
                + " [" + items.get(0).getClass() + "]");
          }
          match = t;
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    if (match == null) {
      throw new RuntimeException("couldn't find tool named \""
          + toolname + "\" in " + possible
          + " [" + items.get(0).getClass() + "]");
    }
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
