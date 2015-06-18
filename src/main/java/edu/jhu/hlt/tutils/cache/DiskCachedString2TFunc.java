package edu.jhu.hlt.tutils.cache;

import java.io.DataOutputStream;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.hlt.tutils.Timer;

/**
 * Similar to a disk-backed SSTable, but where the keys are strings and the
 * values are of type {@param T}.
 *
 * TODO add support for custom serialization via {@link DataOutputStream}
 * TODO add support for locality-sensitive hash functions (cache last few deserialize calls)
 *
 * @author travis
 */
public class DiskCachedString2TFunc<T extends Serializable> {

  private File cacheDir;
  private int numShards;
  private Function<T, String> idFunc;
  private Function<String, Integer> hash;

  private Timer timer;

  public DiskCachedString2TFunc(Function<T, String> idFunc, File cacheDir, int numShards) {
    if (numShards >= 100000)
      throw new IllegalArgumentException();
    this.cacheDir = cacheDir;
    this.numShards = numShards;
    this.idFunc = idFunc;
    this.hash = s -> s.hashCode();

    this.timer = new Timer("DiskCachedString2TFunc", 10, false);
  }

  public void setHashFunction(Function<String, Integer> hash) {
    this.hash = hash;
  }

  public File getCacheFileFor(String id) {
    int h = hash.apply(id);
    if (h < 0) h = -h;
    h = h % numShards;
    return new File(cacheDir, String.format("%05d.jser.gz", h));
  }

  @SuppressWarnings("unchecked")
  public T get(String id, Supplier<T> thunk) {
    timer.start();

    // Look for the item in (disk) cache
    File f = getCacheFileFor(id);
    List<T> parses = new ArrayList<>();
    if (f.isFile()) {
      parses = (List<T>) FileUtil.deserialize(f);
      for (T t : parses) {
        String id2 = this.idFunc.apply(t);
        if (id.equals(id2)) {
          timer.stop();
          return t;
        }
      }
    }

    // If not found, compute it and save it
    T t = thunk.get();
    parses.add(t);
    FileUtil.serialize(parses, f);

    timer.stop();
    return t;
  }

}
