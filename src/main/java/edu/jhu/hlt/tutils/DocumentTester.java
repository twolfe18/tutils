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
}
