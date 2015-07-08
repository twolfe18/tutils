package edu.jhu.hlt.tutils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lets you take a list of comparable things and print order statistics. For
 * example you could use this class to find the 75th, 90th, 95th, and 99th
 * percentile of response times for some server which needs to have consistent
 * low latency.
 *
 * The methods which don't take arguments use the natural ordering of items and
 * (min, 1st quartile, median, 3rd quartile, max).
 *
 * @author travis
 */
public class OrderStatistics<T> extends ArrayList<T> {
  private static final long serialVersionUID = 1L;
  public static final List<Double> DEFAULT_ORDERS = Arrays.asList(0.0, 0.25, 0.5, 0.75, 1.0);
  public static final List<Double> EXTREME_ORDERS = Arrays.asList(0.5, 0.75, 0.9, 0.95, 0.975, 0.99, 1.0);

  private boolean sorted = false;

  @Override
  public void sort(Comparator<? super T> cmp) {
    super.sort(cmp);
    this.sorted = true;
  }

  @Override
  public boolean add(T item) {
    this.sorted = false;
    return super.add(item);
  }

  public List<T> getOrders() {
    return getOrders(DEFAULT_ORDERS, null);
  }

  public List<T> getOrders(Comparator<? super T> cmp) {
    return getOrders(DEFAULT_ORDERS, cmp);
  }

  public List<T> getOrders(List<Double> orders) {
    return getOrders(orders, null);
  }

  public List<T> getOrders(List<Double> orders, Comparator<? super T> cmp) {
    if (!sorted)
      this.sort(cmp);
    List<T> ret = new ArrayList<>();
    Collections.sort(orders);
    int n = size();
    for (double ord : orders) {
      int i = (int) (ord * n);
      if (i >= n)
        i = n - 1;
      ret.add(get(i));
    }
    return ret;
  }

  public String getOrdersStr() {
    List<T> t = getOrders();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < DEFAULT_ORDERS.size(); i++) {
      if (i > 0)
        sb.append("  ");
      sb.append(String.format("%.1f%%: %s", 100d * DEFAULT_ORDERS.get(i), t.get(i)));
    }
    return sb.toString();
  }

  /**
   * @param orders e.g. [0.5, 0.75, 0.9, 0.95, 0.975, 0.99]
   * @param cmp may be null, then the natural ordering will be used
   * @return
   */
  public String getOrdersStr(List<Double> orders, Comparator<? super T> cmp) {
    List<T> t = getOrders(orders, cmp);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < orders.size(); i++) {
      if (i > 0)
        sb.append(' ');
      sb.append(String.format("%.1f%%: %s", 100d * orders.get(i), t.get(i)));
    }
    return sb.toString();
  }
}
