package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.jhu.prim.tuple.Pair;

public class Feat implements Serializable {
  private static final long serialVersionUID = -2723964704627341786L;

  public static boolean SHOW_REASON_IN_TOSTRING = true;

  public static List<Feat> take(int n, List<Feat> in) {
    n = Math.min(n, in.size());
    List<Feat> out = new ArrayList<>(n);
    for (int i = 0; i < n; i++)
      out.add(in.get(i));
    return out;
  }

  public static List<Feat> exp(List<Feat> in) {
    List<Feat> out = new ArrayList<>(in.size());
    for (Feat i : in)
      out.add(new Feat(i.getName(), Math.exp(i.getWeight())));
    return out;
  }

  /**
   * e.g. ["foo/a":1, "foo/b":3, "bar/d":1] => {"foo":["a":1, "b":3], "bar":["d":1]}
   * Features must contain at least one '/' and the prefix is considered the namespace.
   * Useful for converting to vowpal wabbit feature format.
   */
  public static MultiMap<String, Feat> groupByNamespace(List<Feat> fs, char delim) {
    MultiMap<String, Feat> m = new MultiMap<>();
    for (Feat f : fs) {
      int i = f.getName().indexOf(delim);
      assert i >= 0;
      String ns = f.getName().substring(0, i);
      String rest = f.getName().substring(i+1);
      m.add(ns, new Feat(rest, f.getWeight()));
    }
    return m;
  }

  /** expects duplicates will have the same weight, only removes duplicates rather than summing their weights */
  public static List<Feat> dedup(List<Feat> mayContainRepeatedKeysWithSameValue) {
    List<Feat> fs = new ArrayList<>();
    Map<String, Double> seen = new HashMap<>();
    for (Feat f : mayContainRepeatedKeysWithSameValue) {
      Double p = seen.get(f.getName());
      if (p == null) {
        seen.put(f.getName(), f.getWeight());
        fs.add(f);
      } else {
        assert p == f.getWeight();
      }
    }
    return fs;
  }

  public static List<Feat> aggregateSum(List<Feat> mayContainRepeatedKeysWithSameValue) {
    Map<String, Double> seen = new HashMap<>();
    for (Feat f : mayContainRepeatedKeysWithSameValue) {
      double p = seen.getOrDefault(f.getName(), 0d);
      seen.put(f.getName(), p + f.getWeight());
    }
    List<Feat> fs = new ArrayList<>();
    for (String f : seen.keySet())
      fs.add(new Feat(f, seen.get(f)));
    return fs;
  }

  public static List<Feat> aggregateMax(List<Feat> mayContainRepeatedKeysWithSameValue) {
    Map<String, Double> seen = new HashMap<>();
    for (Feat f : mayContainRepeatedKeysWithSameValue) {
      double p = seen.getOrDefault(f.getName(), Double.NEGATIVE_INFINITY);
      seen.put(f.getName(), Math.max(p, f.getWeight()));
    }
    List<Feat> fs = new ArrayList<>();
    for (String f : seen.keySet())
      fs.add(new Feat(f, seen.get(f)));
    return fs;
  }

  /**
   * @returns (cosineSim, commonFeatures)
   */
  public static Pair<Double, List<Feat>> cosineSim(List<Feat> a, List<Feat> b) {

    double ssa = 0;
    Map<String, Feat> am = index(a);
    for (Feat f : am.values())
      ssa += f.weight * f.weight;
    assert ssa >= 0;

    double ssb = 0;
    Map<String, Feat> bm = index(b);
    for (Feat f : bm.values())
      ssb += f.weight * f.weight;
    assert ssb >= 0;

    double dot = 0;
    List<Feat> common = new ArrayList<>();
    for (Feat f : bm.values()) {
      Feat ff = am.get(f.name);
      if (ff != null) {
        dot += f.weight * ff.weight;
        common.add(new Feat(f.name, f.weight * ff.weight));
      }
    }

    if (dot == 0 || ssa * ssb == 0)
      return new Pair<>(0d, Collections.emptyList());

    double cosineSim = dot / (Math.sqrt(ssa) * Math.sqrt(ssb));
    return new Pair<>(cosineSim, common);
  }

  public static List<Feat> deindex(Iterable<Entry<String, Double>> m) {
    List<Feat> f = new ArrayList<>();
    for (Entry<String, Double> e : m)
      f.add(new Feat(e.getKey(), e.getValue()));
    return f;
  }

