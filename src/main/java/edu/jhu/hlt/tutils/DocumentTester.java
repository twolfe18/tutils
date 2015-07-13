package edu.jhu.hlt.tutils;

/**
 * Provides many things that you can test about a {@link Document}. Note that
 * this tool is designed to let you run as many or as few checks as fits your
 * usage scenario.
 *
 * @author travis
 */
public class DocumentTester {

  private Document doc;
  private boolean printErrorMessages;

  public DocumentTester(Document doc, boolean printErrorMessages) {
    this.doc = doc;
    this.printErrorMessages = printErrorMessages;
  }

  public boolean firstAndLastTokensValid() {
    boolean good = true;
    int nToks = doc.word.length;

    // sanity
    if (doc.firstToken.length != doc.lastToken.length) {
      if (printErrorMessages)
        System.err.println("first and last aren't the same length");
      good = false;
    }

    // first
    for (int i = 0; i < doc.firstToken.length; i++) {
      if (doc.firstToken[i] != Document.UNINITIALIZED &&
          (doc.firstToken[i] >= nToks || doc.firstToken[i] < 0)) {
        if (printErrorMessages) {
          System.err.println("firstToken[" + i + "]=" + doc.firstToken[i]
              + " and nToks=" + nToks);
        }
        good = false;
      }
    }

    // last
    for (int i = 0; i < doc.lastToken.length; i++) {
      if (doc.lastToken[i] != Document.UNINITIALIZED &&
          (doc.lastToken[i] >= nToks || doc.lastToken[i] < 0)) {
        if (printErrorMessages) {
          System.err.println("lastToken[" + i + "]=" + doc.lastToken[i]
              + " and nToks=" + nToks);
        }
        good = false;
      }
    }

    return good;
  }

  public boolean checkConstituencyTree(int start, int end) {

    boolean good = true;

    // Only one constituent has one NONE parent
    int numNoneParents = 0;
    for (int i = start; i <= end; i++) {
      int p = doc.parent[i];
      if (p < 0 && p != Document.NONE) {
        if (printErrorMessages)
          System.err.println("p=" + p);
        good = false;
      }
      if (p == Document.NONE)
        numNoneParents++;
    }
    if (numNoneParents != 1) {
      if (printErrorMessages)
        System.err.println("numNoneParents=" + numNoneParents);
      good = false;
    }

    // TODO other conditions...

    return good;
  }

  public boolean checkWords() {
    boolean good = true;
    MultiAlphabet alph = doc.getAlphabet();
    int Nw = alph.numWord();
    for (int i = 0; i < doc.tokTop; i++) {
      int w = doc.getWord(i);
      if (w == Document.UNINITIALIZED) {
        good = false;
        if (printErrorMessages)
          System.err.println("unititialized word: i=" + i + " tokTop=" + doc.tokTop);
      }
      if (w >= Nw) {
        good = false;
        if (printErrorMessages)
          System.err.println("mis-indexed word: i=" + i + " word=" + w + " alph.numWords=" + Nw);
      }
    }
    return good;
  }
}
