package edu.jhu.hlt.tutils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.ToIntFunction;

public class ShardUtils {

  /** Eager */
  public static <T> ArrayList<T> shard(Iterable<T> all, ToIntFunction<T> hash, int shard, int numShards) {
    ArrayList<T> rel = new ArrayList<>();
    for (T t : all)
      if (Math.floorMod(hash.applyAsInt(t), numShards) == shard)
        rel.add(t);
    return rel;
  }

  /** Lazy */
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
