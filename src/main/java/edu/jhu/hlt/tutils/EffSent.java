package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Short for "efficient sentence"
 *
 * @author travis
 */
public class EffSent implements Serializable {
  private static final long serialVersionUID = -7935126113889559888L;
  public static final Charset UTF8 = Charset.forName("UTF8");
  
  public static class Mention implements Serializable {
    private static final long serialVersionUID = 7528881636006561742L;
    public byte[] mid;   // UTF-8 encoded, everything after "/m/"
    public short head;   // token index
    public short start;  // inclusive token index
    public short end;    // exclusive token index
    
    public void setHead(int head) {
      if (head > Short.MAX_VALUE || head < Short.MIN_VALUE)
        throw new IllegalArgumentException();
      this.head = (short) head;
    }
    
    public void setSpan(Span s) {
      if (s.start > Short.MAX_VALUE || s.start < 0)
        throw new IllegalArgumentException();
      if (s.end > Short.MAX_VALUE || s.end < 0)
        throw new IllegalArgumentException();
      start = (short) s.start;
      end = (short) s.end;
    }
    
    public Span span() {
      return Span.getSpan(start, end);
    }
    
    public String getFullMid() {
      return "/m/" + new String(mid, UTF8);
    }
    public String getAbbrMid() {
      return new String(mid, UTF8);
    }
    
    @Override
    public String toString() {
      return "(" + getFullMid() + " " + span().shortString() + " h=" + head + ")";
    }
    
    public String show(DepNode[] parse, MultiAlphabet a) {
      List<String> toks = new ArrayList<>();
      for (int i = start; i < end; i++)
        toks.add(a.word(parse[i].word));
      toks.add(span().shortString());
      toks.add(getFullMid());
      return StringUtils.join(" ", toks);
    }
    
    public static final Comparator<Mention> BY_SPAN_ASC = new Comparator<Mention>() {
      @Override
      public int compare(Mention o1, Mention o2) {
        if (o1.start < o2.start)
          return -1;
        if (o1.start > o2.start)
          return +1;
        assert o1.end == o2.end : "overlapping?";
        return 0;
      }
    };
  }
  
  
  private DepNode[] parse;
  private Mention[] entities;
  
  public EffSent(DepNode[] parse) {
    this.parse = parse;
  }
  
  public void setEntities(Mention[] entities) {
    this.entities = entities;
  }
  
  public List<String> getTokenStrings(MultiAlphabet a) {
    List<String> t = new ArrayList<>(parse.length);
    for (int i = 0; i < parse.length; i++)
      t.add(a.word(parse[i].word));
    return t;
  }
  
  public DepNode[] parse() {
    return parse;
  }

  public DepNode parse(int i) {
    return parse[i];
  }

  public void setMentions(String mentionLine, boolean computeHeads) {
    this.entities = parseMentions(mentionLine);
    if (computeHeads) {
      int[] depth = DepNode.depths(parse);
      for (int i = 0; i < entities.length; i++) {
        ArgMin<Integer> a = new ArgMin<>();
        for (int j = entities[i].start; j < entities[i].end; j++)
          a.offer(j, depth[j]);
        entities[i].setHead(a.get());
      }
    }
  }
  
  public Mention mention(int i) {
    return entities[i];
  }
  
  public int numMentions() {
    return entities.length;
  }

  public int numTokens() {
    return parse.length;
  }
  
  public Iterable<Mention> mentions() {
    return Arrays.asList(entities);
  }
  
  public int[] buildToken2EntityMap() {
    int[] m = new int[parse.length];
    Arrays.fill(m, -1);
    for (int i = 0; i < entities.length; i++)
      for (int j = entities[i].start; j < entities[i].end; j++)
        m[j] = i;
    return m;
  }
  
  public boolean containsVerb(MultiAlphabet a) {
    for (int i = 0; i < parse.length; i++) {
      String pos = a.pos(parse[i].pos);
      if (pos.startsWith("V"))
        return true;
    }
    return false;
  }
  
  public int numContentWords(MultiAlphabet a) {
    int c = 0;
    for (int i = 0; i < parse.length; i++) {
      String pos = a.pos(parse[i].pos);
      if (pos.startsWith("V")
          || pos.startsWith("N")
          || pos.startsWith("R")
          || pos.startsWith("J")) {
        c++;
      }
    }
    return c;
  }

