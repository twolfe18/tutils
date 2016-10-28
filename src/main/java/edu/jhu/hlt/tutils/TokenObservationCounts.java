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

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.Sentence;
import edu.jhu.hlt.concrete.Token;
import edu.jhu.hlt.concrete.serialization.iterators.TarGzArchiveEntryCommunicationIterator;
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
//    Log.info("word=" + word + " count=" + count + " s=" + s);
    return s;
  }
  
  public List<String> possibleCompletionsOf(String w, int minCount) {
    Trie t = root;
    for (int i = 0; i < w.length() && t != null; i++)
      t = t.children.get(w.codePointAt(i));
    if (t == null)
      return Collections.emptyList();

    List<String> c = new ArrayList<>();
    t.dfsVisit(new ArrayDeque<>(), spine -> {
      if (spine.peek().count >= minCount) {
        StringBuilder sb = new StringBuilder(w);
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
//        for (Trie cur : spine)
//          sb.appendCodePoint(cur.codepoint);
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
          
          if (tm.enoughTimePassed(2)) {
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
    List<File> commArchives = config.getFileGlob("communications");
    boolean lower = config.getBoolean("lowercase", false);
    boolean reverse = config.getBoolean("reverse", false);
    File out = config.getFile("output");
    TokenObservationCounts f = null, fl = null, b = null, bl = null;
    if (!reverse && !lower) f = new TokenObservationCounts();
    if (!reverse && lower) fl = new TokenObservationCounts();
    if (reverse && !lower) b = new TokenObservationCounts();
    if (reverse && lower) bl = new TokenObservationCounts();
    trainOnCommunications(commArchives, f, fl, b, bl);
    if (!reverse && !lower) FileUtil.serialize(f, out);
    if (!reverse && lower) FileUtil.serialize(fl, out);
    if (reverse && !lower) FileUtil.serialize(b, out);
    if (reverse && lower) FileUtil.serialize(bl, out);
  }

  public static void playing() {
    TokenObservationCounts t = new TokenObservationCounts();
    String s = "the quick brown fox jumped over the fence on thursday";
    t.train(s.split("\\s+"));
    
    t.getPrefixOccuringAtLeast("the", 0);
    t.getPrefixOccuringAtLeast("the", 1);
    t.getPrefixOccuringAtLeast("the", 2);
    t.getPrefixOccuringAtLeast("the", 3);
    t.getPrefixOccuringAtLeast("thursday", 3);
    
    t.possibleCompletionsOf("t", 1);
    t.possibleCompletionsOf("t", 2);
    t.possibleCompletionsOf("t", 3);
    
    ArrayDeque<String> st = new ArrayDeque<>();
    st.push("cats");
    st.push("are");
    st.push("farting");
    for (String i : st)
      System.out.println(i);
    
//    for (String ss : s.split("\\s+"))
//      t.getPrefixOccuringAtLeast(ss, 1);

    File f = new File("/tmp/tokenObs.lower.jser.gz");
    Log.info("loading from " + f.getPath());
    t = (TokenObservationCounts) FileUtil.deserialize(f);
    List<String> words = Arrays.asList("frustratingly", "simple", "morphology",
        "united", "nations", "how", "to", "determine", "break", "points",
        "prefix", "adwords", "google", "stress-testing");
    for (String w : words) {
      String prev = null;
      int prev_c = 0;
      for (int c = 1; true; c++) {
        String cur = t.getPrefixOccuringAtLeast(w, c);
        if (cur.isEmpty())
          break;
        if (!cur.equals(prev)) {
          double eps = 0.01;
          double r = (c+eps) / (prev_c+eps);
//          System.out.println(c + "\t" + ((int) Math.log(c)) + "\t" + r + "\t" + prev_c + "\t" + cur);
          System.out.printf("%-20s%12.1f%12.1f", cur, r, Math.log(c));
          if (r > 20 && cur.length() > 2)
            System.out.println("\t" + t.possibleCompletionsOf(cur, 5));
          else
            System.out.println();
          prev_c = c;
        }
        prev = cur;
      }
      System.out.println();
    }
  }
}
