package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

public class ConllxToDocument {
  
  private MultiAlphabet alph;
  public boolean showReads = false;
  
  /**
   * Read dependency parse tree in as a "constituency" parse where
   * first and last tokens are both the token/node index.
   * Lhs encodes the edge label (use {@link MultiAlphabet#dep(int)}).
   * The parent/sibling/children fields encode the tree.
   */
  public boolean readDepsAsConsTree = false;
  
  public ConllxToDocument(MultiAlphabet alph) {
    this.alph = alph;
  }

  //CoNLL document format reader for dependency annotated corpora.
  //The expected format is described e.g. at http://ilk.uvt.nl/conll/#dataformat
  //
  //Data should adhere to the following rules:
  //  - Data files contain sentences separated by a blank line.
  //  - A sentence consists of one or tokens, each one starting on a new line.
  //  - A token consists of ten fields described in the table below.
  //  - Fields are separated by a single tab character.
  //  - All data files will contains these ten fields, although only the ID
  //    column is required to contain non-dummy (i.e. non-underscore) values.
  //Data files should be UTF-8 encoded (Unicode).
  //
  //Fields:
  //1  ID:      Token counter, starting at 1 for each new sentence and increasing
  //            by 1 for every new token.
  //2  FORM:    Word form or punctuation symbol.
  //3  LEMMA:   Lemma or stem.
  //4  CPOSTAG: Coarse-grained part-of-speech tag or category.
  //5  POSTAG:  Fine-grained part-of-speech tag. Note that the same POS tag
  //            cannot appear with multiple coarse-grained POS tags.
  //6  FEATS:   Unordered set of syntactic and/or morphological features.
  //7  HEAD:    Head of the current token, which is either a value of ID or '0'.
  //8  DEPREL:  Dependency relation to the HEAD.
  //9  PHEAD:   Projective head of current token.
  //10 PDEPREL: Dependency relation to the PHEAD.
  //
  //This CoNLL reader is compatible with the CoNLL-U format described at
  //  http://universaldependencies.org/format.html
  //Note that this reader skips CoNLL-U multiword tokens and ignores the last two
  //fields of every line, which are PHEAD and PDEPREL in CoNLL format, but are
  //replaced by DEPS and MISC in CoNLL-U.

  public Document parse(String id, File conllxFile) throws IOException {
    if (showReads)
      Log.info("reading docId=" + id + " conllxFile=" + conllxFile.getPath());
    if (readDepsAsConsTree)
      throw new RuntimeException("implement me");
    Document d = new Document(id, -1, alph);
    LabeledDirectedGraph.Builder g = new LabeledDirectedGraph().new Builder();
    try (BufferedReader r = FileUtil.getReader(conllxFile)) {
      int sentenceOffset = 0;   // index of first token in this sentence
      int token = 0;
      Document.Constituent prevSent = null;
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        if (line.isEmpty()) {
          // Sentence boundary
          Document.Constituent sent = d.newConstituent();
          sent.setOnlyChild(Document.NONE);
          if (prevSent == null) {
            d.cons_sentences = sent.index;
          } else {
            prevSent.setRightSib(sent.index);
            sent.setLeftSib(prevSent.index);
          }
          sent.setFirstToken(sentenceOffset);
          assert token > 0;
          sent.setLastToken(token-1);
          sentenceOffset = token;
          prevSent = sent;
        } else {
          // New token
          token++;
          String[] toks = line.split("\t");

          Document.Token t = d.newToken();
          t.setWord(alph.word(toks[1]));
          t.setPosH(alph.pos(toks[4]));
//          Log.info(toks[1] + "\t" + toks[4]);

          int parent = sentenceOffset + Integer.parseInt(toks[6]) - 1;
          if (parent >= 0) {
            int child = sentenceOffset + Integer.parseInt(toks[0]) - 1;
            int edgeLabel = alph.dep(toks[7]);
            g.add(parent, child, edgeLabel);
          } else {
            assert "ROOT".equals(toks[7]);
          }
        }
      }
    }
    d.parseyMcParseFace = g.freeze();
    return d;
  }
  
  public Document parseSafe(String id, File conllXFile) {
    try {
      return parse(id, conllXFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static void main(String[] args) throws Exception {
    File f = new File("/home/travis/code/fnparse/data/parma/ecbplus/ECB+_LREC2014/ECB+_pmp_conll/12/12_5ecbplus.conll");
    ConllxToDocument c2c = new ConllxToDocument(new MultiAlphabet());
    Document d = c2c.parse("noop", f);
    System.out.println("numToks=" + d.numTokens());
    System.out.println("numCons=" + d.numConstituents());
  }
}
