package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;

public class CLI {

  public static int wcDashL(File f) {
    int lines = 0;
    try (BufferedReader r = new BufferedReader(new FileReader(f))) {
      while (r.ready()) {
        lines++;
        r.readLine();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return lines;
  }

  public static List<String> execAndGetResults(String[] command) {
    Log.info("exec: " + Arrays.toString(command));
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      Process p = pb.start();

      InputStreamGobbler stdout = new InputStreamGobbler(p.getInputStream());
      InputStreamGobbler stderr = new InputStreamGobbler(p.getErrorStream());

      stdout.start();
      stderr.start();

      int r = p.waitFor();
      if (r != 0)
        throw new RuntimeException("exit value: " + r);

      return stdout.getLines();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
