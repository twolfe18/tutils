package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LL<T> implements Serializable {
  private static final long serialVersionUID = 4477896942985448828L;

  public final T item;
  public final LL<T> next;

  public LL(T item, LL<T> next) {
    this.item = item;
    this.next = next;
  }

  @Override
  public String toString() {
    return item + " -> " + next;
  }

  public LL<T> prepend(T item) {
    return new LL<>(item, this);
  }

  public List<T> toList() {
    return toList(false);
  }
  public List<T> toList(boolean reverse) {
    List<T> l = new ArrayList<>();
    for (LL<T> cur = this; cur != null; cur = cur.next)
      l.add(cur.item);
    if (reverse)
      Collections.reverse(l);
    return l;
  }

  public static <R> List<R> toList(LL<R> ll) {
    return toList(ll, false);
  }
  /**
   * Use this rather than the instance method since this handles null LLs
   */
  public static <R> List<R> toList(LL<R> ll, boolean reverse) {
    List<R> r = new ArrayList<>();
    for (LL<R> cur = ll; cur != null; cur = cur.next)
      r.add(ll.item);
    if (reverse)
      Collections.reverse(r);
    return r;
  }
}
