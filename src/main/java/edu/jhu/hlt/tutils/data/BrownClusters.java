package edu.jhu.hlt.tutils.data;

import java.io.BufferedReader;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.Document.Token;
import edu.jhu.hlt.tutils.ExperimentProperties;
import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.MultiAlphabet;
import edu.jhu.hlt.tutils.StringUtils;

/**
 * Thin wrapper around the output of Percy Liang's brown clustering code output
 *
 * @author travis
 */
public class BrownClusters {
  public static boolean DEBUG = false;

  private Map<String, String> word2path;

  // TODO Consider filtering by frequency:
  // /home/travis/code/fnparse/data/embeddings/bc_out_256/full.txt_en_256
  // head paths
  //  0000000 Fierecillos     2
  //  0000000 Golab-e 2
  //  0000000 nadmorski       2
  //  0000000 Trumshing       2
  //  0000000 BosniaThe       2
  //  0000000 Radanja 2
  //  0000000 FEMUSC  2
  //  0000000 TOŠK    2
  //  0000000 Sheder  2
  //  0000000 Adansiman       2
  //  caligula full.txt_en_256 $ awk '{print $1}' <paths | wc -l
  //  3805621
  //  caligula full.txt_en_256 $ awk '$3 >= 3 {print $1}' <paths | wc -l
  //  2626651
  //  caligula full.txt_en_256 $ awk '$3 >= 4 {print $1}' <paths | wc -l
  //  2099093
  //  caligula full.txt_en_256 $ awk '$3 >= 5 {print $1}' <paths | wc -l
  //  1778575
  //  caligula full.txt_en_256 $ awk '$3 >= 6 {print $1}' <paths | wc -l
  //  1562592
  //  caligula full.txt_en_256 $ awk '$3 >= 7 {print $1}' <paths | wc -l
  //  1401029
  // I checked with propbank, and there are NO words which occur in the count==2 subset of the BC data
  public BrownClusters(File pathToPercyOutput) {
    this(pathToPercyOutput, 3);
  }
  public BrownClusters(File pathToPercyOutput, int minCount) {
    if (DEBUG)
      Log.info("calling from:\n" + StringUtils.join("\n", new Exception().getStackTrace()));
    Log.info("loading path=" + pathToPercyOutput.getPath() + " minCount=" + minCount);
    if (!pathToPercyOutput.isDirectory()) {
      throw new IllegalArgumentException(
          "not a directory: " + pathToPercyOutput.getPath());
          //"should give a path that contains a file called \"path\"");
    }
    int discarded = 0;
    File pathFile = new File(pathToPercyOutput, "paths");
    word2path = new HashMap<>();
    try (BufferedReader r = FileUtil.getReader(pathFile)) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        String[] toks = line.split("\t");
        String path = toks[0].intern();
        String word = toks[1];
        int frequency = Integer.parseInt(toks[2]);
        if (frequency >= minCount) {
          String old = word2path.put(word, path);
          assert old == null;
        } else {
          discarded++;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    long estStrSize = 2 * 7;
    long estBytes = (long) (word2path.size() * (estStrSize + 8) * 1.6d);
    Log.info("with minCount=" + minCount + " discarded=" + discarded + " kept=" + word2path.size() + " estSize=" + (estBytes/(1L << 20) + "MB"));
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
