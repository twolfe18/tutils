package edu.jhu.hlt.tutils.ling;

import java.io.File;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Random;
import java.util.function.Function;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.serialization.TarGzCompactCommunicationSerializer;
import edu.jhu.hlt.tutils.ConcreteToDocument;
import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.Document.ConstituentItr;
import edu.jhu.hlt.tutils.LabeledDirectedGraph;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.LabeledDirectedGraph.Node;
import edu.jhu.hlt.tutils.MultiAlphabet;

/**
 * Finds the head of a by choosing the token closest to root,
 * breaking ties by preferring nodes to the right.
 * 
 * NOTE: This does not work well on Stanford dependencies!
 * The edgesToSkip is a bit of a hack, which lets you skip, e.g. possesive
 * words as "not heads". This can run into problems where backoff/backtracking
 * is needed, e.g. if poss and possesive are both skipped, then the span
 * [Japan 's] ... has no head, even though we'd like to say something like,
 * "if there's no head thats not poss or possesive, allow poss and try again".
 */
public class DParseHeadFinder implements HeadFinder {

  public Function<Document, LabeledDirectedGraph> parse = d -> d.stanfordDepsBasic;
  private BitSet edgesToSkip;
  
  /**
   * When choosing a head, ignore all edges of the form (gov, dep, deprel)
   * where deprel == edgeType. You might want to use deprel = punc for example.
   */
  public void ignoreEdge(int edgeType) {
    if (edgesToSkip == null)
      edgesToSkip = new BitSet();
    edgesToSkip.set(edgeType);
  }

  /** Returns the parse that this head finder uses */
  public LabeledDirectedGraph getParse(Document d) {
    return parse.apply(d);
  }

  public DParseHeadFinder useParse(Function<Document, LabeledDirectedGraph> parse) {
    this.parse = parse;
    return this;
  }

  @Override
  public int head(Document doc, int first, int last) {
    LabeledDirectedGraph graph = parse.apply(doc);
    return head(graph, first, last);
  }

  public int head(LabeledDirectedGraph graph, int first, int last) {
    return head(graph, first, last, false);
  }
  public int head(LabeledDirectedGraph graph, int first, int last, boolean disableEdgeSkipping) {
    assert first <= last;
    if (first == last)
      return first;
    int shallowest = -1;
    int depth = -1;
    for (int i = first; i <= last; i++) {
      Node n = graph.getNode(i);
      if (n.numParents() + n.numChildren() == 0)
        continue;
      assert n.numParents() <= 1;
      if (!disableEdgeSkipping && edgesToSkip != null) {
        int edge = n.getParentEdgeLabel(0);
        if (edgesToSkip.get(edge))
          continue;
      }
      int d;
      try {
        d = n.computeDepthAssumingTree();
      } catch (RuntimeException e) {
        Log.warn(e.getMessage());
        return -1;
      }
      if (d < depth || shallowest < 0 || true) {
        shallowest = i;
        depth = d;
      }
    }
    return shallowest;
  }
  
  /**
   * Returns a multi-word nn-compound tokens.
   * E.g. "the famous Johns Hopkins University" => "Johns Hopkins University"
   *  and "the United States of America" => "United States"
   * @param nnEdgeType should be the edge type for the "nn" edge
   */
  public int[] headWithNn(LabeledDirectedGraph graph, int first, int last, int nnEdgeType) {
    assert first >= 0;
    assert first <= last;

    // Find the head
    int h = head(graph, first, last, false);
    if (h < first || h > last) {
      // Try disabling edge skipping,
      // e.g. [Japan 's] is [poss possesive], which leads to no head
      h = head(graph, first, last, true);
    }
    if (h < first || h > last)
      return null;
    
    // Find any nn edges out of the head
    int left = h;
    while (left > first) {
      Node n = graph.getNode(left-1);
      if (n.numParents() == 1 && n.getParentEdgeLabel(0) == nnEdgeType)
        left--;
      else
        break;
    }
    int right = h;
    while (right < last) {
      Node n = graph.getNode(right+1);
      if (n.numParents() == 1 && n.getParentEdgeLabel(0) == nnEdgeType)
        right++;
      else
        break;
    }

    int w = (right - left) + 1;
    assert w > 0;
    int[] toks = new int[w];
    for (int i = 0; i < toks.length; i++) {
      toks[i] = left + i;
      assert toks[i] >= 0;
      assert toks[i] >= first;
      assert toks[i] <= last;
    }
    return toks;
  }

