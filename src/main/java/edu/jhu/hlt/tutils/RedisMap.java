package edu.jhu.hlt.tutils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import redis.clients.jedis.Jedis;

/**
 * java.util.Map-like wrapper for Redis. This is specialized to the case of
 * String keys and arbitrary values.
 *
 * If <V> is {@link Serializable}, then see {@link SerializationUtils#bytes2t(byte[])}.
 *
 * @author travis
 *
 * @param <V> is the type of the values in this map. Keys must be Strings.
 */
public class RedisMap<V> {

  public static final int NUM_CONNECTION_RETRIES = 50;
  public static final int CONNECTION_RETRY_DELAY_SECONDS = 10;

  // If you want have multiple RedisMaps use a single redis DB, then set this
  // to a unique string for every RedisMap instance.
  private String prefix;

  private String host;
  private int port;
  private int db;
  private transient Jedis redis;

  private Function<V, byte[]> serialize;
  private Function<byte[], V> deserialize;

  public boolean debug = false;

  public RedisMap(ExperimentProperties config,
      Function<V, byte[]> serialize, Function<byte[], V> deserialize) {
    this(config.getString("redis.prefix", null),
        config.getString("redis.host"),
        config.getInt("redis.port"),
        config.getInt("redis.db", 0),
        serialize, deserialize);
  }

  /**
   * @param prefix may be null, or may be used to distinguish entries from
   * multiple RedisMaps in the same redis DB.
   * @param host
   * @param port
   * @param db
   * @param serialize
   * @param deserialize
   */
  public RedisMap(String prefix, String host, int port, int db,
      Function<V, byte[]> serialize, Function<byte[], V> deserialize) {
    this.prefix = prefix;
    this.host = host;
    this.port = port;
    this.db = db;
    this.serialize = serialize;
    this.deserialize = deserialize;
  }

  private Jedis getConnection() {
    if (redis == null) {
      Log.info("connecting host=" + host + " port=" + port
          + " db=" + db + " prefix=" + prefix);
      Exception last = null;
      for (int i = 0; i < NUM_CONNECTION_RETRIES; i++) {
        // Connect
        try {
          redis = new Jedis(host, port);
          redis.select(db);
          break;
        } catch (Exception e) {
          Log.warn("Exception while connecting (" + e.getMessage() + "),"
              + " will try to connect again in " + CONNECTION_RETRY_DELAY_SECONDS
              + " seconds " + (NUM_CONNECTION_RETRIES - (i+1)) + " more times");
          last = e;
        }
        // Sleep
        try {
          Thread.sleep(CONNECTION_RETRY_DELAY_SECONDS * 1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      if (redis == null)
        throw new RuntimeException("failed to connect", last);
    }
    return redis;
  }

  public void close() {
    if (debug) System.out.println("[RedisMap] close");
    if (redis != null)
      redis.close();
  }

  private byte[] getKey(String key) {
    if (prefix != null)
      key = prefix + key;
    if (debug) System.out.println("[RedisMap] key=" + key);
    return key.getBytes(StandardCharsets.UTF_8);
  }

  public void put(String key, V value) {
    if (debug) System.out.println("[RedisMap put] key=" + key + " value=" + value);
    byte[] k = getKey(key);
    byte[] v = serialize.apply(value);
    Jedis conn = getConnection();
    conn.setnx(k, v);
  }

  public void forcePut(String key, V value) {
    if (debug) System.out.println("[RedisMap forcePut] key=" + key + " value=" + value);
    byte[] k = getKey(key);
    byte[] v = serialize.apply(value);
    Jedis conn = getConnection();
    conn.set(k, v);
  }

  public V get(String key) {
    if (debug) System.out.println("[RedisMap get] key=" + key);
    byte[] k = getKey(key);
    Jedis conn = getConnection();
    byte[] v = conn.get(k);
    if (v == null)
      return null;
    V value = deserialize.apply(v);
    return value;
  }
}
