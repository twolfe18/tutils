package edu.jhu.hlt.tutils.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class NetworkDenseParams implements NetworkParameterAveraging.Params {
  protected double[] weights;

  public NetworkDenseParams(int dimension) {
    this.weights = new double[dimension];
  }

  public double[] getWeights() {
    return weights;
  }

  public double getWeight(int i) {
    return weights[i];
  }

  public int size() {
    return weights.length;
  }

  @Override
  public void set(InputStream data) {
    try {
      // Don't close: that will close the socket connection
      DataInputStream dis = new DataInputStream(data);
      int n = dis.readInt();
      assert n == weights.length;
      for (int i = 0; i < n; i++)
        weights[i] = dis.readDouble();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void get(OutputStream data) {
    try {
      // Don't close: that will close the socket connection
      DataOutputStream dos = new DataOutputStream(data);
      dos.writeInt(weights.length);
      for (int i = 0; i < weights.length; i++)
        dos.writeDouble(weights[i]);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static class Avg extends NetworkDenseParams implements NetworkParameterAveraging.AvgParams {
    private int adds;

    public Avg(int dimension) {
      super(dimension);
      adds = 0;
    }

    public int getNumAdds() {
      return adds;
    }

    @Override
    public void add(InputStream data) {
      try {
        // Don't close: that will close the socket connection
        DataInputStream dis = new DataInputStream(data);
        int n = dis.readInt();
        assert n == weights.length;
        for (int i = 0; i < n; i++)
          weights[i] += dis.readDouble();
        adds++;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void getAverage(OutputStream data) {
      if (adds == 0)
        throw new IllegalStateException();
      try {
        // Don't close: that will close the socket connection
        DataOutputStream dos = new DataOutputStream(data);
        dos.writeInt(weights.length);
        for (int i = 0; i < weights.length; i++)
          dos.writeDouble(weights[i] / adds);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}