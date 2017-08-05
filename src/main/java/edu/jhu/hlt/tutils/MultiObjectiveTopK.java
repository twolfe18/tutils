package edu.jhu.hlt.tutils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import com.google.common.collect.Iterators;

import edu.jhu.prim.tuple.Pair;

/**
 * Stores the top K elements for B different buckets (each bucket corresponding to
 * its own objective function or measure on items). Items are stored in at most one
 * bucket. Items are added to the bucket which has the highest objective function.
 * 
 * Objective values may have negative infinity values, but not positive infinity or NaN.
 *
 * @param <T> is the type of the item stored. It must be comparable and hash/equals-able
 * because internally a hashmap is used. Additionally, ties in the scoring function are
 * broken by Comparable<T>, keeping higher/last over lower/earlier elements.
 *
 * @author travis
 */
public class MultiObjectiveTopK<T extends Comparable<T>> implements Iterable<Pair<T, Double>> {
  
  public static class Bucket<R extends Comparable<R>> {
    private MostFrequentKeys<R> topk;
    private ToDoubleFunction<R> objective;

    public Bucket(int capacity, ToDoubleFunction<R> obj) {
      this.topk = new MostFrequentKeys<>(capacity);
      this.objective = obj;
    }

    void add(R item, double score) {
      topk.add(item, score);
    }

    void remove(R item) {
      topk.remove(item);
    }
  }
  
  private Bucket<T>[] buckets;
  private Map<T, Bucket<T>> whichBucket;
  private int capacity;
  
  @SuppressWarnings("unchecked")
  public MultiObjectiveTopK(int itemsPerBucket, List<ToDoubleFunction<T>> objectives) {
    buckets = new Bucket[objectives.size()];
    for (int i = 0; i < buckets.length; i++)
      buckets[i] = new Bucket<>(itemsPerBucket, objectives.get(i));
    whichBucket = new HashMap<>();
    capacity = itemsPerBucket * buckets.length;
  }
  
  public void add(T item) {
    // Remove item if it exists already
    Bucket<T> br = whichBucket.remove(item);
    if (br != null)
      br.remove(item);
    
    // Find the bucket with the highest objective
    ArgMax<Bucket<T>> a = new ArgMax<>();
    for (int i = 0; i < buckets.length; i++) {
      Bucket<T> b = buckets[i];
      double y = b.objective.applyAsDouble(item);
      assert !Double.isNaN(y);
      if (Double.isInfinite(y)) {
        assert y < 0;
        continue;
      }
      a.offer(b, y);
    }
    if (a.numOffers() > 0) {
      Bucket<T> bb = a.get();
      bb.add(item, a.getBestScore());
      whichBucket.put(item, bb);
    }
  }
  
  public int capacity() {
    return capacity;
  }
  
  public int size() {
    return whichBucket.size();
  }

  @Override
  public Iterator<Pair<T, Double>> iterator() {
    Iterator<Pair<T, Double>> iter = buckets[0].topk.iterator();
    for (int i = 1; i < buckets.length; i++)
      iter = Iterators.concat(iter, buckets[i].topk.iterator());
    return iter;
  }
}
