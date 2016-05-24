package edu.jhu.hlt.tutils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.ToIntFunction;

public class ShardUtils {

  public static class Shard extends IntPair {
    private static final long serialVersionUID = 8002427683485461635L;

    public static final Shard ONLY = new Shard(0, 1);

    public Shard(int shard, int numShards) {
      super(shard, numShards);
      if (shard < 0 || shard >= numShards || numShards < 1)
        throw new IllegalArgumentException("shard=" + shard + " numShards=" + numShards);
    }

    public int getShard() {
      return first;
    }

    public int getNumShards() {
      return second;
    }

    public boolean matches(int i) {
      if (i < 0)
        i = -i;
      return (i % second) == first;
    }

    public boolean matches(Object hashable) {
      return matches(hashable.hashCode());
    }
  }

  /**
   * @deprecated Use {@link ExperimentProperties#getShard(String)}
   * @return (shard, numShards)
   * If "shard" and "numShards" properties are not given, then returns
   * (shard=0, numShards=1), which is equivalent to not having any shards.
   */
  public static Shard getShard(ExperimentProperties config) {
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
    return new Shard(s, ns);
  }

  /** Eager */
  public static <T> ArrayList<T> shard(Iterable<T> all, ToIntFunction<T> hash, Shard shard) {
    return shard(all, hash, shard.first, shard.second);
  }
  public static <T> ArrayList<T> shard(Iterable<T> all, ToIntFunction<T> hash, int shard, int numShards) {
    ArrayList<T> rel = new ArrayList<>();
    for (T t : all) {
      if (t == null)
        throw new IllegalArgumentException("may not have null items to hash");
      if (Math.floorMod(hash.applyAsInt(t), numShards) == shard)
        rel.add(t);
    }
    return rel;
  }
  public static <T> ArrayList<T> shardByIndex(Iterable<T> all, Shard shard) {
    return shardByIndex(all, shard.getShard(), shard.getNumShards());
  }
  public static <T> ArrayList<T> shardByIndex(Iterable<T> all, int shard, int numShards) {
    ArrayList<T> rel = new ArrayList<>();
    int i = 0;
    for (T t : all) {
      if (t == null)
        throw new IllegalArgumentException("may not have null items to hash");
      if (Math.floorMod(i, numShards) == shard)
        rel.add(t);
      i++;
    }
    return rel;
  }

  /** Lazy */
  public static <T> Iterator<T> shard(Iterator<T> all, ToIntFunction<T> hash, Shard shard) {
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
  public static <T> Iterator<T> shardByIndex(Iterator<T> all, Shard shard) {
    return shardByIndex(all, shard.getShard(), shard.getNumShards());
  }
  public static <T> Iterator<T> shardByIndex(Iterator<T> all, int shard, int numShards) {
    Iterator<T> itr = new Iterator<T>() {
      private int i = 0;
      private T next;
      private Iterator<T> itr = all;
      @Override
      public boolean hasNext() {
        return next != null;
      }
      @Override
      public T next() {
        T t = next;
        next = null;
        while (itr.hasNext()) {
          T n = itr.next();
          i++;
          if (Math.floorMod(i, numShards) == shard) {
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