  public int numNuisanceWords(MultiAlphabet a, boolean countEndOfSentPunc) {
    int c = 0;
    int n = parse.length;
    if (!countEndOfSentPunc)
      n--;
    for (int i = 0; i < n; i++) {
      String pos = a.pos(parse[i].pos);
      if (pos.isEmpty()
          || pos.equals("NFP")
          || pos.equals("HYPH")
          || pos.equals("LS")
          || pos.equals("UH")
          || pos.equals("-LRB-")
          || pos.equals("-RRB-")
          || pos.equals(":")
          || pos.equals(",")
          || pos.equals(".")
          || pos.equals("``")
          || pos.equals("''")
          || pos.equals("SYM")) {
        c++;
      }
    }
    return c;
  }
  
//  public double averageIdf(MultiAlphabet a, ComputeIdf df) {
//    assert parse.length > 0;
//    double u = 0;
//    for (int i = 0; i < parse.length; i++) {
//      String w = a.word(parse[i].word);
//      u += df.idf(w);
//    }
//    double b = 1;
//    return (u + b) / (parse.length + b);
//  }
  public double averageIdf(MultiAlphabet a, ToDoubleFunction<String> idf) {
    assert parse.length > 0;
    double u = 0;
    for (int i = 0; i < parse.length; i++) {
      String w = a.word(parse[i].word);
      u += idf.applyAsDouble(w);
    }
    double b = 1;
    return (u + b) / (parse.length + b);
  }
  
  /**
   * Reads the format with lines matching:
   *   (<midWithSlashMSlash> <mentionSpanStart>-<mentionSpanEnd>)*
   */
  public static Mention[] parseMentions(String mentionLine) {
    String[] a = mentionLine.split("\\s+");
    if (a.length % 2 != 0)
      throw new IllegalArgumentException("mentionLine=" + mentionLine);
    Mention[] entities = new Mention[a.length/2];
    for (int i = 0; i < entities.length; i++) {
      String mid = a[2*i + 0];
      Span s = Span.inverseShortString(a[2*i + 1]);
      
      if (mid.startsWith("/m/"))
        mid = mid.substring(3);
      
      entities[i] = new Mention();
      entities[i].head = -1;
      entities[i].mid = mid.getBytes(UTF8);
      entities[i].setSpan(s);
    }
    return entities;
  }
  
