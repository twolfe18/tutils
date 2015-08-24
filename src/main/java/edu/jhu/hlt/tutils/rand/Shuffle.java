package edu.jhu.hlt.tutils.rand;

import java.util.Random;

public class Shuffle {

  public static int[] permutation(int n, Random rand) {
    int[] pi = new int[n];
    for (int i = 0; i < n; i++) pi[i] = i;
    fisherYates(pi, rand);
    return pi;
  }

  public static void fisherYates(int[] pi, Random rand) {
    for (int i = pi.length - 1; i > 0; i--) {
      int j = rand.nextInt(i);
      int t = pi[i];
      pi[i] = pi[j];
      pi[j] = t;
    }
  }

}
