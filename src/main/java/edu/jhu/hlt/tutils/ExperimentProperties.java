package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import edu.jhu.hlt.tutils.ShardUtils.Shard;

/**
 * Methods with defaults will return the default if the key is not in this map,
 * and also add the (key, defaultValue) pair to this map.
 *
 * Prefer the singleton usage of this class over this over System.getProperties.
 *
 * Booleans have a special feature called "flip". If you provide the value "flip"
 * to a boolean field on the command line and then getBoolean(key, defaultBool)
 * is called, that method will return !defaultBool. If you call getBoolean(key),
 * an exception will be thrown. This is useful for perturbing one boolean at a
 * time to test if it works better than the default.
 *
 * TODO Now that init() is becomming pretty standard in my codebases, I should
 * add coordination with {@link Log}. Namely, whoever calls init() should have
 * their class prefix recorded, e.g. "edu.jhu.hlt.fnparse.foo.Bar". After this,
 * any calls should have their common prefix with the class that called init()
 * stripped off.
 *
 * @author travis
 */
public class ExperimentProperties extends java.util.Properties {
  private static final long serialVersionUID = 1L;

  public static final String FLIP = "flip";
  public static boolean LOG_FLIPS = true;

  private static ExperimentProperties SINGLETON = null;

  public static ExperimentProperties getInstance() {
    if (SINGLETON == null)
      throw new IllegalStateException("you must call init(String[]) first (with the main args)");
    return SINGLETON;
  }

  /**
   * This should be the first call of your program!
   *
   * Takes arguments from main like [..., "--key", "value", ...]
   *
   * Also takes properties from system properties (ones passed in with -Dkey=value).
   */
  public static ExperimentProperties init(String[] mainArgs) {
    if (mainArgs.length % 2 != 0)
      throw new IllegalArgumentException("args must have matching key-value pairs (length % 2 == 0)");
    if (SINGLETON != null)
      throw new IllegalStateException("you called init more than once!");
    SINGLETON = new ExperimentProperties();
    SINGLETON.putAll(System.getProperties());
    int n1 = SINGLETON.size();
    SINGLETON.putAll(mainArgs);
    int n2 = SINGLETON.size();
    if (n2 != n1 + mainArgs.length / 2)
      throw new RuntimeException("duplicate keys, TODO implement code to show dups");
    return SINGLETON;
  }

  /** Use this if you only want to read in properties from java properties and not command line args */
  public static ExperimentProperties init() {
    return init(new String[0]);
  }

  /**
   * Throws an exception if more than one of the provided keys is present
   * @param atleastOne will force an exception if none of the given keys is present
   */
  public void assertMutuallyExclusive(boolean atleastOne, String... keys) {
    List<String> present = new ArrayList<>();
    for (String k : keys) {
      if (this.containsKey(k))
        present.add(k);
    }
    if (atleastOne && present.isEmpty()) {
      throw new IllegalStateException(
          "you need to provide at least one of the following key: "
              + Arrays.toString(keys));
    }
    if (present.size() > 1) {
      throw new IllegalStateException(
          "these flags are mutually exclusive: " + present);
    }
  }

  public void putAll(String[] mainArgs) {
    putAll(mainArgs, false);
  }

  public void putAll(String[] mainArgs, boolean allowOverwrites) {
    if (mainArgs.length % 2 != 0)
      throw new IllegalArgumentException();
    for (int i = 0; i < mainArgs.length; i += 2) {
      String key = mainArgs[i].replaceFirst("^-{0,2}", "");
      String value = mainArgs[i+1];
      Object old = put(key, value);
      if (!allowOverwrites && old != null) {
        throw new RuntimeException(mainArgs[i] + " has two values: "
            + mainArgs[i+1] + " and " + old);
      }
    }
  }

  public int getInt(String key, int defaultValue) {
    String value = getProperty(key);
    if (value == null) {
      put(key, String.valueOf(defaultValue));
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(key + " must be an int: " + value);
    }
  }

