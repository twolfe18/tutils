package edu.jhu.hlt.tutils.transition;

import java.util.Iterator;

public interface TransitionFunction<S, A> {

  /**
   * Return the actions possible out of a given state.
   */
  public Iterator<A> next(S state);

  /**
   * Return the result of taking action in state without mutating state.
   * TODO I think this is perhaps better in Action/A.
   * TODO Split TransitionFunction/State into persistent and ephemeral versions?
   * Ephemeral version could have a version that can do unapply(action)?
   * Where to put apply/unapply?
   * You want to guarantee that it works one way for all (state,action) pairs
   * that could come about in the program.
   */
  public S apply(S state, A action);
}
