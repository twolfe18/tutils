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

public class FileUtil {
  public static boolean VERBOSE = false;

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
  public static Iterable<File> find(File parent, String glob) {
    List<File> output = new ArrayList<>();
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

  public static BufferedWriter getWriter(File f, boolean append) throws IOException {
    OutputStream is = new FileOutputStream(f, append);
    if (f.getName().toLowerCase().endsWith(".gz"))
      is = new GZIPOutputStream(is);
    return new BufferedWriter(new OutputStreamWriter(is));
  }

  public static BufferedWriter getWriter(File f) throws IOException {
    return getWriter(f, false);
  }

  public static BufferedReader getReader(File f) throws IOException {
    InputStream is = new FileInputStream(f);
    if (f.getName().toLowerCase().endsWith(".gz"))
      is = new GZIPInputStream(is);
    return new BufferedReader(new InputStreamReader(is));
  }

  public static Object deserialize(File f) {
    if (VERBOSE)
      Log.info("reading from " + f.getPath());
    Object out = null;
    try (FileInputStream fis = new FileInputStream(f);
        ObjectInputStream oos = f.getName().toLowerCase().endsWith(".gz")
            ? new ObjectInputStream(new GZIPInputStream(fis))
            : new ObjectInputStream(fis)) {
      out = oos.readObject();
    } catch (Exception e) {
      throw new RuntimeException(e);
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
}
