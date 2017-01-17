package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public interface Beam<T> extends Iterable<T>, Serializable {

  /**
   * How many items are on the beam.
   */
  public int size();

  /**
   * Maximum number of items allowed on the beam.
   */
  public int width();

  public T pop();
  public T peek();

  public Beam.Item<T> popItem();
  public Beam.Item<T> peekItem();

  /**
   * Returns true if this item was added to the beam.
   */
  public boolean push(T item, double score);

  /**
   * Returns true if this item was added to the beam.
   */
  public boolean push(Beam.Item<T> item);

  public Iterator<Item<T>> itemIterator();

  /**
   * Returns the score of the best thing on the beam.
   * Has undefined behavior if there is nothing on the beam.
   */
  public double maxScore();

  /**
   * Returns the score of the worst thing on the beam.
   * Has undefined behavior if there is nothing on the beam.
   */
  public double minScore();



  default public String show() {
    StringBuilder sb = new StringBuilder();
    sb.append("<Beam " + size() + "/" + width());
    Iterator<Item<T>> it = itemIterator();
    while (it.hasNext()) {
      Item<T> i = it.next();
      sb.append(String.format("\n\t%.2f:%s", i.score, i.item));
    }
    sb.append(">");
    return sb.toString();
  }


  /**
   * BE CAREFUL with {@link Comparator}! a.compareTo(b) == 0  <=> a.equals(b)!
   * This means that if you are putting these into anything that implements
   * {@link Set} and compareTo returns 0, you will lose an {@link Item}!
   */
  public static final class Item<T> implements Comparable<Item<T>>, Serializable {
    private static final long serialVersionUID = 2883199674665691420L;

    private final T item;
    private final double score;
    private final int scoreTiebreaker;     // breaks ties when scores are equal

    public Item(T item, double score, int scoreTiebreaker) {
      this.item = item;
      this.score = score;
      this.scoreTiebreaker = scoreTiebreaker;
    }

    @Override
    public String toString() {
      return String.format("(Beam.Item %s %+.2f)", item, score);
    }

    public T getItem() { return item; }
    public double getScore() { return score; }

    @Override
    public int compareTo(Item<T> o) {
      if (score > o.score)
        return -1;
      if (score < o.score)
        return 1;
      assert (scoreTiebreaker == o.scoreTiebreaker) == (this == o);
      if (scoreTiebreaker > o.scoreTiebreaker)
        return -1;
      if (scoreTiebreaker < o.scoreTiebreaker)
        return 1;
      return 0;
    }
  }

  public static <T> Beam<T> getMostEfficientImpl(int beamSize) {
    if (beamSize == 1)
      return new Beam1<>();
    // I think there is a bug in beam4! Do not use!
//    if (beamSize == 4)
//      return new Beam4<>();
    return new BeamN<>(beamSize);
  }

  public static <T> Beam<T> getUnboundedBeam() {
    return new BeamN<>(-1);
  }

  /**
   * An efficient width-4 beam.
   * TODO Do better testing, I think this may have a bug.
   */
  public static class Beam4<T> implements Beam<T>, Serializable {
    private static final long serialVersionUID = 4372756823072541320L;

    private Item<T> x1, x2, x3, x4;
    private int size = 0;
    private int tieCtr = 0;

    @Override
    public Iterator<T> iterator() {
      throw new RuntimeException("implement me");
    }

    @Override
    public Iterator<Beam.Item<T>> itemIterator() {
      throw new RuntimeException("implement me");
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public int width() {
      return 4;
    }

    @Override
    public T pop() {
      assert size > 0;
      T r = x1.getItem();
      x1 = x2;
      x2 = x3;
      x3 = x4;
      size--;
      return r;
    }

    @Override
    public T peek() {
      assert size > 0;
      return x1.getItem();
    }

    @Override
    public Item<T> popItem() {
      assert size > 0;
      Item<T> r = x1;
      x1 = x2;
      x2 = x3;
      x3 = x4;
      size--;
      return r;
    }

    @Override
    public Item<T> peekItem() {
      assert size > 0;
      return x1;
    }

    @Override
    public boolean push(T item, double score) {
      if (size < 1 || score > x1.getScore()) {
        x4 = x3;
        x3 = x2;
        x2 = x1;
        x1 = new Item<>(item, score, tieCtr++);
        if (size < 4) size++;
        return true;
      } else if (size < 2 || score > x2.getScore()) {
        x4 = x3;
        x3 = x2;
        x2 = new Item<>(item, score, tieCtr++);
        if (size < 4) size++;
        return true;
      } else if (size < 3 || score > x3.getScore()) {
        x4 = x3;
        x3 = new Item<>(item, score, tieCtr++);
        if (size < 4) size++;
        return true;
      } else if (size < 4 || score > x4.getScore()) {
        x4 = new Item<>(item, score, tieCtr++);
        if (size < 4) size++;
        return true;
      } else {
        return false;
      }
    }

    @Override
    public boolean push(Item<T> item) {
      return push(item.item, item.score);
    }

    @Override
    public String toString() {
      return show();
    }

    @Override
    public double maxScore() {
      if (size == 0)
        return Double.NEGATIVE_INFINITY;
      return x1.getScore();
    }

    @Override
    public double minScore() {
      switch (size) {
      case 0:
        return Double.NEGATIVE_INFINITY;
      case 1:
        return x1.getScore();
      case 2:
        return x2.getScore();
      case 3:
        return x3.getScore();
      case 4:
        return x4.getScore();
      default:
        throw new IllegalStateException();
      }
    }
  }

  /**
   * An efficient width-1 beam.
   */
  public static class Beam1<T> implements Beam<T>, Serializable {
    private static final long serialVersionUID = 67275305786744127L;

    private Item<T> x1;

    @Override
    public Iterator<T> iterator() {
      if (x1 == null)
        return Collections.emptyIterator();
      return Arrays.asList(x1.getItem()).iterator();
    }

    @Override
    public Iterator<Beam.Item<T>> itemIterator() {
      if (x1 == null)
        return Collections.emptyIterator();
      return Arrays.asList(x1).iterator();
    }

    @Override
    public int size() {
      if (x1 == null)
        return 0;
      return 1;
    }

    @Override
    public int width() {
      return 1;
    }

    @Override
    public T pop() {
      if (x1 == null)
        throw new RuntimeException();
      T temp = x1.getItem();
      x1 = null;
      return temp;
    }

    @Override
    public Beam.Item<T> popItem() {
      if (x1 == null)
        throw new RuntimeException();
      Item<T> i = x1;
      x1 = null;
      return i;
    }

    @Override
    public T peek() {
      if (x1 == null)
        throw new RuntimeException();
      return x1.getItem();
    }

    @Override
    public Beam.Item<T> peekItem() {
      if (x1 == null)
        throw new RuntimeException();
      return x1;
    }

    @Override
    public boolean push(T item, double score) {
      if (x1 == null || score > x1.getScore()) {
        x1 = new Item<>(item, score, 0);
        return true;
      }
      return false;
    }

    @Override
    public boolean push(Beam.Item<T> item) {
      return push(item.item, item.score);
    }

    @Override
    public String toString() {
      return show();
    }

    @Override
    public double maxScore() {
      if (x1 == null)
        return Double.NEGATIVE_INFINITY;
      return x1.getScore();
    }

    @Override
    public double minScore() {
      if (x1 == null)
        return Double.NEGATIVE_INFINITY;
      return x1.getScore();
    }
  }

  /**
   * A beam implemented by a TreeSet.
   */
  public static class BeamN<T> implements Beam<T>, Serializable {
    private static final long serialVersionUID = 6164712635641696698L;

    private SortedSet<Item<T>> beam;
    private int width;
    private int tieCtr = 0;

    /**
     * @param width can be <=0, in which case this beam is unbounded (a priority queue).
     */
    public BeamN(int width) {
      this.width = width;
      this.beam = new TreeSet<>();  // use Item natural ordering
    }

    /**
     * Returns an evicted value or null if wasn't full.
     */
    public Item<T> push2(T item, double score) {
      Item<T> i = new Item<>(item, score, tieCtr++);
      beam.add(i);
      if (beam.size() > width && width > 0) {
        Item<T> evict = beam.last();
        beam.remove(evict);
        return evict;
      } else {
        return null;
      }
    }

    public boolean push(T item, double score) {
      Item<T> i = new Item<>(item, score, tieCtr++);
      beam.add(i);
      if (beam.size() > width && width > 0) {
        Item<T> evict = beam.last();
        beam.remove(evict);
        return evict.item != item;
      } else {
        return true;
      }
    }

    @Override
    public boolean push(Beam.Item<T> item) {
      return push(item.item, item.score);
    }

    /**
     * Returns the k item with the highest scores and adds them to addTo.
     * Highest score items are added to addTo first.
     */
    public void pop(Collection<T> addTo, int k) {
      for (int i = 0; i < k && beam.size() > 0; i++)
        addTo.add(pop());
    }

    /**
     * Returns the item with the highest score.
     */
    public T pop() {
      if (beam.size() == 0)
        return null;
      Item<T> it = beam.first();
      beam.remove(it);
      return it.getItem();
    }

    @Override
    public Beam.Item<T> popItem() {
      if (beam.size() == 0)
        throw new RuntimeException();
      Item<T> it = beam.first();
      beam.remove(it);
      return it;
    }

    @Override
    public Iterator<T> iterator() {
      return beam.stream().map(i -> i.item).iterator();
    }

    @Override
    public Iterator<Item<T>> itemIterator() {
      return beam.iterator();
    }

    @Override
    public int size() {
      return beam.size();
    }

    @Override
    public int width() {
      return width;
    }

    @Override
    public T peek() {
      if (beam.size() == 0)
        throw new RuntimeException();
      return beam.first().item;
    }

    @Override
    public Beam.Item<T> peekItem() {
      if (beam.size() == 0)
        throw new RuntimeException();
      return beam.first();
    }

    @Override
    public String toString() {
      return show();
    }

    @Override
    public double maxScore() {
      if (beam.isEmpty())
        return Double.NEGATIVE_INFINITY;
      return beam.first().getScore();
    }

    @Override
    public double minScore() {
      if (beam.isEmpty())
        return Double.NEGATIVE_INFINITY;
      return beam.last().getScore();
    }
  }
}
