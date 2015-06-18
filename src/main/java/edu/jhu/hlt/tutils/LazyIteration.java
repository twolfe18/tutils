package edu.jhu.hlt.tutils;

import java.util.Iterator;
import java.util.function.Function;

public class LazyIteration {

  /** new FIterator(itr, f) is just map(itr, f) */
  public static class FIterator<S, T> implements Iterator<T> {
    private final Function<S, T> func;
    private final Iterator<S> base;
    public FIterator(Iterator<S> base, Function<S, T> func) {
      this.base = base;
      this.func = func;
    }
    @Override
    public boolean hasNext() {
      return base.hasNext();
    }
    @Override
    public T next() {
      S s = base.next();
      return func.apply(s);
    }
  }

  /** new FIterable(itr, f) is just map(itr, f) */
  public static class FIterable<S, T> implements Iterable<T> {
    private final Function<S, T> func;
    private final Iterable<S> base;
    public FIterable(Iterable<S> base, Function<S, T> func) {
      this.base = base;
      this.func = func;
    }
    @Override
    public Iterator<T> iterator() {
      return new FIterator<>(base.iterator(), func);
    }
  }

  // flatmap
  public static class FlatIterator<T> implements Iterator<T> {
    private Iterator<? extends Iterable<T>> base;
    private Iterator<T> cur;
    public FlatIterator(Iterator<? extends Iterable<T>> itr) {
      base = itr;
      if (base.hasNext())
        cur = base.next().iterator();
    }
    @Override
    public boolean hasNext() {
      return cur != null && cur.hasNext();
    }
    @Override
    public T next() {
      T n = cur.next();
      if (!cur.hasNext() && base.hasNext())
        cur = base.next().iterator();
      return n;
    }
  }

  // flatmap
  public static class FlatIterable<T> implements Iterable<T> {
    private Iterable<? extends Iterable<T>> base;
    public FlatIterable(Iterable<? extends Iterable<T>> itr) {
      base = itr;
    }
    @Override
    public Iterator<T> iterator() {
      return new FlatIterator<T>(base.iterator());
    }
  }

}
