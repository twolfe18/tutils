package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.serialization.iterators.TarGzArchiveEntryCommunicationIterator;
import edu.jhu.prim.list.IntArrayList;
import edu.jhu.prim.map.IntObjectHashMap;

/**
 * Answers queries of "find me the longest prefix of a string which
 * has appeared at least X times in some training data".
 * 
 * TODO Add method to prune the trie of nodes with count < X.
 * TODO Non-Java-serialization way of writing out (prefix, count) data.
 *
 * @author travis
 */
public class TokenObservationCounts implements Serializable {
  private static final long serialVersionUID = -4531216400454523274L;

  static class Trie implements Serializable {
    private static final long serialVersionUID = -2073422284776603573L;
    int codepoint;
    long count;
    IntObjectHashMap<Trie> children;
    
    public Trie(int codepoint) {
      this.codepoint = codepoint;
      this.count = 0;
      this.children = new IntObjectHashMap<>();
    }
    
    public void add(String w, int offset) {
      count++;
      if (offset < w.length()) {
        int c = w.codePointAt(offset);
        Trie cc = children.get(c);
        if (cc == null) {
          cc = new Trie(c);
          children.put(c, cc);
        }
        cc.add(w, offset+1);
      }
    }
    
    public void dfsVisit(ArrayDeque<Trie> spine, Consumer<ArrayDeque<Trie>> visitor) {
      spine.push(this);
      visitor.accept(spine);
      IntObjectHashMap<Trie>.Iterator iter = children.iterator();
      while (iter.hasNext()) {
        iter.advance();
        Trie c = iter.value();
        c.dfsVisit(spine, visitor);
      }
      spine.pop();
    }
  }
  
  private Trie root;

  public TokenObservationCounts() {
    root = new Trie(-1);
  }
  
  public void pruneEntriesWithCountLessThan(int c) {
    MutableInt m = new MutableInt(0);
    MutableInt v = new MutableInt(0);
    MutableLong p = new MutableLong(0);
    root.dfsVisit(new ArrayDeque<>(), spine -> {
      v.add(1);
      Trie t = spine.peek();
      IntArrayList rem = new IntArrayList();
      IntObjectHashMap<Trie>.Iterator iter = t.children.iterator();
      while (iter.hasNext()) {
        iter.advance();
        Trie tt = iter.value();
        if (tt.count < c) {
          p.add(tt.count);
          rem.add(iter.key());
        }
      }
      for (int i = 0; i < rem.size(); i++) {
        t.children.remove(rem.get(i));
        m.add(1);
      }
    });
    Log.info("visited " + v.getValue() + " nodes, "
        + "removed " + m.getValue() + " nodes (does not count sub-tree nodes pruned)"
        + " with count < " + c
        + ", representing " + (100d*p.toLong() / ((double) root.count))
        + "% abs=" + p.toLong());
  }
  
  public long getNumTrainingTokens() {
    return root.count;
  }

  public void train(String[] words) {
    train(Arrays.asList(words));
  }
  
  public void train(List<String> words) {
    for (String w : words)
      root.add(w, 0);
  }
  
  public static String reverse(String s) {
    int n = s.length();
    StringBuilder sb = new StringBuilder(n);
    for (int i = n-1; i >= 0; i--)
      sb.appendCodePoint(s.codePointAt(i));
    return sb.toString();
  }

  /** returns how many tokens trained on */
  public int train(Communication c, boolean lowercase, boolean reverse) {
    int tok = 0;
    if (c.isSetSectionList()) {
      for (Section section : c.getSectionList()) {
        if (section.isSetSentenceList()) {
          for (Sentence sentence : section.getSentenceList()) {
            List<Token> toks = sentence.getTokenization().getTokenList().getTokenList();
            for (Token t : toks) {
              String w = t.getText();
              if (lowercase)
                w = w.toLowerCase();
              if (reverse)
                w = reverse(w);
              root.add(w, 0);
              tok++;
            }
          }
        }
      }
    }
    return tok;
  }
  
  public int getCount(String s) {
    int n = s.length();
    Trie t = root;
    for (int i = 0; i < n; i++) {
      t = t.children.get(s.codePointAt(i));
      if (t == null)
        return 0;
    }
    assert t.count <= Integer.MAX_VALUE;
    return (int) t.count;
  }
  
  public String getPrefixOccuringAtLeast(String word, int count) {
    int n = word.length();
    StringBuilder sb = new StringBuilder(n);
    Trie cur = root;
    while (sb.length() < n) {
      int cp = word.codePointAt(sb.length());
      Trie c = cur.children.get(cp);
      if (c != null && c.count >= count) {
        sb.appendCodePoint(cp);
        cur = c;
      } else {
        break;
      }
    }
    String s = sb.toString();
    return s;
  }
  
  /** if count=1 and you pass in a string with count=2, the entire string is returned */
  public String getPrefixOccurringAtMost(String word, int count) {
    int n = word.length();
    StringBuilder sb = new StringBuilder(n);
    Trie cur = root;
    while (sb.length() < n) {
      int cp = word.codePointAt(sb.length());
      Trie c = cur.children.get(cp);
      sb.appendCodePoint(cp);
      if (c == null || c.count < count)
        break;
      cur = c;
    }
//    if (cur.count > count)
//      return "";
    String s = sb.toString();
    return s;
  }
  
