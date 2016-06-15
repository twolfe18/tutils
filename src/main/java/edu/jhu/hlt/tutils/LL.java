package edu.jhu.hlt.tutils;

import java.util.ArrayList;
import java.util.List;

public class LL<T> {

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
    List<T> l = new ArrayList<>();
    for (LL<T> cur = this; cur != null; cur = cur.next)
      l.add(cur.item);
    return l;
  }
}
