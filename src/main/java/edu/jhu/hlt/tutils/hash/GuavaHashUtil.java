package edu.jhu.hlt.tutils.hash;

import java.nio.charset.Charset;
import java.util.Arrays;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public class GuavaHashUtil {

  /**
   * Guava's version of this returns a hash function with the
   * seed set to the time! This is a stable version of that method.
   */
  public static HashFunction goodFastHash(int minimumBits, int seed) {
    int bits = checkPositiveAndMakeMultipleOf32(minimumBits);

    if (bits == 32) {
//      return Murmur3_32Holder.GOOD_FAST_HASH_FUNCTION_32;
      return Hashing.murmur3_32(seed);
    }
    if (bits <= 128) {
//      return Murmur3_128Holder.GOOD_FAST_HASH_FUNCTION_128;
      return Hashing.murmur3_128(seed);
    }

    // Otherwise, join together some 128-bit murmur3s
    int hashFunctionsNeeded = (bits + 127) / 128;
    HashFunction[] hashFunctions = new HashFunction[hashFunctionsNeeded];
//    hashFunctions[0] = Murmur3_128Holder.GOOD_FAST_HASH_FUNCTION_128;
    hashFunctions[0] = Hashing.murmur3_128(seed);
//    int seed = GOOD_FAST_HASH_SEED;
    for (int i = 1; i < hashFunctionsNeeded; i++) {
      seed += 1500450271; // a prime; shouldn't matter
      hashFunctions[i] = Hashing.murmur3_128(seed);
    }
//    return new ConcatenatedHashFunction(hashFunctions);
    return Hashing.concatenating(Arrays.asList(hashFunctions));
  }

  /**
   * Checks that the passed argument is positive, and ceils it to a multiple of 32.
   */
  static int checkPositiveAndMakeMultipleOf32(int bits) {
    if (bits <= 0)
      throw new IllegalArgumentException("Number of bits must be positive");
    return (bits + 31) & ~31;
  }
  
  
  public static void main(String[] args) {
    int nhash = 10;
    int logb = 20;
    
    int neededBits = 8 * nhash * logb;
    System.out.println("needed bits: " + neededBits);
    System.out.println("needed bytes: " + nhash*logb);
    HashFunction hf1 = Hashing.goodFastHash(neededBits);
    HashFunction hf2 = goodFastHash(neededBits, 9001);
    
    Charset cs = Charset.forName("UTF8");
    byte[] b1 = hf1.hashString("hello", cs).asBytes();
    byte[] b2 = hf2.hashString("hello", cs).asBytes();
    System.out.println(b1.length);
    System.out.println(b2.length);
    System.out.println(Arrays.toString(b1));
    System.out.println(Arrays.toString(b2));
  }
}
