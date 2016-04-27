package edu.jhu.hlt.tutils;

import java.util.Arrays;

public class StringUtils {

  public static String trunc(Object x, int limit) {
    return trunc(x.toString(), limit);
  }

  public static String trunc(String s, int limit) {
    if (s.length() <= limit)
      return s;
    return s.substring(0, limit) + "...";
  }

  // Java String.join requires string elements...
  public static <T> String join(String sep, T[] items) {
    return join(sep, Arrays.asList(items));
  }

  public static String join(String sep, int[] items) {
    if (items.length == 0)
      return "";
    StringBuilder sb = new StringBuilder();
    sb.append(items[0]);
    for (int i = 1; i < items.length; i++) {
      sb.append(sep);
      sb.append(items[i]);
    }
    return sb.toString();
  }

  // Java String.join requires string elements...
  public static String join(String sep, Iterable<?> items) {
    StringBuilder sb = new StringBuilder();
    for (Object i : items) {
      if (sb.length() > 0)
        sb.append(sep);
      sb.append(i.toString());
    }
    return sb.toString();
  }

  public static String trim(String s, int maxLength) {
    if (s.length() <= maxLength)
      return s;
    return s.substring(0, maxLength);
  }
}
