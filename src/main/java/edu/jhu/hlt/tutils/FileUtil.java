package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
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
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import edu.jhu.prim.list.DoubleArrayList;

public class FileUtil {
  public static boolean VERBOSE = false;
  
  public static void writeLines(Iterable<String> lines, File to) throws IOException {
    try (BufferedWriter w = getWriter(to)) {
      for (String l : lines) {
        w.write(l);
        w.newLine();
      }
    }
  }

  public static List<String> getLines(File f) {
    try (BufferedReader r = getReader(f)) {
      List<String> lines = new ArrayList<>();
      for (String l = r.readLine(); l != null; l = r.readLine())
        lines.add(l);
      return lines;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static DoubleArrayList getLinesAsDoubles(File f) throws IOException {
    try (BufferedReader r = getReader(f)) {
      DoubleArrayList lines = new DoubleArrayList();
      for (String l = r.readLine(); l != null; l = r.readLine())
        lines.add(Double.parseDouble(l));
      return lines;
    }
  }

  public static byte[] readBytes(File f) throws IOException {
    try (InputStream is = getInputStream(f)) {
      return readBytes(is);
    }
  }
  
  public static byte[] readBytes(InputStream is) throws IOException {
    int read = 0;
    int bs = 4096;
    byte[] buf = new byte[4 * bs];
    while (true) {
      if (read + bs >= buf.length)
        buf = Arrays.copyOf(buf, (int) (1.6 * buf.length + 1));
      int r = is.read(buf, read, bs);
      if (r <= 0)
        break;
      read += r;
    }
    return Arrays.copyOfRange(buf, 0, read);
  }
  
  public static String getContents(File f, boolean replaceNewlinesWithSpaces) {
    StringBuilder sb = new StringBuilder();
    for (String line : getLines(f)) {
      if (replaceNewlinesWithSpaces && sb.length() > 0)
        sb.append(' ');
      sb.append(line);
      if (!replaceNewlinesWithSpaces)
        sb.append('\n');
    }
    return sb.toString();
  }
  
  public static void rm_rf(File f) {
    Path directory = f.toPath();
    try {
      Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Calls /usr/bin/find
   * @param dir is the directory to search.
   * @param findArguments should be things like ["-name", "*.txt", "-size", "+1M"] etc
   */
  public static List<File> execFind(File dir, String... findArguments) throws InterruptedException, IOException {
    String[] cmd = new String[findArguments.length + 2];
    cmd[0] = "/usr/bin/find";
    cmd[1] = dir.getPath();
    System.arraycopy(findArguments, 0, cmd, 2, findArguments.length);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    Process p = pb.start();
    InputStreamGobbler stdout = new InputStreamGobbler(p.getInputStream());
    InputStreamGobbler stderr = new InputStreamGobbler(p.getErrorStream());
    stdout.start();
    stderr.start();
    int r = p.waitFor();
    if (r != 0)
      throw new RuntimeException("ret=" + r);
    stdout.join();
    List<String> files = stdout.getLines();
    List<File> ret = new ArrayList<>();
    for (String f : files)
      ret.add(new File(f));
    return ret;
  }

  public static List<File> execFindEx1(File dir, String... findArguments) {
    try {
      return execFind(dir, findArguments);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static List<File> execFindEx2(File dir, String... findArguments) {
    try {
      return execFind(dir, findArguments);
    } catch (Exception e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  /**
   * @param parent is the directory to search in.
   * @param glob should be a string like "glob:**\/foo*" (note that there
   * shouldn't be a backslash, that is just added to make this a legal comment
   * (it looks like a close block comment otherwise).
   */
  public static ArrayList<File> find(File parent, String glob) {
    ArrayList<File> output = new ArrayList<>();
    PathMatcher pm = FileSystems.getDefault().getPathMatcher(glob);
    try {
      Files.walkFileTree(parent.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
          if (pm.matches(path))
            output.add(path.toFile());
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

  public static ArrayList<File> findDirs(File parent, String glob) {
    ArrayList<File> output = new ArrayList<>();
    PathMatcher pm = FileSystems.getDefault().getPathMatcher(glob);
    try {
      Files.walkFileTree(parent.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          if (pm.matches(dir))
            output.add(dir.toFile());
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return output;
  }

  public static void copy(File sourceFile, File destFile) throws IOException {
    if (!destFile.exists())
      destFile.createNewFile();
    try (FileInputStream source = new FileInputStream(sourceFile);
        FileChannel sChan = source.getChannel();
        FileOutputStream destination = new FileOutputStream(destFile);
        FileChannel dChan = destination.getChannel()) {
      dChan.transferFrom(sChan, 0, sChan.size());
    }
  }

  public static OutputStream getOutputStream(File f) throws IOException {
    boolean append = false;
    return getOutputStream(f, append);
  }
  public static OutputStream getOutputStream(File f, boolean append) throws IOException {
    OutputStream is = new FileOutputStream(f, append);
    if (f.getName().toLowerCase().endsWith(".gz"))
      is = new GZIPOutputStream(is);
    else if (f.getName().toLowerCase().endsWith(".bz2"))
      is = new BZip2CompressorOutputStream(is, 9);
    return is;
  }

  public static BufferedWriter getWriter(File f, boolean append) throws IOException {
    return new BufferedWriter(new OutputStreamWriter(getOutputStream(f, append)));
  }

  public static BufferedWriter getWriter(File f) throws IOException {
    return getWriter(f, false);
  }

  public static InputStream getInputStream(File f) throws IOException {
    InputStream is = new FileInputStream(f);
    if (f.getName().toLowerCase().endsWith(".gz"))
      is = new GZIPInputStream(is);
    else if (f.getName().toLowerCase().endsWith(".bz2"))
      is = new BZip2CompressorInputStream(is);
    return is;
  }

  public static InputStream getInputStreamOrBlowup(File f) {
    try {
      return getInputStream(f);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static BufferedReader getReader(File f) throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(f)));
  }

  public static BufferedReader getReaderOrBlowup(File f) {
    try {
      return getReader(f);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Object deserialize(File f) {
    return deserialize(f, false);
  }
  public static Object deserialize(File f, boolean quiet) {
    if (VERBOSE)
      Log.info("reading from " + f.getPath());
    long s = quiet ? 0 : System.currentTimeMillis();
    Object out = null;
    try (FileInputStream fis = new FileInputStream(f);
        ObjectInputStream oos = f.getName().toLowerCase().endsWith(".gz")
            ? new ObjectInputStream(new GZIPInputStream(fis))
            : new ObjectInputStream(fis)) {
      out = oos.readObject();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (!quiet) {
      double sec = (System.currentTimeMillis() - s) / 1000d;
      Log.info(String.format("took %.2f sec to read from %s", sec, f.getPath()));
    }
    return out;
  }

  public static void serialize(Object obj, File f) {
    if (VERBOSE)
      Log.info("writing to " + f.getPath());
    try (FileOutputStream fis = new FileOutputStream(f);
        ObjectOutputStream oos = f.getName().toLowerCase().endsWith(".gz")
          ? new ObjectOutputStream(new GZIPOutputStream(fis))
          : new ObjectOutputStream(fis)) {
      oos.writeObject(obj);
      oos.flush();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  static void findTest() throws Exception {
    List<File> fs = execFind(new File("/home/travis/code/fnparse"), "-type", "f", "-size", "+1M", "-name", "*.gz");
    System.out.println("found " + fs.size() + " files");
  }

  static void compressionTest(String[] args) throws IOException {
    if (args.length != 3) {
      System.err.println("please provide:");
      System.err.println("1) an input file");
      System.err.println("2) an output compressed file");
      System.err.println("3) an output uncompressed/round-trip file");
      return;
    }
    File i1 = new File(args[0]);
    File o1 = new File(args[1]);
    File o2 = new File(args[2]);
    Log.info("compressing: " + i1.getPath() + " => " + o1.getPath());
    try (BufferedReader r = FileUtil.getReader(i1);
        BufferedWriter w = FileUtil.getWriter(o1)) {
      for (String line = r.readLine(); line != null; line = r.readLine())
        w.write(line + "\n");
    }
    // Slower by about 28:16
//    try (InputStream r = FileUtil.getInputStream(i1);
//        OutputStream w = FileUtil.getOutputStream(o1)) {
//      int i;
//      while ((i = r.read()) >= 0)
//        w.write(i);
//    }

    Log.info("decompressing: " + o1.getPath() + " => " + o2.getPath());
    try (BufferedReader r = FileUtil.getReader(o1);
        BufferedWriter w = FileUtil.getWriter(o2)) {
      for (String line = r.readLine(); line != null; line = r.readLine())
        w.write(line + "\n");
    }
    Log.info("done");
  }

  public static void main(String[] args) throws Exception {
//    compressionTest(args);
    findTest();
  }
}
