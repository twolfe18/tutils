package edu.jhu.hlt.tutils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

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
import edu.jhu.hlt.tutils.Document.Token;
import edu.jhu.hlt.tutils.data.BrownClusters;
import edu.jhu.hlt.tutils.data.WordNetPosUtil;
import edu.jhu.hlt.tutils.ling.Language;
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

  public static final Predicate<AnnotationMetadata> IS_STANFORD = am -> {
    return Arrays.stream(am.getTool().split("\\s+"))
        .anyMatch(s -> s.equalsIgnoreCase("Stanford")
            || s.equalsIgnoreCase("CoreNLP"));
  };
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
    return IS_STANFORD.test(p.getMetadata())
        && p.getMetadata().getTool().toLowerCase().contains("basic");
  };
  public static final Predicate<DependencyParse> STANFORD_DPARSE_COLL_CC = p -> {
    return IS_STANFORD.test(p.getMetadata())
        && p.getMetadata().getTool().toLowerCase().contains("col-cc");
  };
  public static final Predicate<DependencyParse> STANFORD_DPARSE_COLL = p -> {
    return IS_STANFORD.test(p.getMetadata())
        && p.getMetadata().getTool().toLowerCase().contains("col")
        && !STANFORD_DPARSE_COLL_CC.test(p);
  };
  public static final Predicate<EntitySet> STANFORD_COREF = es -> {
    return IS_STANFORD.test(es.getMetadata());
  };

  // See issue #??? in concrete on gitlab
  public static boolean REWRITE_CONSTITUENT_IDS_TO_INDICES = true;

  public boolean debug = false;
  public boolean debug_cons = false;
  public boolean debug_deps = false;
  public boolean debug_propbank = false;
  public boolean log_cons_id_conversion = true;   // issue related to concrete Constituent id vs index
  public boolean log_no_entities = true;

  /** See {@link ConcreteDocumentMapping}, if false only keeps a {@link UUID} */
  public boolean keepConcrete = true;

  /** Only supports a single POS {@link TokenTagging} for now */
  public Predicate<TokenTagging> posToolGold;
  public Predicate<TokenTagging> posToolAuto;

  /** Only supports {@link TokenTagging} style NER for now */
  public Predicate<TokenTagging> nerToolGold;
  public Predicate<TokenTagging> nerToolAuto;

  public Predicate<TokenTagging> lemmaTool;

  /** Only supports a single {@link Parse} for now */
  public String cparseToolGold;
  public String cparseToolAuto;

  public Predicate<DependencyParse> dparseBasicTool;   // stanfordDepsBasic
  public Predicate<DependencyParse> dparseColTool;     // stanfordDepsCollapsed
  public Predicate<DependencyParse> dparseColCCTool;   // stanfordDepsCollapsedCC
  public Predicate<DependencyParse> dparseUDTool;      // universalDependencies

  /**
   * Assumes Propbank SRL is stored as a {@link SituationMention} using
   * {@link ConstituentRef}. See the documentation in {@link Document} for how
   * they are added to the {@link Document}.
   */
  public String propbankToolGold;      // cons_propbank_gold
  public String propbankToolAuto;      // cons_propbank_auto

  /**
   * Looks for an {@link EntitySet} matching this tool name and adds them to
   * {@link Document#cons_coref_gold}.
   */
  public String corefToolGold;     // cons_coref_gold
  public String corefToolAuto;     // cons_coref_auto

  /**
   * These operate independently of the coref tools. Even if I wanted to re-use
   * the mention set (i.e. it's the same in concrete), this is not possible
   * because the sibling pointers in an entity constituent must terminate at
   * the entity boundary; so we can't just re-use the linked list with
   *   doc.cons_coref_mention_gold = leftChild(doc.cons_coref_gold)
   *
   * If you want to be pedantic, we could have all the mentions be in the same
   * list and use:
   *    for (mention = entity.leftChild; mention.parent == entity.index; ...)
   * but I know that is too tricky and would bite me...
   */
  public String corefMentionToolGold;   // cons_coref_mention_gold
  public String corefMentionToolAuto;   // cons_coref_mention_auto

  protected BrownClusters bc256, bc1000;
  protected IRAMDictionary wnDict;

  public Language lang;

  public void clearTools() {
    lemmaTool = null;
    posToolGold = null;
    posToolAuto = null;
    nerToolGold = null;
    nerToolAuto = null;
    cparseToolGold = null;
    cparseToolAuto = null;
    dparseBasicTool = null;
    dparseColTool = null;
    dparseColCCTool = null;
    dparseUDTool = null;
    propbankToolGold = null;
    propbankToolAuto = null;
    corefToolGold = null;
    corefToolAuto = null;
    corefMentionToolGold = null;
    corefMentionToolAuto = null;
  }

  /** Warning: This is an ad-hoc utility method, don't rely on this unless you're Travis */
  public void readConll() {
    corefToolGold = "conll-2011 coref";
    cparseToolGold = "conll-2011 parse";
    posToolGold = tt -> "conll-2011 pos".equalsIgnoreCase(tt.getMetadata().getTool());
    nerToolGold = tt -> "conll-2011 ner".equalsIgnoreCase(tt.getMetadata().getTool());
  }

  /** Warning: This is an ad-hoc utility method, don't rely on this unless you're Travis */
  public void readPropbank() {
    readConll();
    propbankToolGold = "conll-2011 SRL";
    corefToolGold = null;
  }

  /** Warning: This is an ad-hoc utility method, don't rely on this unless you're Travis */
  public void readConcreteStanford() {
    corefToolAuto = "Stanford Coref";
    cparseToolAuto = "Stanford CoreNLP";
    dparseBasicTool = STANFORD_DPARSE_BASIC;
    dparseColTool = STANFORD_DPARSE_COLL;
    dparseColCCTool = STANFORD_DPARSE_COLL_CC;
    posToolAuto = STANFORD_POS;
    nerToolAuto = STANFORD_NER;
    lemmaTool = STANFORD_LEMMA;
  }

  public void readTacKbp2015SituationsAsSrl() {
    propbankToolGold = "TAC KBP 2015 Cold Start Slot Filling";
  }

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

  private static void setTokenHelper(IntConsumer tokSet, ToIntFunction<String> alph, TokenTagging tags, int i, Tokenization toks) {
    int n = toks.getTokenList().getTokenListSize();
    if (tags == null)
      return;
    if (tags.getTaggedTokenListSize() == 0) {
//      if (i == 0) {   // Only print this once per sentence
//        Log.warn("zero length TokenTagging! " + tags.getMetadata()
//            + " " + tags.getTaggingType() + " in " + toks.getUuid());
//      }
      return;
    }
    if (tags.getTaggedTokenListSize() != n) {
      Log.warn("inappropriate size: " + tags.getTaggedTokenListSize() + " vs " + n + " in " + toks.getUuid());
      System.err.println(toks.getMetadata());
      for (edu.jhu.hlt.concrete.Token t : toks.getTokenList().getTokenList())
        System.err.println(t);
      System.err.println(tags.getMetadata());
      for (edu.jhu.hlt.concrete.TaggedToken t : tags.getTaggedTokenList())
        System.err.println(t);
      throw new RuntimeException("i=" + i);
    }
    String s = tags.getTaggedTokenList().get(i).getTag();
    int v = alph.applyAsInt(s);
    tokSet.accept(v);
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

    setTokenHelper(token::setLemma, alph::word, lemma, tokenIdx, t);
    setTokenHelper(token::setPosG, alph::pos, posG, tokenIdx, t);
    setTokenHelper(token::setPosH, alph::pos, posH, tokenIdx, t);
    setTokenHelper(token::setNerG, alph::ner, nerG, tokenIdx, t);
    setTokenHelper(token::setNerH, alph::ner, nerH, tokenIdx, t);

    if (lang.isRoman()) {
      token.setWordNocase(alph.word(w.toLowerCase()));
      token.setShape(alph.shape(WordShape.wordShape(w)));
    }

    // WordNet synset id
    TokenTagging pos = posG != null ? posG : posH;
    if (wnDict != null && pos != null && lang == Language.EN) {
      assert pos.getTaggedTokenListSize() == t.getTokenList().getTokenListSize();
      String p = pos.getTaggedTokenList().get(tokenIdx).getTag();
      token.setWnSynset(alph.wnSynset(MultiAlphabet.UNKNOWN));
      edu.mit.jwi.item.POS wnPos = WordNetPosUtil.ptb2wordNet(p);
      if (wnPos != null) {
        String wd = token.getLemma() >= 0 ? alph.word(token.getLemma()) : token.getWordStr();
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
   * @returns a constituent index of the first {@link EntityMention} given
   * (which may be Document.NONE if the given set is empty).
   *
   * If parent is a valid constituent, this will update the firstChild and lastChild
   * pointers in congruence with the added children.
   */
  public static int addEntityMentions(
      List<EntityMention> mentions,
      int parentConstituent,
      Document doc,
      ConcreteDocumentMapping mapping) {

    if (mentions.size() == 0) {
      Log.warn("no mentions!");
      return Document.NONE;
    }

    if (parentConstituent >= 0)
      doc.getConstituent(parentConstituent).setOnlyChild(Document.NONE);

    MultiAlphabet alph = doc.getAlphabet();
    int ret = -1;
    int prev = Document.NONE;
    for (EntityMention em : mentions) {
      if (em.getChildMentionIdListSize() > 0)
        throw new RuntimeException("recursive EntityMentions are not supported");

      Constituent c = doc.newConstituent();

      // Update parent left/right child pointers
      if (ret < 0) {
        ret = c.getIndex();
        if (parentConstituent >= 0) {
          assert doc.getConstituent(parentConstituent).getLeftChild() < 0;
          doc.getConstituent(parentConstituent).setLeftChild(c.getIndex());
        }
      }
      if (parentConstituent >= 0)
        doc.getConstituent(parentConstituent).setRightChild(c.getIndex());

      // Update Document.Constituent <=> EntityMention mapping
      mapping.put(c, em.getUuid());

      TokenRefSequence trs = em.getTokens();
      List<Integer> tokens = trs.getTokenIndexList();
      if (!ascending(tokens))
        throw new RuntimeException("can't handle split sequences");
      // Convert from sentence-relative to document-relative
      Document.Constituent sentence = mapping.get(trs.getTokenizationId());
      int first = sentence.getFirstToken() + tokens.get(0);
      int last = sentence.getFirstToken() + tokens.get(tokens.size() - 1);

      c.setLhs(alph.ner(em.getEntityType()));
      c.setParent(parentConstituent);
      c.setOnlyChild(Document.NONE);
      c.setFirstToken(first);
      c.setLastToken(last);
      c.setLeftSib(prev);
      if (prev >= 0)
        doc.getConstituent(prev).setRightSib(c.getIndex());
      prev = c.getIndex();
    }
    return ret;
  }

  /**
   * Returns the coreference clustering represented by coref as constituents in
   * the document (see {@link Document#cons_coref_gold} for documentation on
   * the representation).
   * @param mapping may be null
   * @return the first Constituent (which is a pointer to an {@link Entity} and
   * also a linked list representing a {@link EntitySet}) added or Document.NONE
   * if the coref/entity set is empty.
   */
  public int addCorefConstituents(
      List<Entity> entities,
      String toolName,
      EntityMentionSet mentions,
      Document doc,
      ConcreteDocumentMapping mapping) {

    if (entities.isEmpty()) {
      if (log_no_entities)
        Log.warn("No entities in \"" + toolName + "\"");
      return Document.NONE;
    }

    Map<UUID, EntityMention> ems = indexByUUID(mentions.getMentionList());

    MultiAlphabet alph = doc.getAlphabet();
    int ret = -1;
    int prevEnt = Document.NONE;
    for (Entity ent : entities) {          // LOOP over Entities

      if (ent.getMentionIdListSize() == 0)
        throw new RuntimeException("Empty Entity?");

      Constituent entC = doc.newConstituent();

      if (ret < 0)
        ret = entC.getIndex();

      entC.setParent(Document.NONE);
      entC.setLhs(alph.ner(ent.getType()));
      entC.setLeftSib(prevEnt);
      entC.setRightSib(Document.NONE);
      if (prevEnt != Document.NONE)
        doc.getConstituent(prevEnt).setRightSib(entC.getIndex());
      prevEnt = entC.getIndex();

      // Update mapping: Entity.UUID
      mapping.put(entC, ent.getUuid());

      // Add mentions (this handles entC's children)
      List<EntityMention> ms = join(ent.getMentionIdList(), ems);
      addEntityMentions(ms, entC.getIndex(), doc, mapping);
    }
    return ret;
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

  public static void rewriteConstituentIdsToMatchIndex(Parse p) {
    int n = p.getConstituentListSize();
    int[] id2index = new int[n];
    for (int i = 0; i < n; i++)
      id2index[p.getConstituentList().get(i).getId()] = i;
    for (edu.jhu.hlt.concrete.Constituent c : p.getConstituentList()) {
      c.setId(id2index[c.getId()]);
      List<Integer> children = new ArrayList<>();
      for (int child : c.getChildList())
        children.add(id2index[child]);
      c.setChildList(children);
    }
  }

  public static boolean wordsAreInTheTree(Parse p) {
    if (STANFORD_CPARSE.test(p))
      return true;
    if ("conll-2011 parse".equalsIgnoreCase(p.getMetadata().getTool()))
      return false;
    throw new RuntimeException("unknown for " + p.getMetadata());
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
      int tokenOffset,
      Document doc,
      Map<ConstituentRef, Integer> constituentIndices,
      MultiAlphabet alph) {

    if (debug_cons)
      Log.info("converting " + p);

    // Sanity check: Constituents shouldn't really have ids, they should work
    // only on indices.
    if (REWRITE_CONSTITUENT_IDS_TO_INDICES) {
      if (log_cons_id_conversion)
        Log.info("re-writing constituent ids as indices");
      rewriteConstituentIdsToMatchIndex(p);
    } else {
      for (int i = 0; i < p.getConstituentListSize(); i++)
        if (i != p.getConstituentList().get(i).getId())
          throw new RuntimeException("can't handle constituent id != index");
    }

    BitSet isNotChild = new BitSet();   // constituent indices
    int adds = 0;

    /*
     * TODO If words are in the tree, then do not add leave Constituents.
     * POS tags should stay in the tree, as they could in principle differ
     * from posH.
     */
    boolean hasWords = wordsAreInTheTree(p);

    // Build the constituents and mapping between them
    int nCons = p.getConstituentListSize();
    int[] ccon2dcon = new int[nCons];
    Arrays.fill(ccon2dcon, -1);
    int pl = 0; // index into parse.constituentList
    for (edu.jhu.hlt.concrete.Constituent ccon : p.getConstituentList()) {
      if (debug_cons)
        Log.info("ccon=" + ccon);

      // Skip over leaf/word constituents
      if (hasWords && ccon.getChildListSize() == 0)
        continue;

      String t = "???";
      if (!ccon.isSetTag())
        Log.warn("no tag for " + ccon + " in parse=" + p.getMetadata());
      else
        t = ccon.getTag();
      if (t.length() >= 7)
        Log.warn("long pos tag, no? " + ccon.getTag());
      if (!t.toUpperCase().equals(t)) {
        // Words should never be tags! There should be a small set of CFG/POS
        // tags (which are all upper case, sometimes with dashes)!
        Log.warn("did you put words into the pos tags? " + ccon + " parser=" + p.getMetadata());
      }
      assert ccon.isSetStart() == ccon.isSetEnding();

      adds++;
      Constituent cons = doc.newConstituent();
      isNotChild.set(cons.getIndex(), true);
      cons.setLhs(alph.cfg(t));
      cons.setParent(Document.NONE);      // Will be over-written later
      cons.setOnlyChild(Document.NONE);   // Will be over-written later

      // Set first and last token if available
      if (ccon.isSetStart()) {
        if (debug_cons)
          Log.info("setting lastToken[" + cons.getIndex() + "]=" + (tokenOffset + ccon.getEnding() - 1));
        cons.setFirstToken(tokenOffset + ccon.getStart());
        cons.setLastToken(tokenOffset + ccon.getEnding() - 1);
      }

      // Set concrete -> tutils mapping
      assert cons.getIndex() >= 0;
      if (ccon2dcon[ccon.getId()] >= 0)
        throw new RuntimeException("loopy parse? " + p);
      ccon2dcon[ccon.getId()] = cons.getIndex();

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

        // child must have been a leaf
        if (hasWords && childIdx < 0) {
          assert p.getConstituentList().get(child).getChildListSize() == 0;
          continue;
        }

        assert parentIdx != childIdx;
        isNotChild.set(childIdx, false);
        Document.Constituent c = doc.getConstituent(childIdx);
        c.setParent(parentIdx);
        c.setLeftSib(prevChildIdx);
        if (prevChildIdx >= 0)
          doc.getConstituent(prevChildIdx).setRightSib(childIdx);
        if (parent.getLeftChild() < 0) {
          if (debug_cons) {
            Log.info("setting parent=" + parentIdx + ".firstChild=" + childIdx
                + " " + parent.show(alph) + " -> " + c.show(alph));
          }
          parent.setLeftChild(childIdx);
        }
        if (debug_cons) {
          Log.info("setting parent=" + parentIdx + ".lastChild=" + childIdx
              + " " + parent.show(alph) + " -> " + c.show(alph));
        }
        parent.setRightChild(childIdx);
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

  /**
   * @return the first Constituent which is a linked list of {@link SituationMention}s
   * or Document.NONE if there are predicates in the SRL labels
   * or Document.UNINITIALIZED if the {@link SituationMentionSet} cannot be found by the given tool name.
   */
  public int addPropbankSrl(
      Communication c,
      String situationMentionSetToolName,
      Map<ConstituentRef, Integer> constituentIndices,  // TODO fold into mapping
      ConcreteDocumentMapping mapping,
      MultiAlphabet alph) {

    Document doc = mapping.getDocument();

    if (debug_propbank) {
      Log.info("adding propbank for " + doc.getId() + " sms="
        + c.getSituationMentionSetList().stream().map(SituationMentionSet::getMetadata).collect(Collectors.toList()));
    }

    SituationMentionSet sms = findByTool(c.getSituationMentionSetList(), situationMentionSetToolName);
    if (sms == null) {
      System.err.println("failed to find SituationMentionSet by tool: " + situationMentionSetToolName
        + " among: " + c.getSituationMentionSetList().stream().map(SituationMentionSet::getMetadata).collect(Collectors.toList()));
      return Document.UNINITIALIZED;
    }

    if (debug_propbank)
      Log.info("numSituations=" + sms.getMentionListSize());

    int first = Document.NONE;
    int prevSit = Document.NONE;
    for (SituationMention sm : sms.getMentionList()) {
      if (sm.getTokens() == null && sm.getConstituent() == null)
        throw new RuntimeException("SituationMention does not provide any target info: " + sm);

      // Make a situation node which is the parent of all the pred/args
      Constituent sit = doc.newConstituent();
      sit.setLhs(alph.srl("propbank"));
      sit.setLeftSib(prevSit);
      sit.setRightSib(Document.NONE); // will be over-written
      sit.setParent(Document.NONE);
      if (prevSit != Document.NONE)
        doc.getConstituent(prevSit).setRightSib(sit.getIndex());
      if (first == Document.NONE) {
        first = sit.getIndex();
        assert first >= 0;
      }
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
      if (debug_propbank) {
        Log.info("adding Situation text=\"" + sm.getText());
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

        if (debug_propbank) {
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

      if (debug_propbank) {
        Log.info("sit.firstToken=" + sit.getFirstToken()
            + " sit.lastToken=" + sit.getLastToken()
            + " numArgs=" + numArgs);
      }
    }
    return first;
  }

  /**
   * Sets {@link Document#cons_section} and {@link Document#cons_paragraph} to
   * reflect the {@link Communication}'s {@link Section}s.
   */
  public void addParagraphs(Communication c, Document doc) {
    int paragraphStart = 0;
    int sectionIdx = 0;
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
      constituent.setLhs(sectionIdx);
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
      sectionIdx++;
    }
    doc.cons_section = doc.cons_paragraph;
  }

  /**
   * Convert a {@link Communication} into a {@link Document}.
   */
  public ConcreteDocumentMapping communication2Document(
      Communication c, int docIndex, MultiAlphabet alph, Language language) {

    if (debug) {
      System.out.println("keepConcrete=" + this.keepConcrete);
      System.out.println("cparseToolAuto=" + this.cparseToolAuto);
      System.out.println("cparseToolGold=" + this.cparseToolGold);
      System.out.println("propbankToolAuto" + this.propbankToolAuto);
      System.out.println("propbankToolGold" + this.propbankToolGold);
      System.out.println("corefMentionToolAuto=" + this.corefMentionToolAuto);
      System.out.println("corefMentionToolGold=" + this.corefMentionToolGold);
      System.out.println("corefToolAuto=" + this.corefToolAuto);
      System.out.println("corefToolGold=" + this.corefToolGold);
    }

    Document doc = new Document(c.getId(), docIndex, alph);
    doc.language = language;
    doc.allowExpansion(true);
    ConcreteDocumentMapping mapping = keepConcrete
        ? new ConcreteDocumentMapping(c, doc)
        : new ConcreteDocumentMapping(c.getUuid(), doc);
    DocumentTester tester = new DocumentTester(doc, true);

    // Count the number of tokens and add the sentence sectioning
    int sentenceIdx = 0;
    int numToks = 0;
    int prevSent = Document.NONE;
    // Note that this info is already captured in the LHS of the sentence segmentation/cparse
    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {

        Tokenization tkz = ss.getTokenization();
        int first = numToks;
        numToks += tkz.getTokenList().getTokenListSize();
        int last = numToks - 1;

        Constituent constituent = doc.newConstituent();
        if (doc.cons_sentences == Document.NONE)
          doc.cons_sentences = constituent.getIndex();

        // Update mapping: Document.Constituent <=> Tokenization.UUID
        mapping.put(doc.getConstituent(constituent.getIndex()), tkz.getUuid());

        constituent.setLhs(sentenceIdx);
        constituent.setFirstToken(first);
        constituent.setLastToken(last);
        constituent.setOnlyChild(Document.NONE);
        constituent.setParent(Document.NONE);
        constituent.setRightSib(Document.NONE);
        constituent.setLeftSib(prevSent);
        if (prevSent != Document.NONE)
          doc.getConstituent(prevSent).setRightSib(constituent.getIndex());
        prevSent = constituent.getIndex();

        sentenceIdx++;
      }
    }

    // Add paragraph/section segmentation as linked list of constituents
    addParagraphs(c, doc);

    // Build the Document
    int tokenOffset = 0;
    Map<ConstituentRef, Integer> constituentIndices = new HashMap<>();
    int prevParseG = Document.NONE;
    int prevParseH = Document.NONE;
    LabeledDirectedGraph.Builder dparseBasic = new LabeledDirectedGraph().new Builder();
    LabeledDirectedGraph.Builder dparseColl = new LabeledDirectedGraph().new Builder();
    LabeledDirectedGraph.Builder dparseCollCC = new LabeledDirectedGraph().new Builder();
    LabeledDirectedGraph.Builder dparseUD = new LabeledDirectedGraph().new Builder();

    // We use numToks (in the entire document) as the root for all dparses
    assert numToks > 0;
    final int dParseRoot = numToks;

    for (Section s : c.getSectionList()) {
      for (Sentence ss : s.getSentenceList()) {
        Tokenization tkz = ss.getTokenization();

        if (debug) {
          System.out.println();
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

        // Add tokens and token taggings
        TokenTagging posG = null;
        if (posToolGold != null)
          posG = findByPredicate(tkz.getTokenTaggingList(), posToolGold);
        TokenTagging posH = null;
        if (posToolAuto != null)
          posH = findByPredicate(tkz.getTokenTaggingList(), posToolAuto);

        TokenTagging nerG = null;
        if (nerToolGold != null)
          nerG = findByPredicate(tkz.getTokenTaggingList(), nerToolGold);
        TokenTagging nerH = null;
        if (nerToolGold != null)
          nerH = findByPredicate(tkz.getTokenTaggingList(), nerToolAuto);

        TokenTagging lemma = null;
        if (lemmaTool != null)
          lemma = findByPredicate(tkz.getTokenTaggingList(), lemmaTool);

        int n = tkz.getTokenList().getTokenListSize();
        for (int i = 0; i < n; i++) {
          Token token = doc.newToken();
          setToken(token, c, tkz, lemma, posG, posH, nerG, nerH, i, alph);
          if (debug) {
            Log.info("just set token[" + token.getIndex() + "]=" + token.getWordStr());
          }
        }

        // Add constituency parses
        if (cparseToolGold != null) {
          Parse parseG = findByTool(tkz.getParseList(), cparseToolGold);
          Constituent rootG = addParseConstituents(parseG, tokenOffset, doc, constituentIndices, alph);
          if (doc.cons_ptb_gold == Document.NONE)
            doc.cons_ptb_gold = rootG.getIndex();
          rootG.setParent(Document.NONE);
          rootG.setLeftSib(prevParseG);
          rootG.setRightSib(Document.NONE);
          if (prevParseG != Document.NONE)
            doc.getConstituent(prevParseG).setRightSib(rootG.getIndex());
          prevParseG = rootG.getIndex();
        }
        if (cparseToolAuto != null) {
          Parse parseH = findByTool(tkz.getParseList(), cparseToolAuto);
          Constituent rootH = addParseConstituents(parseH, tokenOffset, doc, constituentIndices, alph);
          if (doc.cons_ptb_auto == Document.NONE)
            doc.cons_ptb_auto = rootH.getIndex();
          rootH.setParent(Document.NONE);
          rootH.setLeftSib(prevParseH);
          rootH.setRightSib(Document.NONE);
          if (prevParseH != Document.NONE)
            doc.getConstituent(prevParseH).setRightSib(rootH.getIndex());
          prevParseH = rootH.getIndex();
        }

        // Add dependency parses
        // Uses numToks (count for the document) as the root index in these
        // dep trees. This is a requirement due to not being able to bit-pack
        // negative numbers (LabeledDirectedGraph limitation).
        dparseBasic.addFromConcrete(
            findByPredicate(tkz.getDependencyParseList(), this.dparseBasicTool),
            tokenOffset, n, dParseRoot, alph);
        dparseColl.addFromConcrete(
            findByPredicate(tkz.getDependencyParseList(), this.dparseColTool),
            tokenOffset, n, dParseRoot, alph);
        dparseCollCC.addFromConcrete(
            findByPredicate(tkz.getDependencyParseList(), this.dparseColCCTool),
            tokenOffset, n, dParseRoot, alph);
        dparseUD.addFromConcrete(
            findByPredicate(tkz.getDependencyParseList(), this.dparseUDTool),
            tokenOffset, n, dParseRoot, alph);

        tokenOffset += n;
      }
    }

    if (dparseBasic.numEdges() > 0)
      doc.stanfordDepsBasic = dparseBasic.freeze();
    if (dparseColl.numEdges() > 0)
      doc.stanfordDepsCollapsed = dparseColl.freeze();
    if (dparseCollCC.numEdges() > 0)
      doc.stanfordDepsCollapsedCC = dparseCollCC.freeze();
    if (dparseUD.numEdges() > 0)
      doc.universalDependencies = dparseUD.freeze();

    doc.computeDepths();

    assert tester.firstAndLastTokensValid();

    // Add Propbank SRL
    if (propbankToolGold != null) {
      if (debug_propbank)
        Log.info("adding propbankToolGold=" + propbankToolGold);
      doc.cons_propbank_gold = addPropbankSrl(c, propbankToolGold, constituentIndices, mapping, alph);
      if (doc.cons_propbank_gold == Document.UNINITIALIZED)
        throw new RuntimeException("couldn't find gold propbank: " + propbankToolGold);
    }
    if (propbankToolAuto != null) {
      if (debug_propbank)
        Log.info("adding propbankToolAuto=" + propbankToolAuto);
      doc.cons_propbank_auto = addPropbankSrl(c, propbankToolAuto, constituentIndices, mapping, alph);
      if (doc.cons_propbank_auto == Document.UNINITIALIZED)
        throw new RuntimeException("couldn't find auto propbank: " + propbankToolAuto);
    }

    // Add coref
    if (corefToolGold != null) {
      if (debug)
        Log.info("adding corefToolGold=" + corefToolGold);
      EntitySet es = findByTool(c.getEntitySetList(), corefToolGold);
      if (!es.isSetMentionSetId())
        throw new RuntimeException("implement EntityMentionSet finder for when EntitySet doesn't provide one");
      EntityMentionSet ems = findByUUID(c.getEntityMentionSetList(), es.getMentionSetId());
      doc.cons_coref_gold = addCorefConstituents(es.getEntityList(), corefToolGold, ems, doc, mapping);
    }
    if (corefToolAuto != null) {
      if (debug)
        Log.info("adding corefToolAuto=" + corefToolAuto);
      EntitySet es = findByTool(c.getEntitySetList(), corefToolAuto);
      if (!es.isSetMentionSetId())
        throw new RuntimeException("implement EntityMentionSet finder for when EntitySet doesn't provide one");
      EntityMentionSet ems = findByUUID(c.getEntityMentionSetList(), es.getMentionSetId());
      doc.cons_coref_auto = addCorefConstituents(es.getEntityList(), corefToolAuto, ems, doc, mapping);
    }

    // Add coref mentions
    if (corefMentionToolGold != null) {
      if (debug)
        Log.info("adding corefMentionToolGold=" + corefMentionToolGold);
      EntityMentionSet ems = findByTool(c.getEntityMentionSetList(), corefMentionToolGold);
      doc.cons_coref_mention_gold = addEntityMentions(ems.getMentionList(), Document.NONE, doc, mapping);
    }
    if (corefMentionToolAuto != null) {
      if (debug)
        Log.info("adding corefMentionToolAuto=" + corefMentionToolAuto);
      EntityMentionSet ems = findByTool(c.getEntityMentionSetList(), corefMentionToolAuto);
      doc.cons_coref_mention_auto = addEntityMentions(ems.getMentionList(), Document.NONE, doc, mapping);
    }

    // Compute BrownClusters
    if (bc256 != null || bc1000 != null)
      BrownClusters.setClusters(doc, bc256, bc1000);

    assert tester.checkWords();

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
    if (p == null)
      return null;
    List<T> possible = new ArrayList<>();
    for (T t : items) {
      if (p.test(t))
        possible.add(t);
    }
    if (possible.size() != 1) {
      throw new RuntimeException("not exactly one match:\n"
          + StringUtils.join("\n", possible));
    }
//    if (possible.size() == 0)
//      return null;
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
