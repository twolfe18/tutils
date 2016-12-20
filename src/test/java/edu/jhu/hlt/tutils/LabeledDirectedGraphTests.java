package edu.jhu.hlt.tutils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.util.function.IntFunction;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.junit.Before;
import org.junit.Test;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.tutils.Document.ConstituentItr;
import edu.jhu.hlt.tutils.ling.Language;

public class LabeledDirectedGraphTests {

  @Before
  public void setup() {
    LabeledDirectedGraph.DEBUG = true;
  }

  @Test
  public void test0() {
    // I think I have an assumption that the graph is dense,
    // i.e. that there will be no node i if there isn't a node i-1.
    // While this is attainable in general, it makes representing dependency
    // graphs hard where a token index is used for a node id.

    // Resolution: No density assumption
    // I had a bug in computeSplitPoints which only showed up when the last node
    // had no children.

    for (int edge = 0; edge < 10; edge++) {
      LabeledDirectedGraph g = new LabeledDirectedGraph();
      LabeledDirectedGraph.Builder b = g.new Builder();
      b.add(0, 5, edge);
      b.freeze();

      LabeledDirectedGraph.Node p = g.getNode(0);
      LabeledDirectedGraph.Node c = g.getNode(5);

      assertEquals(1, p.numChildren());
      assertEquals(0, p.numParents());
      assertEquals(5, p.getChild(0));
      assertEquals(edge, LabeledDirectedGraph.unpackEdge(p.getChildEdge(0)));

      assertEquals(0, c.numChildren());
      assertEquals(1, c.numParents());
      assertEquals(0, c.getParent(0));
      assertEquals(edge, LabeledDirectedGraph.unpackEdge(c.getParentEdge(0)));
    }

  }
  
  @Test
  public void concreteInteropTest0() throws Exception {

    long start = System.currentTimeMillis();

    File p = new File("/home/travis/code/fnparse/");
    File f = new File(p, "data/parma/ecbplus/ECB+_LREC2014/concrete-parsey-and-stanford/12_9ecbplus.comm");
    Communication comm = new Communication();
    try (InputStream b = FileUtil.getInputStream(f)) {
      comm.read(new TCompactProtocol(new TIOStreamTransport(b)));
    }
    long a = System.currentTimeMillis();

    ConcreteToDocument c2d = new ConcreteToDocument(null, null, null, Language.EN);
    c2d.readParsey();
    ConcreteDocumentMapping cdm = c2d.communication2Document(comm, -1, new MultiAlphabet(), c2d.lang);
    Document d = cdm.getDocument();
    long b = System.currentTimeMillis();
    
    System.out.println(d.parseyMcParseFace.toString(d.getAlphabet()));
    long c = System.currentTimeMillis();
    System.out.println(a - start);
    System.out.println(b - a);
    System.out.println(c - b);
  }
  
  @Test
  public void dfsShowTest() throws Exception {
    File p = new File("/home/travis/code/fnparse/");
    File f = new File(p, "data/parma/ecbplus/ECB+_LREC2014/concrete-parsey-and-stanford/12_9ecbplus.comm");
    Communication comm = new Communication();
    try (InputStream b = FileUtil.getInputStream(f)) {
      comm.read(new TCompactProtocol(new TIOStreamTransport(b)));
    }

    ConcreteToDocument c2d = new ConcreteToDocument(null, null, null, Language.EN);
    c2d.readParsey();
    ConcreteDocumentMapping cdm = c2d.communication2Document(comm, -1, new MultiAlphabet(), c2d.lang);
    Document d = cdm.getDocument();
    
//    System.out.println(d.parseyMcParseFace.toString(d.getAlphabet()));
    
    assertNotNull(d.parseyMcParseFace);
    
    // DFS should work from any node in the first sentence
    assertTrue(d.cons_sentences >= 0);
    IntFunction<String> showNode = i -> d.getWordStr(i) + "@" + i + " nParent=" + d.parseyMcParseFace.getNode(i).numParents();
//    IntFunction<String> showNode = i -> d::getWordStr;
    IntFunction<String> showEdge = d.getAlphabet()::dep;
    int maxSent = 5, sent = 0;
    for (ConstituentItr ci = d.getConstituentItr(d.cons_sentences); ci.isValid(); ci.gotoRightSib()) {
      Log.info("looking at " + ci);
      for (int tok = ci.getFirstToken(); tok <= ci.getLastToken(); tok++) {
        Log.info("dfs from token " + tok);
        d.parseyMcParseFace.dfsShow(tok, showNode, showEdge, "dfs(" + tok + ")", System.out);
      }
      if (++sent >= maxSent)
        break;
    }
  }
}
