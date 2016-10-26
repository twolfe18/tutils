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
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

public class FileUtil {
  public static boolean VERBOSE = false;

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
    if (VERBOSE)
      Log.info("reading from " + f.getPath());
    long s = System.currentTimeMillis();
    Object out = null;
    try (FileInputStream fis = new FileInputStream(f);
        ObjectInputStream oos = f.getName().toLowerCase().endsWith(".gz")
            ? new ObjectInputStream(new GZIPInputStream(fis))
            : new ObjectInputStream(fis)) {
      out = oos.readObject();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    double sec = (System.currentTimeMillis() - s) / 1000d;
    Log.info(String.format("too %.2f sec to read from %s", sec, f.getPath()));
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

  public static void main(String[] args) throws IOException {
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
}
