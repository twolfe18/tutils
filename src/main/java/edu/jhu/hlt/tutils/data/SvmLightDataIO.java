package edu.jhu.hlt.tutils.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.prim.vector.IntDoubleUnsortedVector;

/**
 * TODO Writer methods.
 *
 * @author travis
 */
public class SvmLightDataIO {

  public static class Example {
    public boolean y;
    public IntDoubleUnsortedVector x;
    public String comment;

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
  }

  public static List<Example> parse(File f, boolean storeComment) throws IOException {
    List<Example> l = new ArrayList<>();
    try (BufferedReader r = FileUtil.getReader(f)) {
      while (r.ready()) {
        String line = r.readLine();
        l.add(new Example(line, storeComment));
      }
    }
    return l;
  }

}
