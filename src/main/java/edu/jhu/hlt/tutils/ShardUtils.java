package edu.jhu.hlt.tutils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.ToIntFunction;

public class ShardUtils {

  /** Returns (shard, numShards) */
  public static IntPair getShard(ExperimentProperties config) {
    String sKey = "shard";
    String nsKey = "numShards";
    if (config.containsKey(sKey) != config.containsKey(nsKey)) {
      throw new RuntimeException("the \"" + sKey + "\" and \"" + nsKey + "\""
          + " options must be either both used or neither used");
    }
    int s, ns;
    if (config.containsKey(nsKey)) {
      s = config.getInt(sKey);
      ns = config.getInt(nsKey);
    } else {
      s = 0;
      ns = 1;
    }
    return new IntPair(s, ns);
  }

  /** Eager */
  public static <T> ArrayList<T> shard(Iterable<T> all, ToIntFunction<T> hash, IntPair shard) {
    return shard(all, hash, shard.first, shard.second);
  }
  public static <T> ArrayList<T> shard(Iterable<T> all, ToIntFunction<T> hash, int shard, int numShards) {
    ArrayList<T> rel = new ArrayList<>();
    for (T t : all)
      if (Math.floorMod(hash.applyAsInt(t), numShards) == shard)
        rel.add(t);
    return rel;
  }

  /** Lazy */
  public static <T> Iterator<T> shard(Iterator<T> all, ToIntFunction<T> hash, IntPair shard) {
    return shard(all, hash, shard.second, shard.second);
  }
  public static <T> Iterator<T> shard(Iterator<T> all, ToIntFunction<T> hash, int shard, int numShards) {
    Iterator<T> itr = new Iterator<T>() {
      private T next;
      private Iterator<T> itr = all;
      @Override
      public boolean hasNext() {
        return next != null;
      }
      @Override
      public T next() {
        T t = next;
        while (itr.hasNext()) {
          T n = itr.next();
          int h = hash.applyAsInt(n);
          if (Math.floorMod(h, numShards) == shard) {
            next = n;
            break;
          }
        }
        return t;
      }
    };
    itr.next();   // initialize next
    return itr;
  }
}
