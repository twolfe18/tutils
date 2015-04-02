package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileUtil {

  public static void copy(File sourceFile, File destFile) throws IOException {
    if (!destFile.exists())
      destFile.createNewFile();
    FileChannel source = null;
    FileChannel destination = null;
    try {
      source = new FileInputStream(sourceFile).getChannel();
      destination = new FileOutputStream(destFile).getChannel();
      destination.transferFrom(source, 0, source.size());
    } finally {
      if (source != null)
        source.close();
      if (destination != null)
        destination.close();
    }
  }

  public static Object deserialize(File f) {
    Log.info("reading from " + f.getPath());
    Object out = null;
    try (FileInputStream fis = new FileInputStream(f)) {
      try (ObjectInputStream oos = f.getName().toLowerCase().endsWith(".gz")
          ? new ObjectInputStream(new GZIPInputStream(fis))
          : new ObjectInputStream(fis)) {
        out = oos.readObject();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  public static void serialize(Object obj, File f) {
    Log.info("writing to " + f.getPath());
    try (FileOutputStream fis = new FileOutputStream(f)) {
      try (ObjectOutputStream oos = f.getName().toLowerCase().endsWith(".gz")
          ? new ObjectOutputStream(new GZIPOutputStream(fis))
          : new ObjectOutputStream(fis)) {
        oos.writeObject(obj);
        oos.flush();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
