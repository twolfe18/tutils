package edu.jhu.hlt.tutils;

import java.io.Serializable;

/**
 * A pointer to a resource saying whether one can/should read/write.
 */
public class RW<T> implements Serializable {
  private static final long serialVersionUID = -1072797683228763938L;

  public final T resource;
  public final boolean read;
  public final boolean write;

  public RW(T resource, boolean read, boolean write) {
    this.resource = resource;
    this.read = read;
    this.write = write;
  }
}
