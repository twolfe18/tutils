package edu.jhu.hlt.tutils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StringUtils {

//  /**
//   * Convert a string to a number between 0 and 1 where 0 means empty string
//   * (i.e. smallest alphabetical string) and 1 means "z" repeated forever.
//   * Is NOT CASE SENSITIVE. Any character which is not a letter is mapped to
//   * a special character which is less than 'a'.
//   */
//  public static double ascii2reals(String text) {
//    text = text.toLowerCase();
//    double val = 0;
//    int n = text.length();
//    for (int i = n-1; i >= 0; i--) {
//      char c = text.charAt(i);
//      if (c >= 97 && c < 97+26) {
//        // [a-z]
//        c -= 97;
//        c += 10;
//        c += 1;
//      } else if (c >= 48 && c < 48+10) {
//        // [0-9]
//        c -= 48;
//        c += 1;
//      } else {
//        c = 0;
//      }
//      double p = c / (26+10+1);
//      assert p >= 0 && p <= 1;
//      val = p + 0.999999999999d * (val / (26+10+1));
//    }
//    return val;
//  }

  public static double ascii2reals(String text) {
    double val = 0;
    int n = text.length();
    for (int i = n-1; i >= 0; i--) {
      char c = text.charAt(i);
      double p = c / 127d;
      assert p >= 0 && p <= 1;
      val = p + 0.999999999999d * (val / 127d);
    }
    return val;
  }

  public static boolean startWithIgnoreCase(String fullString, String maybePrefix) {
    if (maybePrefix.length() > fullString.length())
      return false;
    String f = fullString.substring(0, maybePrefix.length());
    return f.equalsIgnoreCase(maybePrefix);
  }

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
      sb.append(i == null ? "null" : i.toString());
    }
    return sb.toString();
  }

  public static String trim(String s, int maxLength) {
    if (s.length() <= maxLength)
      return s;
    return s.substring(0, maxLength);
  }

  public static String trimPretty(String s, int maxLength) {
    if (s.length() <= maxLength)
      return s;
    return s.substring(0, maxLength-3) + "...";
  }

  public static void main(String[] args) {
    List<String> ss = new ArrayList<>();
    ss.addAll(Arrays.asList("foo", "abc", "", "a", "zz", "zzz", "_z", "z_", "p_2", "p_1", "p_22"));
    ss.add("time");
    ss.add("strength");
    ss.add("Phenomenon_2");
    ss.add("Phenomenon_1");
    Collections.sort(ss, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        double s1 = ascii2reals(o1);
        double s2 = ascii2reals(o2);
        if (s1 < s2)
          return -1;
        if (s1 > s2)
          return +1;
        return 0;
      }
    });
    List<String> ss2 = new ArrayList<>();
    ss2.addAll(ss);
    Collections.sort(ss2);
    for (int i = 0; i < ss.size(); i++) {
      System.out.println(ss.get(i) + "\t" + ascii2reals(ss.get(i)) + "\t" + ss2.get(i) + "\t" + ascii2reals(ss2.get(i)));
    }
  }
}
