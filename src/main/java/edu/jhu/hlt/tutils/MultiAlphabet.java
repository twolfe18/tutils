package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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

  private Map<String, IntObjectBimap<String>> representation() {
    Map<String, IntObjectBimap<String>> m = new HashMap<>();
    m.put("word", wordAlph);
    m.put("pos", posAlph);
    m.put("ner", nerAlph);
    m.put("shape", shapeAlph);
    m.put("feat", featAlph);
    m.put("cfg", cfgAlph);
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

  public int pos(String pos) {
    return posAlph.lookupIndex(pos, true);
  }
  public String pos(int i) {
    return posAlph.lookupObject(i);
  }

  public int ner(String ner) {
    return nerAlph.lookupIndex(ner, true);
  }
  public String ner(int i) {
    return nerAlph.lookupObject(i);
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
          int i = m.lookupIndex(toks[2], true);
          assert i == Integer.parseInt(toks[1]);
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
}