  public static void main(String[] args) throws Exception {
//    File home = new File("/home/travis/code/coref/data/processed/");
//    File concreteFile = new File(home, "conll-2011-train.concrete.tgz");
//    MultiAlphabet alph = MultiAlphabet.deserialize(new File(home, "alphabet-en.txt.gz"));
    Language lang = Language.EN;
    File concreteFile = new File("/home/travis/code/schema-challenge/features/framenet/data/train.concrete.tgz");
    MultiAlphabet alph = new MultiAlphabet();
    DParseHeadFinder hf = new DParseHeadFinder();
    ConcreteToDocument cio = new ConcreteToDocument(null, null, null, lang);
    cio.debug = true;
    cio.clearTools();
    cio.dparseBasicTool = ConcreteToDocument.STANFORD_DPARSE_BASIC;
    Random rand = new Random(9001);
    TarGzCompactCommunicationSerializer ts = new TarGzCompactCommunicationSerializer();
    Iterator<Communication> itr = ts.fromTarGz(Files.newInputStream(concreteFile.toPath()));
    while (itr.hasNext()) {
      Communication comm = itr.next();
      Document doc = cio.communication2Document(comm, 0, alph, lang).getDocument();

      for (ConstituentItr c = doc.getConstituentItr(doc.cons_sentences);
          c.isValid(); c.gotoRightSib()) {

        // Choose a random span
        System.out.println("width=" + c.getWidth() + " firstToken=" + c.getFirstToken());
        int f = rand.nextInt(c.getWidth()) + c.getFirstToken();
        int l = rand.nextInt(c.getWidth()) + c.getFirstToken();
        if (f > l) {
          int t = f; f = l; l = t;
        }
        System.out.println("f=" + f + " l=" + l);

        int h = hf.head(doc, f, l);
        LabeledDirectedGraph deps = hf.getParse(doc);

        for (int i = c.getFirstToken(); i <= c.getLastToken(); i++) {
          LabeledDirectedGraph.Node n = deps.getNode(i);
          String parent = "NONE";
          if (n.numParents() > 0) {
            assert n.numParents() == 1;
            int p = n.getParent(0);
            if (p < doc.numTokens())
              parent = doc.getWordStr(p);
          }
          System.out.print(i + "\t" + doc.getWordStr(i) + " <- " + parent + "\t");
          if (i == f)
            System.out.print("  FIRST");
          if (i == h)
            System.out.print("  HEAD");
          if (i == l)
            System.out.print("  LAST");
          System.out.println();
        }
      }


//      for (Section section : comm.getSectionList()) {
//        for (Sentence sentence : section.getSentenceList()) {
//          Tokenization tokenization = sentence.getTokenization();
//          int n = tokenization.getTokenList().getTokenListSize();
//          IntFunction<String> getWord = i -> {
//            if (i == n)
//              return "ROOT";
//            return tokenization.getTokenList().getTokenList().get(i).getText();
//          };
//          for (DependencyParse p : tokenization.getDependencyParseList()) {
//            LabeledDirectedGraph g = LabeledDirectedGraph.fromConcrete(p, n, alph);
//            //            System.out.println(tokenization.getTokenList());
//            for (int i = 0; i < n; i++)
//              System.out.println(i + "\t" + getWord.apply(i));
//            System.out.print(g.toString(alph));
//            for (int i = 0; i < g.getNumEdges(); i++) {
//              long e = g.getEdge(i);
//              String gov = getWord.apply(LabeledDirectedGraph.unpackGov(e));
//              String dep = getWord.apply(LabeledDirectedGraph.unpackDep(e));
//              String rel = alph.dep(LabeledDirectedGraph.unpackEdge(e));
//              System.out.println(gov + "\t" + rel + "\t" + dep);
//            }
//
//            // Choose a random span
//            int f = rand.nextInt(n);
//            int l = rand.nextInt(n);
//            if (f > l) {
//              int t = f; f = l; l = t;
//            }
//            System.out.println("f=" + f + " l=" + l);
//
//            // Find the head
//            int head = hf.head(doc, f, l);
//            System.out.println("head=" + head);
//          }
//        }
//      }

    }
  }

}
