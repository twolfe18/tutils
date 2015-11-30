package edu.jhu.hlt.tutils.hash;

public class Hash {

  /**
   * Adapted from Jenkins hash:
   * https://en.wikipedia.org/wiki/Jenkins_hash_function
   */
  public static int mix(int a, int b) {
    int h = 0;

    // Pretends as if you read a and then b
    // a
    h += a & 0xFF;
    h += h << 10;
    h ^= h >> 6;
    h += a & 0xFF00;
    h += h << 10;
    h ^= h >> 6;
    h += a & 0xFF0000;
    h += h << 10;
    h ^= h >> 6;
    h += a & 0xFF000000;
    h += h << 10;
    h ^= h >> 6;
    // b
    h += b & 0xFF;
    h += h << 10;
    h ^= h >> 6;
    h += b & 0xFF00;
    h += h << 10;
    h ^= h >> 6;
    h += b & 0xFF0000;
    h += h << 10;
    h ^= h >> 6;
    h += b & 0xFF000000;
    h += h << 10;
    h ^= h >> 6;

    h += h << 3;
    h ^= h >> 11;
    h += h << 15;
    return h;
  }
}
