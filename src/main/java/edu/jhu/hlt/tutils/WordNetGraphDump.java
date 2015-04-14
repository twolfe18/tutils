package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.jhu.jerboa.counting.BloomFilter;
import edu.mit.jwi.IRAMDictionary;
import edu.mit.jwi.RAMDictionary;
import edu.mit.jwi.data.ILoadPolicy;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IPointer;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;

public class WordNetGraphDump {

  public static class WordWithRel {
    public final IWord word;
    public final String relation;
    public WordWithRel(IWord w, String r) {
      this.word = w;
      this.relation = r == null ? null : r.replace(' ', '-').toLowerCase();
    }
  }

  private File dictDir;
  private IRAMDictionary dict;
  public int maxPathLength = 3;

  private Map<String, BloomFilter> bfs; // keys are relations, values are sets of "<termA> <tab> <termB>"
  private BufferedWriter emitter;
  private int emitCounter = 0;
  public int emitCounterInterval = 500_000;

  /**
   * e.g. /home/hltcoe/twolfe/parma/data/wordnet/rion_snow/dict_400k_cropped
   * or /home/travis/code/fnparse/toydata/wordnet/dict_400k_cropped
   */
  public void load(File dictDir) {
    if (!dictDir.isDirectory())
      throw new IllegalArgumentException("not a WN directory: " + dictDir.getPath());
    System.out.println("loading from " + dictDir.getPath());
    long start = System.currentTimeMillis();
    this.dictDir = dictDir;
    dict = new RAMDictionary(dictDir, ILoadPolicy.IMMEDIATE_LOAD);
    try { dict.open(); }
    catch(Exception e) {
      throw new RuntimeException(e);
    }
    long time = System.currentTimeMillis() - start;
    Log.info(String.format(
        "loaded wordnet in %.1f seconds", time/1000d));
  }

  public void close() {
    dictDir = null;
    dict.close();
  }

  public List<String> getAllWords(POS pos) {
    if (dictDir == null)
      throw new IllegalStateException("you must call load first");
    List<String> words = new ArrayList<>();

    File f;
    if (pos == POS.ADJECTIVE) {
      f = new File(dictDir, "index.adj");
    } else if (pos == POS.ADVERB) {
      f = new File(dictDir, "index.adv");
    } else {
        f = new File(dictDir, "index." + pos.name().toLowerCase());
    }
    if (!f.isFile())
      throw new RuntimeException("f=" + f.getPath());

    try (BufferedReader r = new BufferedReader(new FileReader(f))) {
      while (r.ready()) {
        String line = r.readLine();
        String[] toks = line.split(" ");
        if (toks.length == 0) continue;
        String word = toks[0];
        if (word.isEmpty()) continue;
        words.add(word);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return words;
  }

  /**
   * Dump all paths through the WordNet graph (up to a given length) to the
   * given file. Will contain duplicates, will not be sorted (i.e. you probably
   * want to hit the resulting file with `sort -u`.
   */
  public void dump(File f) throws IOException {
    System.out.println("writing to " + f.getPath());
    emitter = new BufferedWriter(new FileWriter(f));
    //emitter = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(f))));
    List<WordWithRel> path = new ArrayList<>();
    for (POS pos : Arrays.asList(POS.NOUN, POS.VERB, POS.ADJECTIVE, POS.ADVERB)) {
      System.out.println("working on " + pos);
      List<String> words = getAllWords(pos);
      for (String w : words) {
        IIndexWord iw = dict.getIndexWord(w, pos);
        if (iw == null) {
          System.err.println("skipping: " + w + " " + pos);
          continue;
        }
        for (IWordID id : iw.getWordIDs()) {
          IWord word = dict.getWord(id);
          path.add(new WordWithRel(word, null));
          explore(path);
          assert path.size() == 1;
          path.clear();
        }
      }
    }
    emitter.close();
  }

  private void explore(List<WordWithRel> path) {
    if (path.size() > maxPathLength)
      return;

    IWord w = path.get(path.size() - 1).word;
    IWord back = path.size() > 1 ? path.get(path.size() - 2).word : null;

    // Related words
    Map<IPointer, List<IWordID>> m = w.getRelatedMap();
    for (Entry<IPointer, List<IWordID>> x : m.entrySet()) {
      String relation = x.getKey().getName();
      for (IWordID id : x.getValue()) {
        if (back != null && back.getID().equals(id))
          continue;
        IWord w2 = dict.getWord(id);
        WordWithRel end = new WordWithRel(w2, relation);
        path.add(end);
        emit(path);
        explore(path);
        path.remove(path.size() - 1);
      }
    }

    // Direct synset
    ISynset ss = w.getSynset();
    for (IWord w2 : ss.getWords()) {
      if (w2 == w || w2 == back) continue;
      WordWithRel end = new WordWithRel(w2, "synonym");
      path.add(end);
      emit(path);
      explore(path);
      path.remove(path.size() - 1);
    }

    // Related synsets
    for (Entry<IPointer, List<ISynsetID>> x : ss.getRelatedMap().entrySet()) {
      String relation = "syn/" + x.getKey();
      for (ISynsetID relSsId : x.getValue()) {
        ISynset relSs = dict.getSynset(relSsId);
        for (IWord w2 : relSs.getWords()) {
          if (w2 == w || w2 == back) continue;
          WordWithRel end = new WordWithRel(w2, relation);
          path.add(end);
          emit(path);
          explore(path);
          path.remove(path.size() - 1);
        }
      }
    }
  }

  private void emit(List<WordWithRel> path) {
    if (path.size() < 2)
      throw new IllegalArgumentException();
    StringBuilder relations = new StringBuilder();
    for (WordWithRel wwr : path) {
      if (wwr.relation == null)
        continue;
      if (relations.length() > 0)
        relations.append('_');
      relations.append(wwr.relation);
    }
    IWord w1 = path.get(0).word;
    IWord w2 = path.get(path.size() - 1).word;

    // File-based emissions
//    try {
//      emitter.write(w1.getID() + "\t" + w2.getID() + "\t" + relations + "\n");
//    } catch (Exception e) {
//      throw new RuntimeException(e);
//    }

    // Bloom-filter based emissions
    String relStr = relations.toString();
    BloomFilter bf = bfs.get(relStr);
    if (bf == null) {
      try {
        bf = new BloomFilter(1_000_000, 500);
      } catch (Exception e) {
        throw new RuntimeException("what were you thinking Ben?", e);
      }
      bfs.put(relStr, bf);
    }
    bf.set(w1 + "\t" + w2);

    if (++emitCounter % emitCounterInterval == 0)
      System.out.println("emitted " + emitCounter + " triples");
  }

  public int numEmitted() {
    return emitCounter;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("please provide:");
      System.err.println("1) a wordnet dict/ directory");
      System.err.println("2) an output file");
      System.err.println("3) max path length");
      System.exit(-1);
    }
    long start = System.currentTimeMillis();
    WordNetGraphDump gd = new WordNetGraphDump();
    gd.maxPathLength = Integer.parseInt(args[2]);
    gd.load(new File(args[0]));
    gd.dump(new File(args[1]));
    gd.close();
    long time = System.currentTimeMillis() - start;
    System.out.println("emitted " + gd.numEmitted() + " triples in " + (time/1000d) + " seconds");
  }
}
