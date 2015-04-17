package edu.jhu.hlt.tutils.transition;

import java.util.Iterator;

import edu.jhu.hlt.tutils.Beam;
import edu.jhu.hlt.tutils.scoring.Adjoints;
import edu.jhu.hlt.tutils.scoring.Params;

public class BestFirstSearch<S, A> implements Runnable {

  public static class ScoredState<S> {
    public final S state;
    public final Adjoints score;
    public ScoredState(S state, Adjoints score) {
      this.state = state;
      this.score = score;
    }
  }

  private TransitionFunction<S, A> transitionFunc;
  private S initialState;
  private Params<S, A> model;
  private Beam<ScoredState<S>> maxBeam;
  private int beamSize;

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
    Adjoints score0 = new Adjoints.Constant(-1000);
    frontier.push(new ScoredState<>(initialState, score0), score0.forwards());
    while (frontier.size() > 0) {

      // Pop an item off the frontier
      Beam.Item<ScoredState<S>> bestItem = frontier.popItem();
      ScoredState<S> best = bestItem.getItem();
      double scoreSoFar = bestItem.getScore();

      // Add it to the running best
      maxBeam.push(bestItem);

      // Try the set of possible actions
      Iterator<A> actionItr = transitionFunc.next(best.state);
      while (actionItr.hasNext()) {
        A action = actionItr.next();
        Adjoints partial = model.score(best.state, action);
        double fullScore = scoreSoFar + partial.forwards();
        if (fullScore > frontier.minScore()) {
          Adjoints full = new Adjoints.Sum(best.score, partial);
          S next = transitionFunc.apply(best.state, action);
          frontier.push(new ScoredState<>(next, full), fullScore);
        }
      }
    }
  }

  public ScoredState<S> getBestState() {
    return maxBeam.peek();
  }
}