  public List<String> possibleCompletionsOf(String w, int minCount, boolean reverse) {
    Trie t = root;
    for (int i = 0; i < w.length() && t != null; i++)
      t = t.children.get(w.codePointAt(i));
    if (t == null)
      return Collections.emptyList();

    List<String> c = new ArrayList<>();
    t.dfsVisit(new ArrayDeque<>(), spine -> {
      if (spine.peek().count >= minCount) {
        StringBuilder sb;
        if (reverse) {
          sb = new StringBuilder();
          for (Trie cur : spine)
            sb.appendCodePoint(cur.codepoint);
          sb.append(reverse(w).substring(1));
        } else {
          sb = new StringBuilder(w);
          boolean first = true;
          java.util.Iterator<Trie> spineItr = spine.descendingIterator();
          while (spineItr.hasNext()) {
            Trie cc = spineItr.next();
            if (first) {
              first = false;
            } else {
              sb.appendCodePoint(cc.codepoint);
            }
          }
        }
        sb.append('(');
        sb.append(spine.peek().count);
        sb.append(')');
        c.add(sb.toString());
      }
    });
    return c;
  }
  
  public static void trainOnCommunications(List<File> commArchives,
      TokenObservationCounts forwards, TokenObservationCounts forwardsLower,
      TokenObservationCounts backwards, TokenObservationCounts backwardsLower) throws IOException {
    assert forwards != null || forwardsLower != null;
    long n = 0;
    TimeMarker tm = new TimeMarker();
    for (File f : commArchives) {
      try (InputStream is = new FileInputStream(f);
          TarGzArchiveEntryCommunicationIterator iter = new TarGzArchiveEntryCommunicationIterator(is)) {
        while (iter.hasNext()) {
          Communication c = iter.next();
          if (forwards != null)
            n += forwards.train(c, false, false);
          if (forwardsLower != null)
            n += forwardsLower.train(c, true, false);
          if (backwards != null)
            n += backwards.train(c, false, true);
          if (backwardsLower != null)
            n += backwardsLower.train(c, true, true);
          
          if (tm.enoughTimePassed(10)) {
            Log.info("tokens=" + n + " archive=" + f.getPath()+ "\t" + memoryUsage());
          }
        }
      }
    }
  }

  public static String memoryUsage() {
    Runtime r = Runtime.getRuntime();
    return String.format("MemoryUsage used=%.1fG free=%.1fG limit=%.1fG",
        r.totalMemory() / (1024 * 1024 * 1024d),
        r.freeMemory() / (1024 * 1024 * 1024d),
        r.maxMemory() / (1024 * 1024 * 1024d));
  }
  
  public static void main(String[] args) throws IOException {
    ExperimentProperties config = ExperimentProperties.init(args);
    if (config.getBoolean("play", false)) {
      playing(config);
    } else {
      File load = config.getFile("load", null);
      List<File> commArchives = config.getFileGlob("communications");
      boolean lower = config.getBoolean("lowercase", false);
      boolean reverse = config.getBoolean("reverse", false);
      File out = config.getFile("output");
      int minCount = config.getInt("minCount", 0);
      Log.info("writing to " + out.getPath());
      Log.info("reading " + commArchives.size() + " communication archives");
      Log.info(commArchives);

      TokenObservationCounts f = null, fl = null, b = null, bl = null;
      if (load != null) {
        Log.info("loading from " + load.getPath());
        if (!reverse && !lower) f = (TokenObservationCounts) FileUtil.deserialize(load);
        if (!reverse && lower) fl = (TokenObservationCounts) FileUtil.deserialize(load);
        if (reverse && !lower) b = (TokenObservationCounts) FileUtil.deserialize(load);
        if (reverse && lower) bl = (TokenObservationCounts) FileUtil.deserialize(load);
      } else {
        Log.info("creating new TokenObservationCounts");
        if (!reverse && !lower) f = new TokenObservationCounts();
        if (!reverse && lower) fl = new TokenObservationCounts();
        if (reverse && !lower) b = new TokenObservationCounts();
        if (reverse && lower) bl = new TokenObservationCounts();
      }

      trainOnCommunications(commArchives, f, fl, b, bl);
      
      if (minCount > 1) {
        Log.info("pruning entries with minCount="+ minCount);
        if (!reverse && !lower) f.pruneEntriesWithCountLessThan(minCount);
        if (!reverse && lower) fl.pruneEntriesWithCountLessThan(minCount);
        if (reverse && !lower) b.pruneEntriesWithCountLessThan(minCount);
        if (reverse && lower) bl.pruneEntriesWithCountLessThan(minCount);
      }

      if (!reverse && !lower) FileUtil.serialize(f, out);
      if (!reverse && lower) FileUtil.serialize(fl, out);
      if (reverse && !lower) FileUtil.serialize(b, out);
      if (reverse && lower) FileUtil.serialize(bl, out);
      
      Log.info("done");
    }
  }
  
