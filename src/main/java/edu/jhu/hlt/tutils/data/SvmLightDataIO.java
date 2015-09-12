package edu.jhu.hlt.tutils.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.prim.vector.IntDoubleUnsortedVector;
import edu.jhu.prim.vector.IntDoubleVector;

/**
 * Reads SVM Light format.
 * TODO Writer methods.
 *
 * @author travis
 */
public class SvmLightDataIO {

  public static class Example {
    public boolean y;
    public IntDoubleVector x;
    public String comment;

    /* TODO test this!
    public Example(String line, boolean storeComment) {
      // <label> \s <key>:<value>* \s? #<comment>
      int n = line.length();
      int offset = line.indexOf(' ');
      y = Integer.parseInt(line.substring(0, offset)) == 1;
      x = new IntDoubleUnsortedVector();
      while (offset < n) {
        int colon = -1;
        int space = -1;
        int hash = -1;
        // Read an <int>:<double>
        int last = offset + 1;
        for (; last < n; last++) {
          char c = line.charAt(last);
          if (c == ':') {
            colon = last;
          } else if (c == ' ') {
            space = last;
            break;
          } else if (c == '#') {
            assert colon < 0;
            hash = last;
            break;
          }
        }
        offset = last + 1;
        if (colon > 0) {
          if (space < 0) {
            assert last == n;
            space = n;
          }
          x.add(Integer.parseInt(line.substring(offset, colon)),
              Double.parseDouble(line.substring(colon + 1, space)));
        } else if (hash > 0) {
          if (storeComment)
            comment = line.substring(hash + 1);
          break;
        } else {
          throw new RuntimeException("can't parse: " + line);
        }
      }
    }
    */

    public Example(String line, boolean storeComment) {
      int c = line.indexOf('#');
      if (c >= 0) {
        if (storeComment)
          comment = line.substring(c + 1).trim();
        line = line.substring(0, c);
      }
      String[] toks = line.split("\\s+");
      double yy = Double.parseDouble(toks[0]);
      y = yy == 1;
      assert y || yy == -1;
      x = new IntDoubleUnsortedVector(toks.length - 1);
      for (int i = 1; i < toks.length; i++) {
        String[] kv = toks[i].split(":");
        assert kv.length == 2;
        x.add(Integer.parseInt(kv[0]), Double.parseDouble(kv[1]));
      }
    }

    public Example(boolean y, IntDoubleVector x, String comment) {
      this.y = y;
      this.x = x;
      this.comment = comment;
    }

    /** Adds 1 to indices because svm-light doesn't like 0-indexed things */
    public String toSvmLightFormat() {
      StringBuilder sb = new StringBuilder();
      sb.append(y ? "+1" : "-1");
      x.apply((i,v) -> {
        sb.append(" " + (i+1) + ":" + v);
        return v;
      });
      if (comment != null) {
        sb.append('\t');
        sb.append('#');
        sb.append(' ');
        sb.append(comment);
      }
      return sb.toString();
    }
  }

  public static List<Example> parse(File f, boolean storeComment) throws IOException {
    List<Example> l = new ArrayList<>();
    try (BufferedReader r = FileUtil.getReader(f)) {
      for (String line = r.readLine(); line != null; line = r.readLine())
        l.add(new Example(line, storeComment));
    }
    return l;
  }

  public static void write(boolean y, IntDoubleVector x, Writer w) {
    write(y, x, null, w);
  }

  public static void write(boolean y, IntDoubleVector x, String comment, Writer w) {
    Example e = new Example(y, x, comment);
    write(e, w);
  }

  public static void write(Example e, Writer w) {
    try {
      w.write(e.toSvmLightFormat());
      w.write('\n');
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