  public int getInt(String key) {
    String value = getProperty(key);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(key + " must be an int: " + value);
    }
  }

  public double getDouble(String key, double defaultValue) {
    String value = getProperty(key);
    if (value == null) {
      put(key, String.valueOf(defaultValue));
      return defaultValue;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(key + " must be an double: " + value);
    }
  }

  public double getDouble(String key) {
    String value = getProperty(key);
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException nfe) {
      throw new RuntimeException(key + " must be an double: " + value);
    }
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    String value = getProperty(key);
    if (value == null) {
      put(key, String.valueOf(defaultValue));
      return defaultValue;
    } else if (FLIP.equals(value)) {
      boolean v = !defaultValue;
      if (LOG_FLIPS)
        Log.info(key + " was flipped from " + defaultValue + " to " + v);
      put(key, String.valueOf(v));
      return v;
    }
    return Boolean.parseBoolean(value);
  }

  public boolean getBoolean(String key) {
    String value = getProperty(key);
    if (value == null) {
      throw new RuntimeException("Boolean property not provided: " + key);
    } else if (FLIP.equals(value)) {
      throw new RuntimeException("a property/argument set the default value to \""
          + FLIP + "\" which means that you cannot ask for \"" + key
          + "\" without providing a default to flip -- use 2 arg method");
    }
    return Boolean.parseBoolean(value);
  }

  public File getOrMakeDir(String key, File defaultValue) {
    File f = getFile(key, defaultValue);
    if (!f.isDirectory())
      f.mkdirs();
    if (!f.isDirectory())
      throw new RuntimeException("bad dir: " + f.getPath());
    return f;
  }

  public File getOrMakeDir(String key) {
    File f = getFile(key);
    if (!f.isDirectory())
      f.mkdirs();
    if (!f.isDirectory())
      throw new RuntimeException("bad dir: " + f.getPath());
    return f;
  }

  public File getExistingDir(String key, File defaultValue) {
    File f = getFile(key, defaultValue);
    if (!f.isDirectory())
      throw new RuntimeException("ExistingDir is not dir: " + f.getPath());
    return f;
  }

  public File getExistingDir(String key) {
    File f = getFile(key);
    if (!f.isDirectory())
      throw new RuntimeException("ExistingDir is not dir: " + f.getPath());
    return f;
  }

  public File getExistingFile(String key, File defaultValue) {
    File f = getFile(key, defaultValue);
    if (!f.isFile())
      throw new RuntimeException("ExistingFile is not file: " + f.getPath());
    return f;
  }

  public File getExistingFile(String key) {
    File f = getFile(key);
    if (!f.isFile())
      throw new RuntimeException("ExistingFile is not file: " + f.getPath());
    return f;
  }

  /**
   * Value for this key should be a comma separated list of files, each of which
   * exists.
   */
  public List<File> getExistingFiles(String key) {
    if (!containsKey(key))
      throw new RuntimeException("ExstingFileS not specified: " + key);
    List<File> f = getExistingFiles(key, null);
    if (f == null)
      throw new RuntimeException("ExstingFileS not specified: " + key);
    return f;
  }

  /**
   * Value for this key should be a comma separated list of files, each of which
   * exists. Default values are also checked for existence.
   */
  public List<File> getExistingFiles(String key, List<File> defaultFiles) {
    if (!containsKey(key)) {
      if (defaultFiles == null)
        throw new IllegalArgumentException("[getExistingFiles] no key (" + key + "), null default");
      for (File f : defaultFiles)
        if (!f.isFile())
          throw new IllegalArgumentException("default was not existing file: " + f.getPath());
      return defaultFiles;
    }
    String s = getString(key);
    String[] ss = s.split(",");
    List<File> files = new ArrayList<>();
    for (String fn : ss) {
      File f = new File(fn);
      if (!f.isFile())
        throw new RuntimeException("ExistingFiles key=" + key + " f=" + fn + " is not a file");
      files.add(f);
    }
    return files;
  }

  /**
   * @param key corresponds to a value which is one or more "<key>:<value>"
   * strings separated by commas.
   */
  public Map<String, String> getMapping(String key) {
    String entries = getString(key);
    Map<String, String> m = new HashMap<>();
    for (String t : entries.split(",")) {
      String[] kv = t.split(":");
      if (kv.length != 2)
        throw new IllegalArgumentException("must be <key>:<value> separted by commas: " + entries + ", in particular: " + t);
      String old = m.put(kv[0], kv[1]);
      if (old != null && !old.equals(kv[1]))
        throw new IllegalArgumentException(kv[0] + " is mapped to old=" + old + " and new=" + kv[1]);
    }
    return m;
  }

  public File getFile(String key, File defaultValue) {
    String value = getProperty(key);
    File f;
    if (value == null) {
      if (defaultValue != null)
        put(key, defaultValue.getPath());
      f = defaultValue;
    } else {
      f = new File(value);
    }
    return f;
  }

  public File getFile(String key) {
    String value = getProperty(key);
    if (value == null)
      throw new RuntimeException("File property not provided: " + key);
    File f = new File(value);
    return f;
  }

  public List<File> getFileGlob(String key) {
    return getFileGlob(key, null);
  }

  /**
   * @param key should corresond to a value like "path/to/dir/**\/*.extension",
   * following the pattern <parent><slash><glob>. Glob must contain a slash.
   * @param keep may be null (take everything) or can be used to restrict matches.
   */
  public List<File> getFileGlob(String key, Predicate<File> keep) {
    String parentGlob = getString(key);
    
    if (parentGlob.isEmpty()) {
      Log.info(key + " has empty value, returning empty list of files");
      return Collections.emptyList();
    }

    int i = parentGlob.indexOf("**");
    if (i < 0) {
//      throw new RuntimeException("glob must contain **");
      return Arrays.asList(new File(parentGlob));
    }
    File parent = new File(parentGlob.substring(0, i));
    String glob = "glob:" + parentGlob.substring(i);
    if (!parent.exists())
      throw new IllegalArgumentException("parsed out parent=" + parent.getPath() + " doesn't exist");

    ArrayList<File> output = new ArrayList<>();
    PathMatcher pm = FileSystems.getDefault().getPathMatcher(glob);
    try {
      Files.walkFileTree(parent.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
          if (pm.matches(path)) {
            File f = path.toFile();
            if (keep == null || keep.test(f))
              output.add(path.toFile());
          }
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return output;
  }

  /**
   * Looks for a key called "[specifier]Shard" or "shard" if specifier is null.
   * The value should match "(\d+)[^\d]+(\d+)".
   * If no value is found, then shard=0/numShards=1 is returned, meaning "take
   * all the data".
   */
  public Shard getShard(String specifier) {
    String key = specifier == null ? "shard" : specifier + "Shard";
    String val = getString(key, "0/1");
    String[] sN = val.split("\\D+", 2);
    int s = Integer.parseInt(sN[0]);
    int n = Integer.parseInt(sN[1]);
    return new Shard(s, n);
  }
  public Shard getShard() {
    return getShard(null);
  }

  /**
   * Value is comma-separated string
   */
  public String[] getStrings(String key) {
    String value = getProperty(key);
    if (value == null)
      throw new RuntimeException("comma-separated string value not found for key: " + key);
    if (value.isEmpty())
      return new String[] {};
    return value.split(",");
//    return getStrings(key, ",");
  }
//  public String[] getStrings(String key, String sep) {
//    String value = getProperty(key);
//    if (value == null)
//      throw new RuntimeException("comma-separated string value not found for key: " + key);
//    return value.split(sep);
//  }
  public String[] getStrings(String key, String[] defaultValue) {
    String value = getProperty(key);
    if (value == null) {
      put(key, StringUtils.join(",", defaultValue));
      return defaultValue;
    }
    if (value.isEmpty())
      return new String[] {};
    return value.split(",");
  }
//  public String[] getStrings(String key, String sep, String[] defaultValue) {
//    String value = getProperty(key);
//    if (value == null) {
//      put(key, StringUtils.join(sep, defaultValue));
//      return defaultValue;
//    }
//    return value.split(sep);
//  }
  public String[] getStrings(String key, String defaultValue) {
    String value = getProperty(key);
    if (value == null) {
      put(key, defaultValue);
      return new String[] {defaultValue};
    }
    if (value.isEmpty())
      return new String[] {};
    return value.split(",");
  }

  public String getString(String key, String defaultValue) {
    String value = getProperty(key);
    if (value == null) {
      if (defaultValue != null)
        put(key, defaultValue);
      return defaultValue;
    }
    return value;
  }

  public String getString(String key) {
    String value = getProperty(key);
    if (value == null)
      throw new RuntimeException("String property not provided: " + key);
    return value;
  }
  
  public Random getRandom() {
    return getRandom(null);
  }
  
  /**
   * @param module is a string describing which seed to look for.
   * E.g. if you have two modules which you would like to be able
   * to seed independently, call this method with two different strings.
   *
   * NOTE: This does not deduplicate instances of {@link Random}, so the
   * caller must take care not to call this too often (most likely once
   * per module string value).
   */
  public Random getRandom(String module) {
    String key = "seed";
    if (module != null)
      key = key + "." + module;
    int seed = getInt(key, 9001);
    return new Random(seed);
  }
}
