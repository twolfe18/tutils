package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

import edu.jhu.hlt.tutils.rand.ReservoirSample;

/**
 * A map which associates a list of values with a given key.
 * This class provides help adding and getting values from such a map.
 *
 * @author travis
 */
public class MultiMap<K, V> implements Serializable {
  private static final long serialVersionUID = 7103943293042096120L;

  private HashMap<K, List<V>> map;
  private int numEntries;
  
  public MultiMap() {
    map = new HashMap<>();
  }
  
  @Override
  public String toString() {
    return "(MM keys=" + map.size() + " entries=" + numEntries + ")";
  }
  
  public K sampleKeyBasedOnNumEntries(Random rand) {
    return sampleKeyBasedOnNumEntries(rand, i -> (double) i);
  }

  public K sampleKeyBasedOnNumEntries(Random rand, IntToDoubleFunction numValuesToWeight) {
    if (map.isEmpty())
      throw new IllegalStateException("map is empty, can't sample");
    // Compute Z
    double sum = 0;
    for (Entry<K, List<V>> e : map.entrySet()) {
      int numVals = e.getValue().size();
      double weight = numValuesToWeight.applyAsDouble(numVals);
      if (weight < 0)
        throw new IllegalArgumentException("negative weights not allowed: f(" + numVals + ")=" + weight);
      sum += weight;
    }
    double thresh = rand.nextDouble() * sum;
    double running = 0;
    for (Entry<K, List<V>> e : map.entrySet()) {
      int numVals = e.getValue().size();
      double weight = numValuesToWeight.applyAsDouble(numVals);
      if (weight < 0)
        throw new IllegalArgumentException("negative weights not allowed: f(" + numVals + ")=" + weight);
      running += weight;
      if (running >= thresh)
        return e.getKey();
    }
    throw new RuntimeException("problem: sum=" + sum + " numEntries=" + numEntries + " thresh=" + thresh);
  }

  /** provided function chooses a key, values are chosen uniformly at random */
  public Entry<K,V> sampleAndRemoveEntryBasedOnNumEntries(Random rand, IntToDoubleFunction numValuesToWeight) {
    if (map.isEmpty())
      throw new IllegalStateException("map is empty, can't sample");
    // Compute Z
    double sum = 0;
    for (Entry<K, List<V>> e : map.entrySet()) {
      int numVals = e.getValue().size();
      double weight = numValuesToWeight.applyAsDouble(numVals);
      if (weight < 0)
        throw new IllegalArgumentException("negative weights not allowed: f(" + numVals + ")=" + weight);
      sum += weight;
    }
    double thresh = rand.nextDouble() * sum;
    double running = 0;
    for (Entry<K, List<V>> e : map.entrySet()) {
      int numVals = e.getValue().size();
      double weight = numValuesToWeight.applyAsDouble(numVals);
      if (weight < 0)
        throw new IllegalArgumentException("negative weights not allowed: f(" + numVals + ")=" + weight);
      running += weight;
      if (running >= thresh) {
        V val = ReservoirSample.sampleOne(e.getValue(), rand);
        boolean r = e.getValue().remove(val);
        assert r;
        numEntries--;
        assert numEntries >= 0;
        return new Map.Entry<K,V>() {
          private K key = e.getKey();
          private V v = val;
          @Override
          public K getKey() {
            return key;
          }
          @Override
          public V getValue() {
            return v;
          }
          @Override
          public V setValue(V value) {
            throw new UnsupportedOperationException();
          }
        };
      }
    }
    throw new RuntimeException("problem: sum=" + sum + " numEntries=" + numEntries + " thresh=" + thresh);
  }
  
  public void hashDedupValues() {
    List<K> keys = new ArrayList<>(map.keySet());
    for (K k : keys) {
      List<V> vals = map.get(k);
      HashSet<V> uniq = new HashSet<>();
      uniq.addAll(vals);
      if (uniq.size() < vals.size())
        map.put(k, new ArrayList<>(uniq));
    }
  }
  
  public <T extends Collection<V>> T project(Collection<K> from, T to) {
    for (K k : from)
      for (V v : get(k))
        to.add(v);
    return to;
  }

  public void sortValues(Comparator<V> c) {
    for (List<V> l : map.values())
      Collections.sort(l, c);
  }
  
  public int numKeys() {
    return map.size();
  }
  
  public int numEntries() {
    return numEntries;
  }
  
  public List<V> get(K key) {
    List<V> v = map.get(key);
    if (v == null)
      v = Collections.emptyList();
    return v;
  }
  
  public List<V> remove(K key) {
    List<V> r = map.remove(key);
    if (r != null)
      numEntries -= r.size();
    return r;
  }
  
  public boolean add(K key, V value) {
    List<V> vals = map.get(key);
    if (vals == null) {
      vals = new ArrayList<>();
      map.put(key, vals);
    }
    numEntries++;
    return vals.add(value);
  }
  
  public boolean addIfNotPresent(K key, V value) {
    List<V> vals = map.get(key);
    if (vals == null) {
      vals = new ArrayList<>();
      map.put(key, vals);
    }
    if (vals.contains(value))
      return false;
    numEntries++;
    return vals.add(value);
  }
  
  public boolean containsKey(K key) {
    return map.containsKey(key);
  }
  
  public Iterable<K> keySet() {
    return map.keySet();
  }
}
