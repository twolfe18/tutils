package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MultiMapUniq<K,V> implements Serializable {
  private static final long serialVersionUID = 1103641130020446308L;

  private HashMap<K, HashSet<V>> map;
  private int numEntries;

  public MultiMapUniq() {
    map = new HashMap<>();
    numEntries = 0;
  }
  
  public Collection<K> keys() {
    return map.keySet();
  }
  
  public boolean containsKey(String key) {
    return map.containsKey(key);
  }
  
  public void add(K key, V value) {
    HashSet<V> vals = map.get(key);
    if (vals == null) {
      vals = new HashSet<>();
      map.put(key, vals);
    }
    if (vals.add(value))
      numEntries++;
  }
  
  public Set<V> get(K key) {
    Set<V> vals = map.get(key);
    if (vals == null)
      vals = Collections.emptySet();
    return vals;
  }
  
  public int numKeys() {
    return map.size();
  }
  
  public int numEntries() {
    return numEntries;
  }
}
