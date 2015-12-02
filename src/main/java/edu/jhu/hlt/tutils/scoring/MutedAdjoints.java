package edu.jhu.hlt.tutils.scoring;

/**
 * In implementing {@link OracleAdjoints} I noticed that you could build such
 * a thing out of a smaller piece: an Adjoints with a boolean for whether
 * either the forward or backward pass should be "muted". In the oracle case,
 * the model score component of the sum should be muted on the forward pass
 * and unmuted on the backwards.
 *
 * @author travis
 */
public class MutedAdjoints implements Adjoints {
  private boolean muteForwards;
  private boolean muteBackwards;
  private Adjoints wrapped;

  public MutedAdjoints(boolean muteForwards, boolean muteBackwards, Adjoints wrapped) {
    this.muteBackwards = muteBackwards;
    this.muteForwards = muteForwards;
    this.wrapped = wrapped;
  }

  @Override
  public String toString() {
    return "(Muted fw=" + muteForwards + " bw=" + muteBackwards + " " + wrapped + ")";
  }

  @Override
  public double forwards() {
    if (muteForwards)
      return 0;
    return wrapped.forwards();
  }

  @Override
  public void backwards(double dErr_dForwards) {
    if (!muteBackwards)
      wrapped.backwards(dErr_dForwards);
  }

  /**
   * In order to get the update
   *   w += f(oracle,x) - f(mostViolated,x)
   * with just Adjoints, a game must be played with the oracle adjoints. I'm
   * presuming that we don't want the oracle to be guided by the model score
   * (though this can be generalized). Thus the problem is that the thing that
   * guides search (loss) for the oracle is different than what must be in the
   * update (model score/features). Put another way: the forward pass must
   * differ from the backward pass.
   *
   * Note: If you have some other adjoints like random noise, add this to
   * model score before calling this method.
   */
  public static Adjoints makeOracleAdjoints(Adjoints deltaLoss, Adjoints modelScore) {
    return new Adjoints.Sum(deltaLoss, new MutedAdjoints(true, false, modelScore));
  }
}