  private static void foo(boolean rev, String w, TokenObservationCounts f) {
    String prefix = rev ? "backwards" : "forwards";
    if (rev)
      w = reverse(w);
    String prev = "";
    int prev_c = 1;//(int) f.root.count;
    double prev_logc = 0.0001;
    for (int c = 1; true; c++) {
      String cur = f.getPrefixOccuringAtLeast(w, c);
      if (cur.isEmpty())
        break;
      if (!cur.equals(prev)) {
        double eps = 0.01;
        double r = (c+eps) / (prev_c+eps);
        double lr = (Math.log(c) - prev_logc) / prev_logc;
        
        String ss;
        if (lr > 0.75) ss = "***";
        else if (lr > 0.5) ss = "**";
        else if (lr > 0.3) ss = "*";
        else ss = "";

        boolean changePoint = lr > 0.5;

        if (rev)
          System.out.printf("%-14s%-5s%20s%12.1f%8.1f%8.2f", prefix, ss, reverse(prev), r, Math.log(c), lr);
        else
          System.out.printf("%-14s%-5s%-20s%12.1f%8.1f%8.2f", prefix, ss, prev, r, Math.log(c), lr);

        if (rev && changePoint && !prev.isEmpty())
          System.out.println("\t" + f.possibleCompletionsOf(prev, 100, true));
        else if (!rev && changePoint && !prev.isEmpty())
          System.out.println("\t" + f.possibleCompletionsOf(prev, 100, false));
        else
          System.out.println();

        if (changePoint) {
          prev_c = c;
          prev_logc = Math.log(c);
        }
      }
      prev = cur;
    }
  }

  private static void bar(boolean rev, String w, TokenObservationCounts f) {
    String prefix = "backwards";
    w = reverse(w);
    String prev = "";
    int prev_c = f.getCount(prev);
    double prev_logc = Math.log(prev_c);
    for (int len = 1; len < w.length(); len++) {
      String cur = w.substring(0, len);
      String pcur = w.substring(0, len-1);
      int c = f.getCount(cur);
      double logc = Math.log(c);
      double lr = (prev_logc - logc) / logc;
//      double lr = (prev_logc - logc) / prev_logc;
        
      String ss;
      if (lr > 0.75) ss = "***";
      else if (lr > 0.5) ss = "**";
      else if (lr > 0.3) ss = "*";
      else ss = "";

      double eps = 0.01;
      double r = (c+eps) / (prev_c+eps);
      System.out.printf("%-14s%-5s%20s%12.1f%8.1f%8.2f", prefix, ss, reverse(pcur), r, Math.log(c), lr);
      System.out.println();
//      if (lr > 1) {
      if (lr > 0.8) {
        prev = cur;
        prev_c = c;
        prev_logc = logc;
      }
    }
  }

  public static void playing(ExperimentProperties config) {
//    TokenObservationCounts t = new TokenObservationCounts();
//    String s = "the quick brown fox jumped over the fence on thursday";
//    t.train(s.split("\\s+"));
//    
//    t.getPrefixOccuringAtLeast("the", 0);
//    t.getPrefixOccuringAtLeast("the", 1);
//    t.getPrefixOccuringAtLeast("the", 2);
//    t.getPrefixOccuringAtLeast("the", 3);
//    t.getPrefixOccuringAtLeast("thursday", 3);
//    
//    t.possibleCompletionsOf("t", 1);
//    t.possibleCompletionsOf("t", 2);
//    t.possibleCompletionsOf("t", 3);
//    
//    ArrayDeque<String> st = new ArrayDeque<>();
//    st.push("cats");
//    st.push("are");
//    st.push("farting");
//    for (String i : st)
//      System.out.println(i);
//    
//    for (String ss : s.split("\\s+"))
//      t.getPrefixOccuringAtLeast(ss, 1);

    File ff = new File("/tmp/charCounts.nyt_eng_2007.lower-true.reverse-false.jser.gz");
    File bf = new File("/tmp/charCounts.nyt_eng_2007.lower-true.reverse-true.jser.gz");
    Log.info("loading forwards from " + ff.getPath());
    TokenObservationCounts f = (TokenObservationCounts) FileUtil.deserialize(ff);
    Log.info("loading backwards from " + bf.getPath());
    TokenObservationCounts b = (TokenObservationCounts) FileUtil.deserialize(bf);

    List<String> words = Arrays.asList("frustratingly", "simple", "morphology",
        "reunited", "united", "nations", "how", "to", "determine", "break", "points",
        "prefix", "adwords", "google", "stress-testing",
        "totality", "totalitarianism", "humanism",
        "dog", "food",
        "happened", "went", "likes", "spiking", "spike",
        "forwards", "backwards", "inwards", "intimate",
        "church", "association", "linguistics");

    for (String w : words) {
      foo(false, w, f);
//      foo(true, w, b);
      bar(true, w, b);
      System.out.println();
    }
  }
}
