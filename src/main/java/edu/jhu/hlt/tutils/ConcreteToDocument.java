package edu.jhu.hlt.tutils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import edu.jhu.hlt.concrete.AnnotationMetadata;
import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.ConstituentRef;
import edu.jhu.hlt.concrete.DependencyParse;
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
import edu.jhu.hlt.tutils.Document.Constituent;
import edu.jhu.hlt.tutils.Document.ConstituentItr;
import edu.jhu.hlt.tutils.Document.ConstituentType;
import edu.jhu.hlt.tutils.Document.Token;
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

  public static final Predicate<TokenTagging> STANFORD_POS = tt -> {
    return "Stanford CoreNLP".equals(tt.getMetadata().getTool())
        && "POS".equals(tt.getTaggingType());
  };
  public static final Predicate<TokenTagging> STANFORD_NER = tt -> {
    return "Stanford CoreNLP".equals(tt.getMetadata().getTool())
        && "NER".equals(tt.getTaggingType());
  };
  public static final Predicate<TokenTagging> STANFORD_LEMMA = tt -> {
    return "Stanford CoreNLP".equals(tt.getMetadata().getTool())
        && "LEMMA".equals(tt.getTaggingType());
  };
  public static final Predicate<Parse> STANFORD_CPARSE = p -> {
    return "Stanford CoreNLP".equals(p.getMetadata().getTool());
  };
  public static final Predicate<DependencyParse> STANFORD_DPARSE_BASIC = p -> {
    return "Stanford CoreNLP basic".equals(p.getMetadata().getTool());
  };
  public static final Predicate<DependencyParse> STANFORD_DPARSE_COLL = p -> {
    return "Stanford CoreNLP col".equals(p.getMetadata().getTool());
  };
  public static final Predicate<DependencyParse> STANFORD_DPARSE_COLL_CC = p -> {
    return "Stanford CoreNLP col-CC".equals(p.getMetadata().getTool());
  };
  // TODO coref{ems,es}


  public boolean debug = false;
  public boolean debug_cons = false;

  /** Only supports a single POS {@link TokenTagging} for now */
  protected String posToolName = "conll-2011 POS";

  /** Only supports {@link TokenTagging} style NER for now */
  protected String nerToolName = "conll-2011 NER";

  /** Only supports a single {@link Parse} for now */
  protected String consParseToolName = "conll-2011 parse";
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

  // If true, look for concrete-stanford pos, ner, lemma, cparse, dparse{1,2,3},
  // coref and put them into stanford-specific fields in Document.
  public boolean ingestConcreteStanford = false;

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
      TokenTagging lemma,
      TokenTagging posG,
      TokenTagging posH,
      TokenTagging nerG,
      TokenTagging nerH,
      int tokenIdx,
      MultiAlphabet alph) {
    edu.jhu.hlt.concrete.Token ct = t.getTokenList().getTokenList().get(tokenIdx);
    assert ct.isSetText();
    String w = ct.getText();
    token.setWord(alph.word(w));
    if (lemma != null)
      token.setLemma(alph.word(lemma.getTaggedTokenList().get(tokenIdx).getTag()));
    if (posG != null)
      token.setPosG(alph.pos(posG.getTaggedTokenList().get(tokenIdx).getTag()));
    if (posH != null)
      token.setPosH(alph.pos(posH.getTaggedTokenList().get(tokenIdx).getTag()));
    if (nerG != null)
      token.setNerG(alph.ner(nerG.getTaggedTokenList().get(tokenIdx).getTag()));
    if (nerH != null)
      token.setNerH(alph.ner(nerH.getTaggedTokenList().get(tokenIdx).getTag()));

    if (lang.isRoman()) {
      token.setWordNocase(alph.word(w.toLowerCase()));
      token.setShape(alph.shape(WordShape.wordShape(w)));
    }

    // WordNet synset id
    if (wnDict != null && posG != null && lang == Language.EN) {
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
      Document doc,
      ConcreteDocumentMapping mapping,
      MultiAlphabet alph) {

    int added = 0;
    Constituent start = doc.newConstituent();

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

      Constituent entC = added == 0 ? start : doc.newConstituent();
      entC.setParent(Document.NONE);
      entC.setLhs(alph.word(ent.getType()));
      entC.setOnlyChild(Document.NONE);
      entC.setLeftSib(prevEnt);
      entC.setRightSib(Document.NONE);
      if (prevEnt != Document.NONE)
        doc.getConstituent(prevEnt).setRightSib(entC.getIndex());
      prevEnt = entC.getIndex();

      // Update mapping: Entity.UUID
      mapping.put(entC, ent.getUuid());

      int prevMention = Document.NONE;
      for (UUID emId : ent.getMentionIdList()) {        // LOOP over Mentions

        Constituent cons = doc.newConstituent();

        // Update parent
        if (entC.getLeftChild() == Document.NONE)
          entC.setLeftChild(cons.getIndex());
        entC.setRightChild(cons.getIndex());

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
   *
   * @return the root of the converted tree.
   */
  public Constituent addParseConstituents(
      Parse p,
      ConstituentType consParseToolType,
      int tokenOffset,
      Document doc,
      Map<ConstituentRef, Integer> constituentIndices,
      MultiAlphabet alph) {

    // Sanity check: Constituents shouldn't really have ids, they should work
    // only on indices.
    for (int i = 0; i < p.getConstituentListSize(); i++)
      assert i == p.getConstituentList().get(i).getId();

    BitSet isNotChild = new BitSet();   // constituent indices
    int adds = 0;

    // Build the constituents and mapping between them
    int nCons = p.getConstituentListSize();
    int[] ccon2dcon = new int[nCons];
    Arrays.fill(ccon2dcon, -1);
    int pl = 0; // index into parse.constituentList
    for (edu.jhu.hlt.concrete.Constituent ccon : p.getConstituentList()) {
      if (debug_cons)
        Log.info("ccon=" + ccon);
      assert ccon.isSetStart() == ccon.isSetEnding();

      adds++;
      Constituent cons = doc.newConstituent();
      isNotChild.set(cons.getIndex(), true);
      cons.setLhs(alph.cfg(ccon.getTag()));
      cons.setParent(Document.NONE);      // Will be over-written later
      cons.setOnlyChild(Document.NONE);   // Will be over-written later
      if (ccon.isSetStart()) {
        if (debug_cons)
          Log.info("setting lastToken[" + cons.getIndex() + "]=" + (tokenOffset + ccon.getEnding() - 1));
        cons.setFirstToken(tokenOffset + ccon.getStart());
        cons.setLastToken(tokenOffset + ccon.getEnding() - 1);
      }
      assert cons.getIndex() >= 0;
      if (ccon2dcon[ccon.getId()] >= 0)
        throw new RuntimeException("loopy parse? " + p);
      ccon2dcon[ccon.getId()] = cons.getIndex();

      if (consParseToolType != null) {
        // Set parent pointer (for leaf tokens)
        if (ccon.getChildListSize() == 0) {
          if (cons.getFirstToken() < 0 || cons.getLastToken() < 0)
            throw new RuntimeException();
          int[] cons_parent = doc.getConsParent(consParseToolType);
          for (int i = cons.getFirstToken(); i <= cons.getLastToken(); i++) {
            if (cons_parent[i] != Document.UNINITIALIZED)
              throw new RuntimeException();
            cons_parent[i] = cons.getIndex();
          }
        }
      }

      // Store this Constituents index
      int i = cons.getIndex();
      ConstituentRef cr = new ConstituentRef(p.getUuid(), pl++);
      Integer old = constituentIndices.put(cr, i);
      if (old != null) throw new RuntimeException();
    }

    // Set parent, left/right children, and left/right sibling
    for (edu.jhu.hlt.concrete.Constituent ccon : p.getConstituentList()) {
      int parentIdx = ccon2dcon[ccon.getId()];
      Document.Constituent parent = doc.getConstituent(parentIdx);
      int prevChildIdx = Document.NONE;
      for (int child : ccon.getChildList()) {
        int childIdx = ccon2dcon[p.getConstituentList().get(child).getId()];
        isNotChild.set(childIdx, false);
        Document.Constituent c = doc.getConstituent(childIdx);
        c.setParent(parentIdx);
        c.setLeftSib(prevChildIdx);
        if (prevChildIdx >= 0)
          doc.getConstituent(prevChildIdx).setRightSib(childIdx);
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
        doc.getConstituent(prevChildIdx).setRightSib(Document.NONE);
    }

    if (isNotChild.cardinality() != 1)
      throw new RuntimeException("isNotChild: " + isNotChild);
    Constituent root = doc.getConstituent(isNotChild.nextSetBit(0));

    if (adds == 0) {
      // NOTE: There is at least one sentence in Ontonotes5 which has a
      // parse which is something like (TOP (S (NP (-NONE- PRO)))), which
      // has no leaf tokens. In the skel/conll (tabular format) this still
      // has a row with no annotations. This will be as if we skipped that
      // sentence.
      // /home/travis/code/fnparse/data/ontonotes-release-5.0/LDC2013T19/data/files/data/english/annotations/nw/p2.5_c2e/00/p2.5_c2e_0034.parse
      // (TOP (S (NP-SBJ (-NONE- *PRO*))))
      Log.warn("there is an empty tree for this sentence:"
          + " parse=" + p.getUuid());
    }
    return root;
  }

  public Constituent addPropbankSrl(
      Communication c,
      String situationMentionSetToolName,
      Map<ConstituentRef, Integer> constituentIndices,  // TODO fold into mapping
      ConcreteDocumentMapping mapping,
      MultiAlphabet alph) {

    Document doc = mapping.getDocument();

    Constituent first = null;
    SituationMentionSet sms = findByTool(c.getSituationMentionSetList(), situationMentionSetToolName);
    if (sms == null) {
      System.err.println("failed to find SituationMentionSet by tool: " + situationMentionSetToolName);
      return null;
    }

    int prevSit = Document.NONE;
    for (SituationMention sm : sms.getMentionList()) {

      // Make a situation node which is the parent of all the pred/args
      Constituent sit = doc.newConstituent();
      sit.setLhs(alph.srl("propbank"));
      sit.setLeftSib(prevSit);
      sit.setRightSib(Document.NONE); // will be over-written
      sit.setParent(Document.NONE);
      if (prevSit != Document.NONE)
        doc.getConstituent(prevSit).setRightSib(sit.getIndex());
      prevSit = sit.getIndex();

      // Set the predicate
      // first/last tokens + left/right children
      Constituent pred = doc.newConstituent();
      setupConstituent(sm.getConstituent(), sm.getTokens(), pred,
          constituentIndices, mapping, doc);
      // Everything else
      pred.setLhs(alph.srl(sm.getSituationKind()));
      pred.setParent(sit.getIndex());
      pred.setLeftSib(Document.NONE);
      pred.setRightSib(Document.NONE);

      // Set sit -> pred
      sit.setOnlyChild(pred.getIndex());
      if (debug)
        Log.info("adding Situation text=\"" + sm.getText());

      // Set the parent links (verb token -> propbank cons node)
      assert pred.getFirstToken() >= 0;
      assert pred.getLastToken() >= 0;
      int[] parents = doc.getConsParent(ConstituentType.PROPBANK_GOLD);
      for (int tokI = pred.getFirstToken(); tokI <= pred.getLastToken(); tokI++) {
        parents[tokI] = pred.getIndex();
      }

      if (debug) {
        Log.info("pred.firstToken=" + pred.getFirstToken()
            + " pred.lastToken=" + pred.getLastToken());
      }

      // Store the bounds of the entire situation
      int firstT = pred.getFirstToken();
      int lastT = pred.getLastToken();

      // Set the arguments
      int prev = pred.getIndex();
      int numArgs = sm.getArgumentListSize();
      for (int ai = 0; ai < numArgs; ai++) {
        MentionArgument arg = sm.getArgumentList().get(ai);
        Constituent argc = doc.newConstituent();
        // first/last tokens + left/right children
        setupConstituent(arg.getConstituent(), arg.getTokens(), argc,
            constituentIndices, mapping, doc);
        // Everything else
        argc.setLhs(alph.srl(arg.getRole()));
        argc.setParent(sit.getIndex());
        argc.setLeftSib(prev);
        argc.setRightSib(Document.NONE);

        doc.getConstituent(prev).setRightSib(argc.getIndex());

        // Update bounds of entire situation
        assert argc.getFirstToken() >= 0;
        if (argc.getFirstToken() < firstT)
          firstT = argc.getFirstToken();
        assert argc.getLastToken() >= 0;
        if (argc.getLastToken() > lastT)
          lastT = argc.getLastToken();

        if (debug) {
          Log.info("setting arg, role=" + arg.getRole()
              + " first=" + argc.getFirstToken()
              + " last=" + argc.getLastToken()
              + " leftChild=" + argc.getLeftChild()
              + " rightChild=" + argc.getRightChild());
        }

        // Update right child of situation
        sit.setRightChild(argc.getIndex());

        prev = argc.getIndex();
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
    return first;
  }

  public void addParagraphs(Communication c, Document doc) {
    int paragraphStart = 0;
    int prevPar = Document.NONE;
    doc.cons_paragraph = Document.NONE;
    for (Section s : c.getSectionList()) {
      int sectionLen = 0;
      for (Sentence ss : s.getSentenceList()) {
        Tokenization tkz = ss.getTokenization();
        sectionLen += tkz.getTokenList().getTokenListSize();
      }
      Constituent constituent = doc.newConstituent();
      if (doc.cons_paragraph == Document.NONE)
        doc.cons_paragraph = constituent.getIndex();
      constituent.setParent(Document.NONE);
      constituent.setOnlyChild(Document.NONE);
      constituent.setLeftSib(prevPar);
      constituent.setFirstToken(paragraphStart);
      constituent.setLastToken(paragraphStart + sectionLen - 1);
      constituent.setRightSib(Document.NONE);
      if (prevPar >= 0)
        doc.getConstituent(prevPar).setRightSib(constituent.getIndex());
      prevPar = constituent.getIndex();
      paragraphStart += sectionLen;
    }
  }

  /**
   * Convert a {@link Communication} into a {@link Document}.
   */
  public ConcreteDocumentMapping communication2Document(
      Communication c, int docIndex, MultiAlphabet alph) {

    Document doc = new Document(c.getId(), docIndex, alph);
    doc.allowExpansion(true);
    ConcreteDocumentMapping mapping = new ConcreteDocumentMapping(c, doc);

    // Count the number of tokens and add the sentence sectioning
    int sectionIdx = 0;
    int numToks = 0;
    int prevSent = Document.NONE;
    // Note that this info is already captured in the LHS of the sentence segmentation/cparse
    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {

        Tokenization tkz = ss.getTokenization();
        int start = numToks;
        numToks += tkz.getTokenList().getTokenListSize();

        Constituent constituent = doc.newConstituent();
        if (doc.cons_sentences == Document.NONE)
          doc.cons_sentences = constituent.getIndex();

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
        prevSent = constituent.getIndex();
      }
      sectionIdx++;
    }

    // Add paragraph/section segmentation as linked list of constituents
    addParagraphs(c, doc);

    // Build the Document
//    DocumentTester test = new DocumentTester(doc, true);
    int tokenOffset = 0;
    Map<ConstituentRef, Integer> constituentIndices = new HashMap<>();
    int prevParseG = Document.NONE;
    int prevParseH = Document.NONE;
    LabeledDirectedGraph.Builder dparseBasic = new LabeledDirectedGraph().new Builder();
    LabeledDirectedGraph.Builder dparseColl = new LabeledDirectedGraph().new Builder();
    LabeledDirectedGraph.Builder dparseCollCC = new LabeledDirectedGraph().new Builder();
    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {

        Tokenization tkz = ss.getTokenization();

        if (debug) {
          System.out.println();
          System.out.println("ingestConcreteStanford=" + ingestConcreteStanford);
          for (TokenTagging tt : tkz.getTokenTaggingList()) {
            System.out.println("tokOffset=" + tokenOffset
                + " TokenTagging type=" + tt.getTaggingType()
                + " tool=" + tt.getMetadata().getTool());
          }
          for (Parse p : tkz.getParseList()) {
            System.out.println("tokOffset=" + tokenOffset
                + " CParse tool=" + p.getMetadata().getTool());
          }
          for (DependencyParse dp : tkz.getDependencyParseList()) {
            System.out.println("tokOffset=" + tokenOffset
                + " DParse tool=" + dp.getMetadata().getTool());
          }
        }

        TokenTagging posG = null;
        if (posToolName != null)
          posG = findByTool(tkz.getTokenTaggingList(), posToolName);
        TokenTagging nerG = null;
        if (nerToolName != null)
          findByTool(tkz.getTokenTaggingList(), nerToolName);
        TokenTagging lemma = null;
        TokenTagging posH = null, nerH = null;
        if (ingestConcreteStanford) {
          lemma = findByPredicate(tkz.getTokenTaggingList(), STANFORD_LEMMA);
          posH = findByPredicate(tkz.getTokenTaggingList(), STANFORD_POS);
          nerH = findByPredicate(tkz.getTokenTaggingList(), STANFORD_NER);
        }

        int n = tkz.getTokenList().getTokenListSize();
        for (int i = 0; i < n; i++) {
          Token token = doc.newToken();
          setToken(token, c, tkz, lemma, posG, posH, nerG, nerH, i, alph);
          if (debug) {
            Log.info("just set token[" + token.getIndex() + "]=" + token.getWordStr());
          }
        }

//        int firstConsTop = doc.consTop;
        Parse parseG = findByTool(tkz.getParseList(), consParseToolName);
        Constituent rootG = addParseConstituents(parseG, consParseToolType, tokenOffset, doc, constituentIndices, alph);
        if (doc.cons_ptb_gold == Document.NONE)
          doc.cons_ptb_gold = rootG.getIndex();
        rootG.setParent(Document.NONE);
        rootG.setLeftSib(prevParseG);
        rootG.setRightSib(Document.NONE);
        if (prevParseG != Document.NONE)
          doc.getConstituent(prevParseG).setRightSib(rootG.getIndex());
        prevParseG = rootG.getIndex();

        if (ingestConcreteStanford) {
          // cparse
          Parse parseH = findByPredicate(tkz.getParseList(), STANFORD_CPARSE);
          Constituent rootH = addParseConstituents(parseH, null, tokenOffset, doc, constituentIndices, alph);
          if (doc.cons_ptb_auto == Document.NONE)
            doc.cons_ptb_auto = rootH.getIndex();
          rootH.setParent(Document.NONE);
          rootH.setLeftSib(prevParseH);
          rootH.setRightSib(Document.NONE);
          if (prevParseH != Document.NONE)
            doc.getConstituent(prevParseH).setRightSib(rootH.getIndex());
          prevParseH = rootH.getIndex();

          // dparse(s)
          // Uses numToks (count for the document) as the root index in these
          // dep trees. This is a requirement due to not being able to bit-pack
          // negative numbers (LabeledDirectedGraph limitation).
          assert numToks > 0;
          dparseBasic.addFromConcrete(
              findByPredicate(tkz.getDependencyParseList(), STANFORD_DPARSE_BASIC),
              tokenOffset, n, numToks, alph);
          dparseColl.addFromConcrete(
              findByPredicate(tkz.getDependencyParseList(), STANFORD_DPARSE_COLL),
              tokenOffset, n, numToks, alph);
          dparseCollCC.addFromConcrete(
              findByPredicate(tkz.getDependencyParseList(), STANFORD_DPARSE_COLL_CC),
              tokenOffset, n, numToks, alph);
        }

        tokenOffset += n;
      }
    }

    if (ingestConcreteStanford) {
      if (debug)
        Log.info("freezing/adding stanford dependency parses, numToks=" + numToks);
      doc.stanfordDepsBasic = dparseBasic.freeze();
      doc.stanfordDepsCollapsed = dparseColl.freeze();
      doc.stanfordDepsCollapsedCC = dparseCollCC.freeze();
    }

    doc.computeDepths();

//    assert test.firstAndLastTokensValid();

    // Add Propbank SRL
    if (this.propbankSrlToolName != null) {
      if (debug)
        Log.info("adding propbank");
      Constituent propbankSrl = addPropbankSrl(c, this.propbankSrlToolName,
          constituentIndices, mapping, alph);
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
      Constituent coref = addCorefConstituents(es, ems, doc, mapping, alph);
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
   * first/last token fields for the {@link Constituent}. If the
   * {@link ConstituentRef} is null, this will insert a dummy constituent
   * corresponding the to {@link TokenRefSequence} and advance the
   * {@link ConstituentItr}, meaning that anything that fields that were set
   * before calling this method will be lost.
   */
  private void setupConstituent(
      ConstituentRef cr,
      TokenRefSequence trs,
      Constituent constituent,
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

      Constituent dummySpan = doc.newConstituent();
      constituent.setOnlyChild(dummySpan.getIndex());
      dummySpan.setParent(constituent.getIndex());
      dummySpan.setFirstToken(s);
      dummySpan.setLastToken(e);
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

  public static <T> T findByPredicate(List<T> items, Predicate<T> p) {
    List<T> possible = new ArrayList<>();
    for (T t : items) {
      if (p.test(t))
        possible.add(t);
    }
    if (possible.size() != 1)
      throw new RuntimeException("not exactly one match: " + possible);
    return possible.get(0);
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
