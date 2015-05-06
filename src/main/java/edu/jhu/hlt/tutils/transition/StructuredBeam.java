package edu.jhu.hlt.tutils.transition;

import java.util.TreeSet;
import java.util.function.ToIntFunction;

import edu.jhu.hlt.tutils.Beam;

/* GOOD RESOURCE: http://www.statmt.org/book/slides/06-decoding.pdf */


// Before I continue with this, I should figure out how I'm going to use
// this for fnparse and coref.
// fnparse: structure = #actions taken
// coref: structure = #actions taken

// maybe could even split by action type:
// fnparse: (#commit, #prune)
// coref: (#merge, #block)

// maybe even slit by frame
// fnparse: (#commit[t=0], #prune[t=0], ...)
// not clear how to do this for coref

// What about random partitions?
// They seem to help with diversity: if you use a LSH, you should increase diversity
// They question is how to order the buckets...
// Maybe not... just have no order?
// pop = max over random partitions' best item?
// It's not clear how this solves the diversity problem other than adding
// more buckets/partitions.

// What is the property that the bucket ordering needs to satisfy?
// all items x which would be hashed into partition p should have all of
// their antecedents in some partition p' such that p' < p.

// Without buckets, out score function has to have lookahead build into the
// score (must discriminate between all possible states). With buckets, our
// score function can be weaker, only needing to distinguish between items
// that fall into the same bucket.

// Additive nature of score function => independence assumption or model
// restriction which we need not make.

// Starting to seem like what I want is for the buckets themselves to be
// discriminatory for good vs bad parses.
// Buckets could have their own partial score?
// score(action) = modelScore(bucket(action)) + modelScore(action) ?
// => Since bucket is a deterministic function, you could just role that
//    into modelScore(action) using a feature.

// If your model could see whatever features would arise from the bucket
// address, it seems like the "information" that we are giving our model
// comes from the hand-crafted nature of the partial order over buckets.

// Lets construct an example where regular beam search fails and come up
// with a structured beam to solve it.
// Lets say we have a feature on triples of actions (a1,a2,a3). If all three
// of these actions happen, then it is very unlikely/poorly-scoring.
// They don't even need to be actions, they could be three properties of
// the structure which can only be committed to through three actions
// (perhaps a length 3 path feature).
// THE PROBLEM that arises is that your model will be very greedy assuming
// that a1 looks good. If there are c1 actions following a1(s0) which have
// a score greater than 0, and c2 actions following any one of those c1
// states. You would add c1*c2 items to the beam, which would means that
// very likely you bumped all of the s0 actions needed to avoid the garden
// path off the beam.
// IF WE USED a partition of number of actions taken, then we still may not
// have solved the problem.

// AH, what we need is to be able to POP from any of the beams in the structure.
// What happens is that the global feature will hammer all ways leaving a1
// to counteract the local improvements made by greedily assessing the actions.
// If we pop all of the children of a1, the feature fires, and kills the score,
// then they will never be entered onto any beam with a score better than
// the sibling of a1 that avoids the problem.

// The problem we would see with a single-beam approach is that these greedy
// assessments would evict the neighbors of a1 before the global feature could
// fire. If the siblings of a1 can't be evicted by these later states, then
// we can conceivably recover once the global feature fires, assuming its
// weight is large enough.

// So in this example, we didn't need to choose an order: we simply chose
// the best item in *any* beam. The job of global feature is to push down
// the bad global structures and force back-tracking (in the case of one
// beam, you often evict the state needed to backtrack. With many beams you
// keep around a variety of worse solutions which cannot be evicted by later
// greediness). The greedy local action scoring tells what to expand
// assuming nothing tricky happens.

/*
 * Oh SNAP, if I implement the "indexed beam" idea I was thinking about, then
 * I could use that for StructuredBeam = IndexedBeam<Beam<T>>.
 * 
 * The idea behind the indexed beam is it will maintain a Beam<T> and a
 * Map<Hash<T>, T> and that push(T) will do de-duplication by seeing if there
 * is something equivalent on the beam already. Equivalence could be done
 * approximately by a hash function or exactly by additionally performing an
 * equals check.
 *
 * In the StructuredBeam case, that hash/equivalence function is just the
 * partition that the item belongs to. => AH, DIFFERENT SEMANTICS. For the
 * StructuredBeam, we should just be able to retrieve the Beam<T> corresponding
 * to that partition (and push to it); we should not need to replace it with
 * a whole other Beam<T>. Maybe:
 * StructuredBeam = Map<Hash<T>, Beam<T>>
 * this would work except for the need for pop, which requires
 * StructuredBeam = Beam<Beam<T>>
 */

/**
 * After my thoughts described in 2015-05-06.txt, it appears that this is just
 * a single-threaded version of what I want. I should go out and get an actor
 * system library where each actor has a PBeam and execution is concurrent.
 * Even if the actor system executes with 1 thread, there shouldn't be too much
 * work wasted.
 *
 * @author travis
 *
 * @param <T>
 */
public class StructuredBeam<T> {

  /**
   * Keeps track of pops off of a given beam.
   * The natural ordering for this class is by the score of the top thing on
   * the beam. If there is nothing on the beam, then it is less any other PBeam
   * with any element on the beam and equal to any other with nothing on the
   * beam. This is used to support StructuredBeam's operations.
   */
  static class PBeam<T> implements Comparable<StructuredBeam.PBeam<T>> {
    private Beam<T> beam;
    public final int partition;
    public final int maxPops;
    private int pops;

    public PBeam(Beam<T> toWrap, int partition, int maxPops) {
      this.partition = partition;
      this.pops = 0;
      this.maxPops = maxPops;
      this.beam = toWrap;
    }

    public boolean push(Beam.Item<T> item) {
      return beam.push(item);
    }

    public Beam.Item<T> pop() {
      assert pops < maxPops;
      pops++;
      return beam.popItem();
    }

    public double minScore() {
      return beam.minScore();
    }

    /** How many items are on the beam */
    public int size() {
      if (pops >= maxPops)
        return 0;
      return beam.size();
    }

    @Override
    public int compareTo(StructuredBeam.PBeam<T> o) {
      throw new RuntimeException("implement me");
    }
  }

  private ToIntFunction<T> item2partition;  // aka structure
  private StructuredBeam.PBeam<T>[] partition2beam;        // aka Map<Int, Beam<T>>
  private TreeSet<StructuredBeam.PBeam<T>> score2beam;     // supports pop = max_b b.pop

  /** Returns true if the item was added */
  public boolean push(Beam.Item<T> item) {
    // Get the beam to be updated by partition.
    int p = item2partition.applyAsInt(item.getItem());
    StructuredBeam.PBeam<T> toUpdate = partition2beam[p];

    // Can potentially bail out early: if this item won't make it onto toUpdate,
    // then we don't need to remove/add it from/to score2beam.
    if (item.getScore() < toUpdate.minScore())
      return false;

    // Find this beam in the TreeSet and remove it. Otherwise the TreeSet will
    // not get to see the score change and will be out of order.
    score2beam.remove(toUpdate);

    // Why we're here: update the beam
    boolean added = toUpdate.push(item);

    // Now the TreeSet may store this item differently given the new item added
    score2beam.add(toUpdate);

    return added;
  }

  public Beam.Item<T> pop() {
    StructuredBeam.PBeam<T> best = score2beam.last();
    if (best.size() == 1) {
      partition2beam[best.partition] = null;
      score2beam.remove(best);
    }
    return best.pop();
  }
}