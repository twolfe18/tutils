package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.jhu.prim.bimap.IntObjectBimap;

/**
 * A bunch of Alphabets with names. Easier to pass this around than a bunch of
 * bare Alphabets.
 *
 * @author travis
 */
public class MultiAlphabet {

  public static final String UNKNOWN = "UNKNOWN";
  public static final String NULL = "tHE-nULL-sTRING";  // how null is (de)serialized

  /** For things that can produce a pretty string if given an alphabet */
  public static interface Showable {
    public String show(MultiAlphabet alph);
  }

  // These can be aliased to each other if isolation is not needed.
  private IntObjectBimap<String> wordAlph = new IntObjectBimap<>();
  private IntObjectBimap<String> posAlph = new IntObjectBimap<>();
  private IntObjectBimap<String> nerAlph = new IntObjectBimap<>();
  private IntObjectBimap<String> shapeAlph = new IntObjectBimap<>();
  private IntObjectBimap<String> featAlph = new IntObjectBimap<>();
  private IntObjectBimap<String> cfgAlph = new IntObjectBimap<>();
  private IntObjectBimap<String> wnSynsetAlph = new IntObjectBimap<>(); // keys are WordNet synsets (using mit/jwi)
  private IntObjectBimap<String> depAlph = new IntObjectBimap<>();  // keys are dependency edge labels
  private IntObjectBimap<String> srlAlph = new IntObjectBimap<>();

  private Map<String, IntObjectBimap<String>> representation() {
    Map<String, IntObjectBimap<String>> m = new HashMap<>();
    m.put("word", wordAlph);
    m.put("pos", posAlph);
    m.put("ner", nerAlph);
    m.put("shape", shapeAlph);
    m.put("feat", featAlph);
    m.put("cfg", cfgAlph);
    m.put("wnSynset", wnSynsetAlph);
    m.put("dep", depAlph);
    m.put("srl", srlAlph);
    return m;
  }

  public void stopGrowth() {
    for (IntObjectBimap<String> m : representation().values())
      m.stopGrowth();
  }

  public void startGrowth() {
    for (IntObjectBimap<String> m : representation().values())
      m.startGrowth();
  }

  public int word(String w) {
    return wordAlph.lookupIndex(w, true);
  }
  public String word(int i) {
    return wordAlph.lookupObject(i);
  }
  public int numWord() {
    return wordAlph.size();
  }

  public int pos(String pos) {
    return posAlph.lookupIndex(pos, true);
  }
  public String pos(int i) {
    return posAlph.lookupObject(i);
  }
  public int numPos() {
    return posAlph.size();
  }

  public int ner(String ner) {
    return nerAlph.lookupIndex(ner, true);
  }
  public String ner(int i) {
    return nerAlph.lookupObject(i);
  }
  public int numNer() {
    return nerAlph.size();
  }

  public int lemma(String lemma) {
    return wordAlph.lookupIndex(lemma, true);
  }
  public String lemma(int i) {
    return wordAlph.lookupObject(i);
  }

  public int shape(String shape) {
    return shapeAlph.lookupIndex(shape, true);
  }
  public String shape(int i) {
    return shapeAlph.lookupObject(i);
  }

  public int feat(String feat) {
    return featAlph.lookupIndex(feat, true);
  }
  public String feat(int i) {
    return featAlph.lookupObject(i);
  }

  public int cfg(String cfg) {
    return cfgAlph.lookupIndex(cfg, true);
  }
  public String cfg(int i) {
    return cfgAlph.lookupObject(i);
  }
  public String[] cfg(int[] i) {
    String[] r = new String[i.length];
    for (int j = 0; j < i.length; j++)
      r[j] = cfg(i[j]);
    return r;
  }

  public int wnSynset(String wnSynset) {
    return wnSynsetAlph.lookupIndex(wnSynset, true);
  }
  public String wnSynset(int i) {
    return wnSynsetAlph.lookupObject(i);
  }

  public int dep(String dependencyArcLabel) {
    return depAlph.lookupIndex(dependencyArcLabel, true);
  }
  public String dep(int i) {
    return depAlph.lookupObject(i);
  }

  public int srl(String srlLabel) {
    return srlAlph.lookupIndex(srlLabel, true);
  }
  public String srl(int i) {
    return srlAlph.lookupObject(i);
  }

