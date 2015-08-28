package edu.jhu.hlt.tutils.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.ExperimentProperties;
import edu.jhu.hlt.tutils.MultiAlphabet;
import edu.jhu.hlt.tutils.Document.Token;

/**
 * Thin wrapper around the output of Percy Liang's brown clustering code output
 *
 * @author travis
 */
public class BrownClusters {

  private Map<String, String> word2path;

  public BrownClusters(File pathToPercyOutput) {
    if (!pathToPercyOutput.isDirectory()) {
      throw new IllegalArgumentException(
          "not a directory: " + pathToPercyOutput.getPath());
          //"should give a path that contains a file called \"path\"");
    }
    File pathFile = new File(pathToPercyOutput, "paths");
    word2path = new HashMap<>();
    try {
      BufferedReader r = new BufferedReader(
          new InputStreamReader(new FileInputStream(pathFile)));
      while (r.ready()) {
        String[] toks = r.readLine().split("\t");
        String path = toks[0];
        String word = toks[1];
        //int frequency = Integer.parseInt(toks[2]);
        String old = word2path.put(word, path);
        assert old == null;
      }
      r.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public String getPath(String word) {
    return getPath(word, Integer.MAX_VALUE);
  }

  public String getPath(String word, int maxLen) {
    String p = word2path.get(word);
    if (p == null) p = "???";
    if (p.length() > maxLen)
      return p.substring(0, maxLen);
    return p;
  }

  /** Populates the bc256 and bc1000 fields using the word field */
  public static void setClusters(Document doc, BrownClusters bc256, BrownClusters bc1000) {
    if (bc256 == null || bc1000 == null)
      throw new IllegalArgumentException();
    MultiAlphabet alph = doc.getAlphabet();
    if (alph == null)
      throw new IllegalArgumentException("need an alphabet");
    int n = doc.numTokens();
    for (int i = 0; i < n; i++) {
      Token t = doc.getToken(i);
      String ws = t.getWordStr();
      if (bc256 != null)
        t.setBc256(alph.shape(bc256.getPath(ws)));
      if (bc1000 != null)
        t.setBc1000(alph.shape(bc1000.getPath(ws)));
    }
  }

  /** Uses the key data.embeddings and ExperimentProperties */
  public static File bc256dirAuto() {
    return bc256dir(ExperimentProperties.getInstance().getExistingDir("data.embeddings"));
  }

  /** Uses the key data.embeddings and ExperimentProperties */
  public static File bc1000dirAuto() {
    return bc1000dir(ExperimentProperties.getInstance().getExistingDir("data.embeddings"));
  }

  /** If bcHome is null, will return a relative path */
  public static File bc256dir(File bcHome) {
    String f = "bc_out_256/full.txt_en_256/";
    if (bcHome == null)
      return new File(f);  // relative
    return new File(bcHome, f);
  }
  /** If bcHome is null, will return a relative path */
  public static File bc1000dir(File bcHome) {
    String f = "bc_out_1000/full.txt_en_1000/bc/";
    if (bcHome == null)
      return new File(f);  // relative
    return new File(bcHome, f);
  }
}