  @SafeVarargs
  public static Map<String, Feat> index(List<Feat>... features) {
    Map<String, Feat> c = new HashMap<>();
    for (List<Feat> l : features) {
      for (Feat f : l) {
        Feat e = c.get(f.name);
        if (e == null) {
          c.put(f.name, f);
        } else {
          c.put(f.name, new Feat(f.name, f.weight + e.weight));
        }
      }
    }
    return c;
  }

  /**
   * interprets the two lists as vectors and adds them (combining Feats with the same name by value-addition).
   */
  public static List<Feat> vecadd(List<Feat> a, List<Feat> b) {
    Map<String, Feat> c = new HashMap<>();
    for (List<Feat> l : Arrays.asList(a, b)) {
      for (Feat f : l) {
        Feat e = c.get(f.name);
        if (e == null) {
          c.put(f.name, f);
        } else {
          c.put(f.name, new Feat(f.name, f.weight + e.weight));
        }
      }
    }
    return new ArrayList<>(c.values());
  }

  public static final Comparator<Feat> BY_NAME = new Comparator<Feat>() {
    @Override
    public int compare(Feat o1, Feat o2) {
      return o1.name.compareTo(o2.name);
    }
  };
  public static final Comparator<Feat> BY_SCORE_DESC = new Comparator<Feat>() {
    @Override
    public int compare(Feat o1, Feat o2) {
      assert !Double.isNaN(o1.weight);
      assert Double.isFinite(o1.weight);
      assert !Double.isNaN(o2.weight);
      assert Double.isFinite(o2.weight);
      if (o1.weight > o2.weight)
        return -1;
      if (o2.weight > o1.weight)
        return +1;
      return 0;
    }
  };
  public static final Comparator<Feat> BY_SCORE_ASC = new Comparator<Feat>() {
    @Override
    public int compare(Feat o1, Feat o2) {
      return BY_SCORE_DESC.compare(o2, o1);
    }
  };
  public static final Comparator<Feat> BY_SCORE_MAG_DESC = new Comparator<Feat>() {
    @Override
    public int compare(Feat o1, Feat o2) {
      assert !Double.isNaN(o1.weight);
      assert Double.isFinite(o1.weight);
      assert !Double.isNaN(o2.weight);
      assert Double.isFinite(o2.weight);
      double s1 = Math.abs(o1.weight);
      double s2 = Math.abs(o2.weight);
      if (s1 > s2)
        return -1;
      if (s1 < s2)
        return +1;
      return 0;
    }
  };

  public static String showScore(List<Feat> features, int maxChars) {
    return showScore(features, maxChars, Feat.BY_SCORE_DESC);
  }
  public static String showScore(List<Feat> features, int maxChars, Comparator<Feat> sort) {
    List<Feat> out = new ArrayList<>();
    for (Feat f : features)
      out.add(f);
    //      Collections.sort(out, Feat.BY_SCORE_DESC);
    Collections.sort(out, sort);

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%+.2f", Feat.sum(features)));
    for (int i = 0; i < out.size(); i++) {
      Feat f = out.get(i);
      String app = " " + f.toString();
      String alt = " and " + (out.size()-i) + " more";
      if (sb.length() + app.length() < maxChars) {
        sb.append(app);
      } else {
        sb.append(alt);
        break;
      }
    }
    return sb.toString();
  }

  public static List<Feat> sortAndPruneByRatio(List<Feat> in, double ratioKeep) {
    List<Feat> l = new ArrayList<>();
    double z = 0;
    for (Feat f : in) {
      if (f.weight < 0)
        throw new IllegalArgumentException();
      l.add(f);
      z += f.weight;
    }
    Collections.sort(l, Feat.BY_SCORE_DESC);
    int nkeep = 0;
    double kept = 0;
    while (nkeep < l.size() && kept/z < ratioKeep) {
      kept += l.get(nkeep).weight;
      nkeep++;
    }
    while (l.size() > nkeep)
      l.remove(l.size()-1);
    return l;
  }

  public static List<Feat> sortAndPrune(Map<String, Double> in, double eps) {
    List<Feat> l = new ArrayList<>();
    for (Entry<String,  Double> e : in.entrySet()) {
      l.add(new Feat(e.getKey(), e.getValue()));
    }
    return sortAndPrune(l, eps);
  }

  public static List<Feat> sortAndPrune(Map<String, Double> in, int topk) {
    List<Feat> l = new ArrayList<>();
    for (Entry<String,  Double> e : in.entrySet()) {
      l.add(new Feat(e.getKey(), e.getValue()));
    }
    return sortAndPrune(l, topk);
  }

