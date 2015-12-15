package edu.jhu.hlt.tutils;

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
}
