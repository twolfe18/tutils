package edu.jhu.hlt.tutils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
}