  public static List<Feat> sortAndPrune(Iterable<Feat> in, double eps) {
    List<Feat> out = new ArrayList<>();
    for (Feat f : in)
      if (Math.abs(f.weight) > eps)
        out.add(f);
    //    Collections.sort(out, Feat.BY_NAME);
    Collections.sort(out, Feat.BY_SCORE_DESC);
    return out;
  }

  public static List<Feat> sortAndPrune(Iterable<Feat> in, int topk) {
    List<Feat> out = new ArrayList<>();
    for (Feat f : in)
      out.add(f);
    Collections.sort(out, Feat.BY_SCORE_DESC);
    while (out.size() > topk)
      out.remove(out.size()-1);
    //    Collections.sort(out, Feat.BY_NAME);
    return out;
  }

  public static List<Feat> sortByMag(Iterable<Feat> in, int topk) {
    List<Feat> out = new ArrayList<>();
    for (Feat f : in)
      out.add(f);
    Collections.sort(out, Feat.BY_SCORE_MAG_DESC);
    if (topk <= 0)
      return out;
    while (out.size() > topk)
      out.remove(out.size()-1);
    return out;
  }

  public static List<String> demote(Iterable<Feat> feats, boolean dedup) {
    Set<String> uniq = new HashSet<>();
    List<String> out = new ArrayList<>();
    for (Feat f : feats)
      if (!dedup || uniq.add(f.name))
        out.add(f.name);
    return out;
  }

  public static List<Feat> promote(double value, Iterable<String> feats) {
    return promote(value, false, feats);
  }

  public static List<Feat> promote(double value, boolean dedup, Iterable<String> feats) {
    Set<String> seen = new HashSet<>();
    List<Feat> out = new ArrayList<>();
    for (String f : feats)
      if (!dedup || seen.add(f))
        out.add(new Feat(f, value));
    return out;
  }

  public static double max(Iterable<Feat> features) {
    double m = 0;
    for (Feat f : features) {
      assert Double.isFinite(f.weight);
      assert !Double.isNaN(f.weight);
      m = Math.max(m, f.weight);
    }
    return m;
  }
  public static double sum(Iterable<Feat> features) {
    double s = 0;
    for (Feat f : features) {
      assert Double.isFinite(f.weight);
      assert !Double.isNaN(f.weight);
      s += f.weight;
    }
    return s;
  }
  public static double avg(Iterable<Feat> features) {
    double s = 0;
    int n = 0;
    for (Feat f : features) {
      assert Double.isFinite(f.weight);
      assert !Double.isNaN(f.weight);
      s += f.weight;
      n++;
    }
    if (n == 0)
      return 0;
    return s / n;
  }

  public String name;
  public double weight;
  public List<String> justifications;    // details which are nice to include, arbitrary values

  public Feat(String name) {
    this.name = name;
  }
  public Feat(String name, double weight) {
    this.name = name;
    this.weight = weight;
    assert !Double.isNaN(this.weight);
    assert Double.isFinite(weight);
  }

  public static Feat prod(Feat a, Feat b) {
    return new Feat(a.name + "*" + b.name, a.weight * b.weight);
  }

  public Feat(Entry<String, Double> e) {
    this(e.getKey(), e.getValue());
  }

  public String getName() {
    return name;
  }
  public double getWeight() {
    return weight;
  }

  public Feat rescale(String reason, double factor) {
    this.weight *= factor;
    addJustification(String.format("rescale[%s]=%.2g", reason, factor));
    assert !Double.isNaN(this.weight);
    assert Double.isFinite(weight);
    return this;
  }

  public Feat setWeight(double w) {
    this.weight = w;
    assert !Double.isNaN(this.weight);
    assert Double.isFinite(weight);
    return this;
  }

  public Feat addJustification(Object... terms) {
    String j = StringUtils.join(" ", terms);
    if (justifications == null)
      justifications = new ArrayList<>();
    justifications.add(j);
    return this;
  }

  @Override
  public String toString() {
    //      String s = String.format("%-20s %.2f", name, weight);
    String s = String.format("%s %.2f", name, weight);
    if (justifications == null || !SHOW_REASON_IN_TOSTRING)
      return s;
    String j = StringUtils.join(", ", justifications);
    //      return String.format("%-26s b/c %s", s, j);
    return String.format("%s b/c %s", s, j);
  }
}