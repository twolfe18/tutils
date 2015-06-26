package edu.jhu.hlt.tutils.cache;

import java.io.DataOutputStream;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.jhu.hlt.tutils.FileUtil;
import edu.jhu.hlt.tutils.LazyIteration;
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
    this.hash = String::hashCode;
    this.timer = new Timer("DiskCachedString2TFunc", 100, false);
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

  public List<File> getAllCacheFiles() {
    List<File> fs = new ArrayList<>();
    File[] x = cacheDir.listFiles();
    if (x != null) {
      for (File f : x)
        if (f.getName().endsWith(".jser.gz"))
          fs.add(f);
    }
    return fs;
  }

  public Iterable<T> getAllValues() {
    @SuppressWarnings("unchecked")
    Function<File, List<T>> read = f -> {
      return (List<T>) FileUtil.deserialize(f);
    };
    Iterable<List<T>> itr =
        new LazyIteration.FIterable<>(getAllCacheFiles(), read);  // map
    Iterable<T> itr2 = new LazyIteration.FlatIterable<T>(itr);    // flatten
    return itr2;
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

  public static <R extends Serializable> DiskCachedString2TFunc<R> rehash(
      DiskCachedString2TFunc<R> source, File cacheDir, int numShards) {

    if (cacheDir.equals(source.cacheDir))
      throw new IllegalArgumentException("need new cacheDir");
    if (numShards == source.numShards)
      throw new IllegalArgumentException("need new numShards");

    DiskCachedString2TFunc<R> target =
        new DiskCachedString2TFunc<R>(source.idFunc, cacheDir, numShards);
    for (File f : source.getAllCacheFiles()) {
      @SuppressWarnings("unchecked")
      List<R> items = (List<R>) FileUtil.deserialize(f);
      for (R r : items) {
        String id = target.idFunc.apply(r);
        target.get(id, () -> r);
      }
    }

    return target;
  }
}
