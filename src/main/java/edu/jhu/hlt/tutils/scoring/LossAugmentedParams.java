package edu.jhu.hlt.tutils.scoring;


/**
 * Solves an issue that crops up with performing loss augmented inference and
 * computing the margin. The search routine must see (modelScore + loss) when
 * choosing/building a path. To compute the margin, we only want to use modelScore
 * (in principle we could use the sum given for loss augmented search, but
 * we want the ability to make that loss a proxy/approximate loss, which the
 * margin should not be based on).
 *
 * How it works: this class builds Adjoints which have state saying whether they
 * should return modelScore+loss or just modelScore.
 *
 * @author travis
 */
public class LossAugmentedParams<S, A> implements Params<S, A> {

  private boolean includeLoss;
  private final Params<S, A> modelScore;
  private final Params<S, A> loss;

  public LossAugmentedParams(Params<S, A> modelScore, Params<S, A> loss) {
    this.includeLoss = true;
    this.modelScore = modelScore;
    this.loss = loss;
  }

  public boolean isIncludingLoss() {
    return includeLoss;
  }

  public void includeLoss(boolean includeLoss) {
    this.includeLoss = includeLoss;
  }

  @Override
  public Adjoints score(S state, A action) {
    return new DLAdjoints(
        modelScore.score(state, action),
        loss.score(state, action));
  }

  public class DLAdjoints implements Adjoints {
    private final Adjoints modelScore;
    private final Adjoints loss;

    public DLAdjoints(Adjoints modelScore, Adjoints loss) {
      this.modelScore = modelScore;
      this.loss = loss;
    }

    @Override
    public double forwards() {
      if (includeLoss)
        return modelScore.forwards() + loss.forwards();
      else
        return modelScore.forwards();
    }

    @Override
    public void backwards(double dErr_dForwards) {
      modelScore.backwards(dErr_dForwards);
      if (includeLoss)
        loss.backwards(dErr_dForwards);
    }

    @Override
    public String toString() {
      if (includeLoss)
        return ("(LossAugmented " + modelScore + " + " + loss + ")");
      return ("(LossAugmented " + modelScore + " without deltaLoss)");
    }
  }
}