  /**
   * Reads a human-readable form of this alphabet written with serialize.
   */
  public static MultiAlphabet deserialize(File f) {
    if (!f.isFile()) {
      assert !f.isDirectory();
      Log.info("returning an empty alphabet because this is not a file: " + f.getPath());
      return new MultiAlphabet();
    }
    Log.info("reading from " + f.getPath());
    MultiAlphabet a = new MultiAlphabet();
    try {
      InputStream is = new FileInputStream(f);
      if (f.getName().toLowerCase().endsWith("gz"))
        is = new GZIPInputStream(is);
      try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
        Map<String, IntObjectBimap<String>> rep = a.representation();
        Counts<String> read = new Counts<>();
        while (r.ready()) {
          String[] toks = r.readLine().split("\t");
          assert toks.length == 3;
          IntObjectBimap<String> m = rep.get(toks[0]);
          read.increment(toks[0]);

          // Need to handle null<->String carefully in order to allow the
          // string "null" to be valid.
          if (NULL.equals(toks[2]))
            toks[2] = null;

          int i = m.lookupIndex(toks[2], true);
          assert i == Integer.parseInt(toks[1]) :
            "toks=" + Arrays.toString(toks) + " i=" + i;
        }
        // Report on the size and how many were read
        for (Entry<String, IntObjectBimap<String>> x : a.representation().entrySet()) {
          int c = read.getCount(x.getKey());
          Log.info("read=" + c + " entries for class=" + x.getKey() + ", total=" + x.getValue().size());
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return a;
  }

  /**
   * Writes a human-readable form of this alphabet to a text file.
   */
  public void serialize(File f) {
    Log.info("writing to " + f.getPath());
    try {
      OutputStream os = new FileOutputStream(f);
      if (f.getName().toLowerCase().endsWith("gz"))
        os = new GZIPOutputStream(os);
      try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os))) {
        for (Map.Entry<String, IntObjectBimap<String>> m : representation().entrySet()) {
          String name = m.getKey();
          IntObjectBimap<String> map = m.getValue();
          int n = map.size();
          for (int i = 0; i < n; i++) {
            String value = map.lookupObject(i);

            // Need to handle null<->String carefully in order to allow the
            // string "null" to be valid.
            if (value == null)
              value = NULL;

            w.write(name + "\t" + i + "\t" + value + "\n");
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 7) {
      System.err.println("Goes through a tabular file and replaces every string"
          + " in a column with an integer specified in a MultiAlphabet, and "
          + "updates the given MultiAlphabet.");
      System.err.println("");
      System.err.println("please provide:");
      System.err.println("1) an input tabular file");
      System.err.println("2) an input alphabet file");
      System.err.println("3) an output tabular file (may NOT be same as input)");
      System.err.println("4) an output alphabet file (may be same as input)");
      System.err.println("5) a representation to use (e.g. \"word\" or \"pos\")");
      System.err.println("6) a list of field numbers separated by commas (1-indexed like awk)");
      System.err.println("   you can use negative indices like in python");
      System.err.println("7) a field separator");
      System.exit(-1);
    }

    System.out.println(Arrays.toString(args));
    int argIdx = 0;
    File table = new File(args[argIdx++]);
    MultiAlphabet alph = MultiAlphabet.deserialize(new File(args[argIdx++]));
    File outputTable = new File(args[argIdx++]);
    File outputAlph = new File(args[argIdx++]);
    String rep = args[argIdx++];
    String[] fieldIdxStr = args[argIdx++].split(",");
    String sep = args[argIdx++];

    IntObjectBimap<String> m = alph.representation().get(rep);
    if (m == null)
      throw new IllegalArgumentException("illegal representation: " + args[argIdx - 1]);

    int n = fieldIdxStr.length;
    int[] fieldIdx = new int[n];
    BitSet seen = new BitSet();
    for (int i = 0; i < n; i++) {
      fieldIdx[i] = Integer.parseInt(fieldIdxStr[i]) - 1;
      assert fieldIdx[i] >= 0 && !seen.get(fieldIdx[i]);
      seen.set(fieldIdx[i]);
    }
    System.out.println("fields=" + Arrays.toString(fieldIdx));

    int c = 0;
    try (BufferedReader r = FileUtil.getReader(table)) {
      try (BufferedWriter w = FileUtil.getWriter(outputTable)) {
        while (r.ready()) {
          String line = r.readLine();
          String[] fields = line.split(sep);
          // Intify fields
          for (int f : fieldIdx) {
            String fi = f < 0 ? fields[fields.length + f] : fields[f];
            int i = m.lookupIndex(fi, true);
            fields[f] = String.valueOf(i);
          }
          // Output to table
          StringBuilder sb = new StringBuilder();
          for (String s : fields) {
            if (sb.length() > 0)
              sb.append(sep);
            sb.append(s);
          }
          sb.append('\n');
          w.write(sb.toString());
          // Print progress
          if (++c % 500_000 == 0)
            System.err.println("converted " + c + " lines");
        }
      }
    }
    // Save alphabet
    alph.serialize(outputAlph);
  }
}
