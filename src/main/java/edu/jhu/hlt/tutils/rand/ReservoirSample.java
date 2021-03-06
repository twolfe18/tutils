package edu.jhu.hlt.tutils.rand;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ReservoirSample<T> implements Iterable<T> {
  
  private Random rand;
  private List<T> reservoir;
  private int k;  // max res size
  private int n;  // number of observations
  
  public ReservoirSample(int k, Random rand) {
    this.rand = rand;
    this.reservoir = new ArrayList<>(k);
    this.k = k;
    this.n = 0;
  }

  public ReservoirSample(int k, Random rand, Iterable<T> items) {
    this(k, rand, items.iterator());
  }

  public ReservoirSample(int k, Random rand, Iterator<T> items) {
    this(k, rand);
    while (items.hasNext())
      add(items.next());
  }
  
  public boolean add(T item) {
    n++;
    if (reservoir.size() < k) {
      reservoir.add(item);
      return true;
    }
    int i = rand.nextInt(n);
    if (i < k) {
      reservoir.set(i, item);
      return true;
    }
    return false;
  }
  
  public List<T> toList() {
    List<T> all = new ArrayList<>();
    all.addAll(reservoir);
    return all;
  }
  
  public T get(int i) {
    return reservoir.get(i);
  }
  
  public int size() {
    return reservoir.size();
  }
  
  public int capactity() {
    return k;
  }
  
  public int numObservations() {
    return n;
  }
  
  public Random getRandom() {
    return rand;
  }

  @Override
  public Iterator<T> iterator() {
    return reservoir.iterator();
  }

  /**
   * NOTE: If you're not using an iterator, you're probably not getting the full
   * benefit of a reservoir sample.
   */
  public static <T> List<T> sample(Iterable<T> stream, int k, Random rand) {
    return sample(stream.iterator(), k, rand);
  }

  public static <T> List<T> sample(Iterator<T> stream, int k, Random rand) {
    List<T> res = new ArrayList<>();
    int taken = 0;
    while (stream.hasNext()) {
      T n = stream.next();
      taken++;
      if (taken <= k)
        res.add(n);
      else {
        int i = rand.nextInt(taken);
        if (i < k)
          res.set(i, n);
      }
    }
    return res;
  }

  /**
   * NOTE: If you're not using an iterator, you're probably not getting the full
   * benefit of a reservoir sample.
   */
  public static <T> T sampleOne(Iterable<T> stream, Random rand) {
    return sample(stream, 1, rand).get(0);
  }

  public static <T> T sampleOne(Iterator<T> stream, Random rand) {
    return sample(stream, 1, rand).get(0);
  }
}
