package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

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

  /** returns how many tokens trained on */
  public int train(Communication c, boolean lowercase) {
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
  
  public static void trainOnCommunications(List<File> commArchives, TokenObservationCounts t, TokenObservationCounts tLower) throws IOException {
    assert t != null || tLower != null;
    long n = 0;
    TimeMarker tm = new TimeMarker();
    for (File f : commArchives) {
      try (InputStream is = new FileInputStream(f);
          TarGzArchiveEntryCommunicationIterator iter = new TarGzArchiveEntryCommunicationIterator(is)) {
        while (iter.hasNext()) {
          Communication c = iter.next();
          if (t != null)
            n += t.train(c, false);
          if (tLower != null)
            n += tLower.train(c, true);
          
          if (tm.enoughTimePassed(2)) {
            Log.info("tokens=" + n + " archive=" + f.getPath());
          }
        }
      }
    }
  }
  
  public static void main(String[] args) {
    TokenObservationCounts t = new TokenObservationCounts();
    String s = "the quick brown fox jumped over the fence on thursday";
    t.train(s.split("\\s+"));
    
    t.getPrefixOccuringAtLeast("the", 0);
    t.getPrefixOccuringAtLeast("the", 1);
    t.getPrefixOccuringAtLeast("the", 2);
    t.getPrefixOccuringAtLeast("the", 3);
    t.getPrefixOccuringAtLeast("thursday", 3);
    
    for (String ss : s.split("\\s+"))
      t.getPrefixOccuringAtLeast(ss, 1);
  }
}
