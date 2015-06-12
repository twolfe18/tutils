package edu.jhu.hlt.tutils;

public interface Average {

  public void add(double value);

  public int getNumObservations();

  public double getAverage();

  public void reset();


  public static class Uniform implements Average {
    private double sum;
    private int adds;
    @Override
    public void add(double value) {
      sum += value;
      adds++;
    }
    @Override
    public int getNumObservations() {
      return adds;
    }
    @Override
    public double getAverage() {
      assert adds > 0;
      return sum / adds;
    }
    @Override
    public void reset() {
      sum = 0d;
      adds = 0;
    }
  }

  public static class Exponential implements Average {
    private double decay;
    private double sum;
    private int adds;
    public Exponential(double decay) {
      if (decay <= 0 || decay >= 1)
        throw new IllegalArgumentException("decay must be in (0,1): " + decay);
      this.decay = decay;
    }
    @Override
    public void add(double value) {
      sum = decay * sum + (1d - decay) * value;
      adds++;
    }
    @Override
    public int getNumObservations() {
      return adds;
    }
    @Override
    public double getAverage() {
      return sum;
    }
    @Override
    public void reset() {
      adds = 0;
      sum = 0d;
    }
  }
}
