package edu.jhu.hlt.tutils.net;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

public class NetworkSerializableParams<T extends Serializable>
    implements NetworkParameterAveraging.Params {

  private T params;

  public NetworkSerializableParams(T params) {
    this.params = params;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void set(InputStream data) {
    try {
      ObjectInputStream ois = new ObjectInputStream(data);
      params = (T) ois.readObject();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void get(OutputStream data) {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(data);
      oos.writeObject(params);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
