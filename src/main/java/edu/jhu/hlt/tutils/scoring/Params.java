package edu.jhu.hlt.tutils.scoring;

import java.util.Random;

/**
 * Represents an object which assigns a score to (context, action) or (x,y)
 * pairs. Specifically an object which is parametric but hides its parameters
 * (the Adjoints returned are used to update these parameters).
 *
 * @author travis
 *
 * @param <S> the state/context of a decision (also called "x" in machine learning)
 * @param <A> the action to be taken (also called "y" in machine learning)
 */
public interface Params<S, A> {

  public Adjoints score(S state, A action);

  /** Returns a random score between 0 and 1 */
  public static class Rand<S, A> implements Params<S, A> {
    private Random rand;
    public Rand(Random r) {
      this.rand = r;
    }
    public Adjoints score(S state, A action) {
      return new Adjoints.Constant(rand.nextDouble());
    }
  }

  /** Always returns the same score (ignores arguments) */
  public static class Constant<S, A> implements Params<S, A> {
    private Adjoints value;
    public Constant(double value) {
      this.value = new Adjoints.Constant(value);
    }
    public Adjoints score(S state, A action) {
      return value;
    }
  }

  public static class Sum<S, A> implements Params<S, A> {
    private Params<S, A>[] params;
    @SafeVarargs
    public Sum(Params<S, A>... params) {
      this.params = params;
    }
    @Override
    public Adjoints score(S state, A action) {
      int n = params.length;
      Adjoints[] scores = new Adjoints[n];
      for (int i = 0; i < n; i++)
        scores[i] = params[i].score(state, action);
      return new Adjoints.Sum(scores);
    }
  }
}