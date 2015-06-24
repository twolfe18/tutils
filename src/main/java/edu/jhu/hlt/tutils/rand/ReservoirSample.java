package edu.jhu.hlt.tutils.rand;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

public class ReservoirSample {

  public static <T> T[] sample(Iterator<T> stream, int k, Random rand) {
    @SuppressWarnings("unchecked")
    T[] res = (T[]) new Object[k];
    int taken = 0;
    while (stream.hasNext()) {
      T n = stream.next();
      taken++;
      if (taken <= k)
        res[taken - 1] = n;
      else {
        int i = rand.nextInt(taken);
        if (i < k)
          res[i] = n;
      }
    }
    if (taken < k)
      return Arrays.copyOf(res, taken);
    return res;
  }

  public static <T> T sampleOne(Iterator<T> stream, Random rand) {
    return sample(stream, 1, rand)[0];
  }
}