  public String getSpanAsStr(int start, int end, MultiAlphabet a) {
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < end; i++) {
      if (i > start) sb.append(' ');
      sb.append(a.word(parse[i].word));
    }
    return sb.toString();
  }
  
  public void showConllStyle(MultiAlphabet a) {
    String[] mids = new String[parse.length];
    Arrays.fill(mids, "_");
    for (int i = 0; i < entities.length; i++) {
      for (int j = entities[i].start; j < entities[i].end; j++) {
        mids[j] = entities[i].getFullMid();
        if (j == entities[i].head)
          mids[j] += " *head";
      }
    }
    for (int i = 0; i < parse.length; i++) {
//      System.out.printf("% 4d  %-16s  %-6s  %s\n",
//          i, a.word(parse[i].word), a.pos(parse[i].pos), mids[i]);
      String w = a.word(parse[i].word);
      int h = parse[i].depParentNode;
      String edge = a.dep(parse[i].depParentLabel)
          + "(" + (h < 0 ? "ROOT" : a.word(parse[h].word))
          + "," + w + ")";
      System.out.printf("% 4d  %-18s  %-6s  % 4d  %-26s  %s\n",
          i, a.word(parse[i].word), a.pos(parse[i].pos), h, edge, mids[i]);
    }
  }
  
  public void showChunkedStyle(MultiAlphabet a) {
    String[] mids = new String[parse.length];
    Arrays.fill(mids, "_");
    for (int i = 0; i < entities.length; i++) {
      for (int j = entities[i].start; j < entities[i].end; j++) {
        mids[j] = entities[i].getFullMid();
        if (j == entities[i].head)
          mids[j] += " *head";
      }
    }
    String prevMid = entities[0].start > 0 ? "_" : mids[0];
    System.out.printf("% 3d", 0);
    for (int i = 0; i < parse.length; i++) {
      boolean n = i > 0 && !mids[i].equals(mids[i-1]);
      if (n) {
        System.out.println("\t" + prevMid);
        prevMid = mids[i];
        System.out.printf("% 3d", i);
      }
      System.out.printf(" %s", a.word(parse[i].word));
    }
    System.out.println("\t" + prevMid);
  }
  
  public static void showConllStyle(List<EffSent> sentences, MultiAlphabet a) {
    if (sentences.isEmpty())
      Log.info("no sentences!");
    int i = 0;
    for (EffSent s : sentences) {
      Log.info("sentence(" + (i++) + "):");
      s.showConllStyle(a);
      System.out.println();
    }
  }
  
  public String showWithMidHighlighted(String mid, MultiAlphabet a) {
    boolean[] open = new boolean[parse.length];
    boolean[] close = new boolean[parse.length];
    for (int i = 0; i < entities.length; i++) {
      if (mid.equals(entities[i].getFullMid())) {
        assert !open[entities[i].start];
        assert !close[entities[i].end-1];
        open[entities[i].start] = true;
        close[entities[i].end-1] = true;
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parse.length; i++) {
      if (i > 0)
        sb.append(' ');
      if (open[i])
        sb.append("<span class=\"subj\">");
      sb.append(a.word(parse[i].word));
      if (close[i])
        sb.append("</span>");
    }
    return sb.toString();
  }

  public class HashEqOnParse {
    public final long hash;

    public HashEqOnParse(long seed) {
      hash = DepNode.hash64(parse, seed, true, true, true, true);
    }
    
    public EffSent orig() { return EffSent.this; }
    public DepNode[] parse() { return parse; }
    
    @Override
    public int hashCode() {
      return (int) hash;
    }
    
    @Override
    public boolean equals(Object other) {
      if (other instanceof HashEqOnParse) {
        HashEqOnParse x = (HashEqOnParse) other;
        return hash == x.hash && DepNode.equals(parse, x.parse());
      }
      return false;
    }
  }
  
  /**
   * Raw/strings is twice as fast as jser!
   *
   * # no gzip
   * write/jser/array     <Timer write/jser/array 11.56 sec and 20 calls total, log(sec/call)=-0.5 sec/call=0.582>
   * read/jser/list       <Timer read/jser/list 7.08 sec and 20 calls total, log(sec/call)=-1.0 sec/call=0.352>
   * read/jser/array      <Timer read/jser/array 6.58 sec and 20 calls total, log(sec/call)=-1.1 sec/call=0.331>
   * read/raw             <Timer read/raw 3.51 sec and 20 calls total, log(sec/call)=-1.8 sec/call=0.159>
   * write/jser/alph      <Timer write/jser/alph 2.45 sec and 20 calls total, log(sec/call)=-2.1 sec/call=0.119>
   * read/jser/alph       <Timer read/jser/alph 1.26 sec and 20 calls total, log(sec/call)=-2.8 sec/call=0.058>
   * total: 44.771
   * 
   * jser does lead to smaller files though:
   * 
   * caligula fnparse $ du -sch data/facc1-entsum/code-testing-data/m.01ztq_/debug-out/*
   * 3.2M    data/facc1-entsum/code-testing-data/m.01ztq_/debug-out/eff-sents.array.jser
   * 3.2M    data/facc1-entsum/code-testing-data/m.01ztq_/debug-out/eff-sents.list.jser
   * 384K    data/facc1-entsum/code-testing-data/m.01ztq_/debug-out/parse-alph.jser
   * 6.8M    total
   * caligula fnparse $ du -sch data/facc1-entsum/code-testing-data/m.01ztq_/parse.conll data/facc1-entsum/code-testing-data/m.01ztq_/mentionLocs.txt 
   * 4.6M    data/facc1-entsum/code-testing-data/m.01ztq_/parse.conll
   * 256K    data/facc1-entsum/code-testing-data/m.01ztq_/mentionLocs.txt
   * 4.8M    total
   * 
   * gzip makes it a little closer though:
   * 
   * # gzipOut=true gzipIn=false
   * write/jser/array     <Timer write/jser/array 8.84 sec and 20 calls total, log(sec/call)=-0.8 sec/call=0.443>
   * read/jser/list       <Timer read/jser/list 4.90 sec and 20 calls total, log(sec/call)=-1.4 sec/call=0.238>
   * read/jser/array      <Timer read/jser/array 4.82 sec and 20 calls total, log(sec/call)=-1.4 sec/call=0.241>
   * read/raw             <Timer read/raw 3.35 sec and 20 calls total, log(sec/call)=-1.9 sec/call=0.151>
   * write/jser/alph      <Timer write/jser/alph 1.87 sec and 20 calls total, log(sec/call)=-2.4 sec/call=0.088>
   * read/jser/alph       <Timer read/jser/alph 0.97 sec and 20 calls total, log(sec/call)=-3.2 sec/call=0.042>
   * total: 34.18
   * 
   * gzip on the input side uses 5x less space in the same time:
   * 
   * write/jser/array     <Timer write/jser/array 8.95 sec and 20 calls total, log(sec/call)=-0.8 sec/call=0.447>
   * read/jser/list       <Timer read/jser/list 5.02 sec and 20 calls total, log(sec/call)=-1.4 sec/call=0.246>
   * read/jser/array      <Timer read/jser/array 4.94 sec and 20 calls total, log(sec/call)=-1.4 sec/call=0.247>
   * read/raw             <Timer read/raw 3.54 sec and 20 calls total, log(sec/call)=-1.8 sec/call=0.159>
   * write/jser/alph      <Timer write/jser/alph 1.91 sec and 20 calls total, log(sec/call)=-2.4 sec/call=0.088>
   * read/jser/alph       <Timer read/jser/alph 1.03 sec and 20 calls total, log(sec/call)=-3.1 sec/call=0.046>
   * total: 34.674
   * 
   * caligula m.01ztq_ $ du -sch parse.conll mentionLocs.txt
   * 4.6M    parse.conll
   * 256K    mentionLocs.txt
   * 4.8M    total
   * caligula m.01ztq_ $ du -sch parse.conll.gz mentionLocs.txt.gz 
   * 872K    parse.conll.gz
   * 64K     mentionLocs.txt.gz
   * 936K    total
   */
  public static void benchmark(ExperimentProperties config) throws Exception {
    
    int times = config.getInt("times", 20);
    boolean gzipOut = config.getBoolean("gzipOut", true);
    boolean gzipIn = config.getBoolean("gzipIn", true);
    
    FileUtil.VERBOSE = true;

    String suf = gzipOut ? ".jser.gz" : ".jser";
    File p = new File("data/facc1-entsum/code-testing-data/m.01ztq_/");
    File conll = new File(p, "parse.conll" + (gzipIn ? ".gz" : ""));
    File mentions = new File(p, "mentionLocs.txt" + (gzipIn ? ".gz" : ""));
    MultiTimer t = new MultiTimer();
    for (int i = 0; i < times; i++) {
      Log.info("starting iter=" + i);

      // Build
      MultiAlphabet a = new MultiAlphabet();
      List<EffSent> sentences = new ArrayList<>();
      t.start("read/raw");
      try (DepNode.ConllxFileReader iter = new DepNode.ConllxFileReader(conll, a, false);
          BufferedReader r = FileUtil.getReader(mentions)) {
        while (iter.hasNext()) {
          DepNode[] parse = iter.next();
          String ms = r.readLine();
          EffSent s = new EffSent(parse);
          boolean setHeads = true;
          s.setMentions(ms, setHeads);
          sentences.add(s);
        }
      }
      t.stop("read/raw");
      Log.info("read " + sentences.size() + " sentences");

      // Save
      File out = new File(p, "debug-out");
      out.mkdirs();
      t.start("write/jser/alph");
      FileUtil.serialize(a, new File(out, "parse-alph" + suf));
      t.stop("write/jser/alph");
      t.start("write/jser/list");
      FileUtil.serialize(sentences, new File(out, "eff-sents.list" + suf));
      t.stop("write/jser/list");
      t.start("write/jser/array");
      FileUtil.serialize(sentences.toArray(), new File(out, "eff-sents.array" + suf));
      t.stop("write/jser/array");

      // Read back
      t.start("read/jser/alph");
      FileUtil.deserialize(new File(out, "parse-alph" + suf));
      t.stop("read/jser/alph");
      t.start("read/jser/list");
      FileUtil.deserialize(new File(out, "eff-sents.list" + suf));
      t.stop("read/jser/list");
      t.start("read/jser/array");
      FileUtil.deserialize(new File(out, "eff-sents.array" + suf));
      t.stop("read/jser/array");

      Log.info(t);
    }
    Log.info("done");
  }
  
//  public static void main(String[] args) throws Exception {
//    ExperimentProperties config = ExperimentProperties.init(args);
////    benchmark(config);
////    debug();
//  }
}
