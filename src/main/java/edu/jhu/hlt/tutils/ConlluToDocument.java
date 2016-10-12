package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import edu.jhu.hlt.tutils.Document.Constituent;
import edu.jhu.hlt.tutils.Document.Token;

/**
 * From http://universaldependencies.org/format.html
 * 
 * CoNLL-U Format
 * 
 * We use a revised version of the CoNLL-X format called CoNLL-U. Annotations are
 * encoded in plain text files (UTF-8, using only the LF character as line break)
 * with three types of lines:
 *
 * Word lines containing the annotation of a word/token in 10 fields separated by
 * single tab characters; see below. Blank lines marking sentence boundaries.
 * Comment lines starting with hash (#). Sentences consist of one or more word
 * lines, and word lines contain the following fields:
 *
 * 0 ID: Word index, integer starting at 1 for each new sentence; may be a range for tokens with multiple words.
 * 1 FORM: Word form or punctuation symbol.
 * 2 LEMMA: Lemma or stem of word form.
 * 3 UPOSTAG: Universal part-of-speech tag drawn from our revised version of the Google universal POS tags.
 * 4 XPOSTAG: Language-specific part-of-speech tag; underscore if not available.
 * 5 FEATS: List of morphological features from the universal feature inventory or from a defined language-specific extension; underscore if not available.
 * 6 HEAD: Head of the current token, which is either a value of ID or zero (0).
 * 7 DEPREL: Universal dependency relation to the HEAD (root iff HEAD = 0) or a defined language-specific subtype of one.
 * 8 DEPS: List of secondary dependencies (head-deprel pairs).
 * 9 MISC: Any other annotation.
 *
 * The fields must additionally meet the following constraints:
 *
 * Fields must not be empty. Fields must not contain space characters. Underscore
 * (_) is used to denote unspecified values in all fields except ID. Note that no
 * format-level distinction is made for the rare cases where the FORM or LEMMA is
 * the literal underscore – processing in such cases is application-dependent.
 * Further, in UD treebanks the UPOSTAG, HEAD, and DEPREL columns are not allowed
 * to be left unspecified.
 *
 * @author travis
 */
public class ConlluToDocument {

  private MultiAlphabet alph;
  public boolean showReads = false;
  
  public ConlluToDocument(MultiAlphabet alph) {
    this.alph = alph;
  }

  /**
   * Using 0 as ROOT
   */
  public Document parse(String id, File conlluFile) throws IOException {
    if (showReads)
      Log.info("reading from " + conlluFile.getPath());
    
    Document d = new Document(null, -1, alph);
    Constituent prev = null;
    
    LabeledDirectedGraph.Builder deps = new LabeledDirectedGraph().new Builder();
    
    int sentenceOffset = 0;
    try (BufferedReader r = FileUtil.getReader(conlluFile)) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        if (line.isEmpty()) {
          throw new RuntimeException("implmeent me");
        } else {
          
          String[] toks = line.split("\t");
          assert toks.length == 10;
          Token t = d.newToken();
          
          int uPos = alph.pos(toks[3]); // universal POS tag
//          int xPos = alph.pos(toks[4]); // language specific POS tag

          t.setWord(alph.word(toks[1]));
          t.setLemma(alph.word(toks[2]));
          t.setPosG(uPos);
          
          int head = Integer.parseInt(toks[6]);
          int modifier = Integer.parseInt(toks[0]);
          int deprel = alph.dep(toks[7]);
          head += sentenceOffset;
          modifier += sentenceOffset;
          deps.add(head, modifier, deprel);
          
          // Have a constituent for every feature type?
          // They aren't "dense", as in the features which fire on any given token are sparse
          /*
           * There are 72 feature types, and only 369 values (across all languages)
           * 15k features if you consider them all together
           * 
           * cat * /*-ud-train.conllu | awk -F"\t" '{print $6}' | sort | uniq -c | sort -rn | tee /tmp/u
           * wc -l /tmp/u
           * 14695 /tmp/u
           * awk '{print $2}' </tmp/u | tr '|' '\n' | sort -u | wc -l
           * 369
           * awk '{print $2}' </tmp/u | tr '|' '\n' | awk -F"=" '{print $1}' | sort -u | wc -l
           * 72
           * 
           * 
           * I think the way to do this is to have one constituent per token
           * each cons has a child corresponding to each feature
           */
          Constituent c = d.newConstituent();
          c.setRightSib(Document.NONE);
          if (prev != null) {
            prev.setRightSib(c.getIndex());
            c.setLeftSib(prev.getIndex());
          } else {
            c.setLeftSib(Document.NONE);
          }
          
          String[] feats = toks[5].split("|");
          Constituent[] featsC = new Constituent[feats.length];
          for (int i = 0; i < feats.length; i++) {
//            String[] fvt = feats[i].split("=");
//            assert fvt.length == 2;
            featsC[i] = d.newConstituent();
            featsC[i].setParent(c.getIndex());
            featsC[i].setLhs(alph.pos(feats[i]));
            featsC[i].setOnlyChild(Document.NONE);
            featsC[i].setLeftSib(Document.NONE);
            featsC[i].setRightSib(Document.NONE);
          }
          for (int i = 1; i < feats.length; i++)
            featsC[i].setLeftChild(featsC[i-1].getIndex());
          for (int i = 0; i < feats.length-1; i++)
            featsC[i].setRightChild(featsC[i+1].getIndex());
          
        }
      }
    }
    d.universalDependencies = deps.freeze();
    return d;
  }

}
