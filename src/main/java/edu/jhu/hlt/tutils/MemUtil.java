package edu.jhu.hlt.tutils;

public class MemUtil {

  public static String memoryUsage() {
    Runtime r = Runtime.getRuntime();
    return String.format("MemoryUsage used=%.1fG free=%.1fG limit=%.1fG",
        r.totalMemory() / (1024 * 1024 * 1024d),
        r.freeMemory() / (1024 * 1024 * 1024d),
        r.maxMemory() / (1024 * 1024 * 1024d));
  }

}
