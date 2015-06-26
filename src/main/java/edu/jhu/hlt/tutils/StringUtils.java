package edu.jhu.hlt.tutils;

public class StringUtils {

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

}
