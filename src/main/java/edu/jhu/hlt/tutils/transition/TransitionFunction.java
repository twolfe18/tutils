package edu.jhu.hlt.tutils.transition;

import java.util.Iterator;

public interface TransitionFunction<S, A> {

  /**
   * Return the actions possible out of a given state.
   */
  public Iterator<A> next(S state);

  /**
   * Return the result of taking action in state without mutating state.
   */
  public S apply(S state, A action);
}
