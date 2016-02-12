package edu.jhu.hlt.tutils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Allows you to use {@link Serializable}'s functionality to implement functions
 * to and from byte[].
 *
 * @author travis
 */
public class SerializationUtils {

  public static Object deserialize(File f) {
    return FileUtil.deserialize(f);
  }
  public static void serialize(Serializable a, File f) {
    FileUtil.serialize(a, f);
  }

  public static <T extends Serializable> byte[] t2bytes(T t) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(t);
      oos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Serializable> T bytes2t(byte[] bytes) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
      ObjectInputStream ois = new ObjectInputStream(bais);
      return (T) ois.readObject();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <T extends Serializable> T cloneViaSerialization(T t) {
    byte[] b = t2bytes(t);
    T copy = bytes2t(b);
    return copy;
  }
}
