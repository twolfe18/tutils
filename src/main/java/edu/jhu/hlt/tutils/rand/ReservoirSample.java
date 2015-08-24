package edu.jhu.hlt.tutils.rand;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ReservoirSample {

  public static <T> List<T> sample(Iterator<T> stream, int k, Random rand) {
    List<T> res = new ArrayList<>();
    int taken = 0;
    while (stream.hasNext()) {
      T n = stream.next();
      taken++;
      if (taken <= k)
        res.add(n);
      else {
        int i = rand.nextInt(taken);
        if (i < k)
          res.set(i, n);
      }
    }
    return res;
  }

  public static <T> T sampleOne(Iterator<T> stream, Random rand) {
    return sample(stream, 1, rand).get(0);
  }
}
