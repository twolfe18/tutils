package edu.jhu.hlt.tutils.transition;

import java.util.Iterator;

import edu.jhu.hlt.tutils.Beam;
import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.scoring.Adjoints;
import edu.jhu.hlt.tutils.scoring.LossAugmentedParams;
import edu.jhu.hlt.tutils.scoring.Params;

/**
 * Beam or Best-first search.
 *
 * @see LossAugmentedParams
 *
 * @author travis
 *
 * @param <S> the state class
 * @param <A> the action class
 */
public class BestFirstSearch<S, A> implements Runnable {

  public static class ScoredState<S> {
    public final S state;
    public final Adjoints score;        // partial score
    public final ScoredState<S> prev;
    public ScoredState(S state, Adjoints score, ScoredState<S> prev) {
      this.state = state;
      this.score = score;
      this.prev = prev;
    }
    /**
     * Computes the total score of this state by calling forwards() on all of
     * the Adjoints in this linked list of ScoredStates.
     * 
     * NOTE: This will not cache/memoize in order to be late-binding so that
     * things like {@link LossAugmentedParams} can work.
     */
    public double getFullScore() {
      double score = 0;
      for (ScoredState<S> cur = this; cur != null; cur = cur.prev)
        score += cur.score.forwards();
      return score;
    }
    public void backwards(double dErr_dForwards) {
      for (ScoredState<S> cur = this; cur != null; cur = cur.prev)
        cur.score.backwards(dErr_dForwards);
    }
  }

  private TransitionFunction<S, A> transitionFunc;
  private S initialState;
  private Params<S, A> model;
  private Beam<ScoredState<S>> maxBeam;
  private int beamSize;
  public boolean debug = false;

  public BestFirstSearch(
      TransitionFunction<S, A> transitionFunction,
      Params<S, A> model, S initialState, int beamSize) {
    if (transitionFunction == null || model == null || initialState == null)
      throw new IllegalArgumentException();
    if (beamSize < 1)
      throw new IllegalArgumentException();
    this.transitionFunc = transitionFunction;
    this.model = model;
    this.initialState = initialState;
    this.beamSize = beamSize;
    this.maxBeam = Beam.getMostEfficientImpl(1);
  }

  @Override
  public void run() {
    Beam<ScoredState<S>> frontier = Beam.getMostEfficientImpl(beamSize);
    Adjoints score0 = Adjoints.Constant.ZERO;
    frontier.push(new ScoredState<>(initialState, score0, null), score0.forwards());
    while (frontier.size() > 0) {

      // Pop an item off the frontier
      Beam.Item<ScoredState<S>> bestItem = frontier.popItem();
      ScoredState<S> best = bestItem.getItem();
      double scoreSoFar = bestItem.getScore();

      if (debug) {
        Log.info("expanding " + best);
      }

      // Add it to the running best
      boolean maxBest = maxBeam.push(bestItem);
      if (debug && maxBest) {
        assert bestItem.getScore() == best.score.forwards();
        Log.info("this is current best on maxBeam with score " + bestItem.getScore());
      }

      // Try the set of possible actions
      Iterator<A> actionItr = transitionFunc.next(best.state);
      while (actionItr.hasNext()) {
        A action = actionItr.next();
        if (debug) {
          Log.info("considering action: " + action);
        }
        Adjoints partial = model.score(best.state, action);
        double fullScore = scoreSoFar + partial.forwards();
        if (fullScore > frontier.minScore()) {
//          Adjoints full = new Adjoints.Sum(best.score, partial);
          S next = transitionFunc.apply(best.state, action);
//          frontier.push(new ScoredState<>(next, full), fullScore);
          frontier.push(new ScoredState<>(next, partial, best), fullScore);
          if (debug) {
            Log.info("added to beam");
          }
        }
      }
    }
  }

  public ScoredState<S> getBestState() {
    return maxBeam.peek();
  }
}
