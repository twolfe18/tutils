package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.tutils.concrete.ConcreteUtil;

/**
 * Holds tf not idf.
 */
public class StringTermVec implements Iterable<Entry<String, Double>>, Serializable {
  private static final long serialVersionUID = -284690402445952436L;

  private Map<String, Double> tf;
  private double z;
  
  public StringTermVec() {
    tf = new HashMap<>();
    z = 0;
  }
  
  public StringTermVec(Communication c, boolean normalizeNumbers) {
    this();
    if (c == null)
      throw new IllegalArgumentException();
    for (String s : ConcreteUtil.terms(c, normalizeNumbers))
      add(s, 1);
  }
  
  public Iterable<String> getKeys() {
    return tf.keySet();
  }
  
  public Double getCount(String word) {
    return tf.get(word);
  }
  
  public Double getProb(String word) {
    Double t = tf.get(word);
    if (t == null)
      return null;
    return t / z;
  }
  
  public double getTotalCount() {
    return z;
  }

  public void add(StringTermVec other) {
    for (Entry<String, Double> e : other.tf.entrySet())
      add(e.getKey(), e.getValue());
  }
  
  public void add(String word, double count) {
    double prev = tf.getOrDefault(word, 0d);
    tf.put(word, prev + count);
    z += count;
  }

  @Override
  public Iterator<Entry<String, Double>> iterator() {
    return tf.entrySet().iterator();
  }
}