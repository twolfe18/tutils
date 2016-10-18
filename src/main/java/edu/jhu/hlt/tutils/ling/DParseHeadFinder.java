package edu.jhu.hlt.tutils.ling;

import java.io.File;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Random;
import java.util.function.Function;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.serialization.TarGzCompactCommunicationSerializer;
import edu.jhu.hlt.tutils.ConcreteToDocument;
import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.Document.ConstituentItr;
import edu.jhu.hlt.tutils.LabeledDirectedGraph;
import edu.jhu.hlt.tutils.MultiAlphabet;

/**
 * Finds the head of a by choosing the token closest to root,
 * breaking ties by preferring nodes to the right.
 */
public class DParseHeadFinder implements HeadFinder {

  public Function<Document, LabeledDirectedGraph> parse = d -> d.stanfordDepsBasic;

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
    assert first <= last;
    LabeledDirectedGraph graph = parse.apply(doc);
    int shallowest = last;
    int depth = graph.getNode(last).computeDepthAssumingTree();
    for (int i = first; i < last; i++) {
      int d = graph.getNode(i).computeDepthAssumingTree();
      if (d < depth) {
        shallowest = i;
        depth = d;
      }
    }
    return shallowest;
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
