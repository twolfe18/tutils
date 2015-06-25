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

  public int mode = 2;

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

  public void run2() {

    if (debug)
      Log.info("starting with model=" + model);

    Beam<ScoredState<S>> frontier = Beam.getMostEfficientImpl(beamSize);
    Adjoints score0 = Adjoints.Constant.ZERO;
    frontier.push(new ScoredState<>(initialState, score0, null), score0.forwards());
    while (frontier.size() > 0) {

      Beam<ScoredState<S>> newFrontier = Beam.getMostEfficientImpl(beamSize);

      Beam.Item<ScoredState<S>> bestItem = frontier.popItem();
      ScoredState<S> best = bestItem.getItem();
      double scoreSoFar = bestItem.getScore();

      Iterator<A> actionItr = transitionFunc.next(best.state);
      while (actionItr.hasNext()) {
        A action = actionItr.next();
        Adjoints partial = model.score(best.state, action);
        double fullScore = scoreSoFar + partial.forwards();
        if (debug) {
          Log.info("partialScore=" + partial.forwards() + " fullScore=" + fullScore + " newFrontier.minScore=" + newFrontier.minScore() + " action=" + action);
        }
        if (fullScore > newFrontier.minScore()) {
          S next = transitionFunc.apply(best.state, action);
          newFrontier.push(new ScoredState<>(next, partial, best), fullScore);
        }
      }

      if (debug)
        Log.info("done iter, frontier.size=" + frontier.size() + " newFrontier.size=" + newFrontier.size());

      if (newFrontier.size() == 0)
        maxBeam.push(bestItem);
      else
        frontier = newFrontier;
    }
    if (debug) {
      Log.info("returning with maxBeam.peek=" + maxBeam.peek());
    }
  }

  @Override
  public void run() {
    Log.info("mode=" + mode);
    switch (mode) {
      case 1:
        run1();
        break;
      case 2:
        run2();
        break;
      default:
        throw new RuntimeException();
    }
  }

  public void run1() {
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
        double b1 = bestItem.getScore();
        double b2 = best.getFullScore();
        assert b1 == b2 : "b1=" + b1 + " b2=" + b2;
        Log.info("this is current best on maxBeam with score " + bestItem.getScore());
      }

      // Try the set of possible actions
      Iterator<A> actionItr = transitionFunc.next(best.state);
      while (actionItr.hasNext()) {
        A action = actionItr.next();
        Adjoints partial = model.score(best.state, action);
        double fullScore = scoreSoFar + partial.forwards();
        if (debug) {
          Log.info("considering action: " + action
              + " partial=" + partial.forwards()
              + " full=" + fullScore);
        }
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
