package edu.jhu.hlt.tutils;

/**
 * Class designed to answer the question:
 * "has it been X seconds since I called you last?"
 *
 * @author travis
 */
public class TimeMarker {
  private long lastMark = -1;
  private long firstMark = -1;
  private int numMarks = 0;

  /**
   * @return true if enoughSeconds have passed since this this method last
   * returned true, or if this method has never been called.
   */
  public boolean enoughTimePassed(double enoughSeconds) {
    long time = System.currentTimeMillis();
    numMarks++;
    if (firstMark < 0)
      firstMark = time;
    if (lastMark < 0) {
      lastMark = time;
      return true;
    }
    double elapsed = (time - lastMark) / 1000d;
    if (elapsed >= enoughSeconds) {
      lastMark = time;
      return true;
    } else {
      return false;
    }
  }

  public double secondsSinceLastMark() {
    assert lastMark > 0;
    return (System.currentTimeMillis() - lastMark) / 1000d;
  }

  public double secondsSinceFirstMark() {
    assert firstMark > 0;
    return (System.currentTimeMillis() - firstMark) / 1000d;
  }

  public int numMarks() {
    return numMarks;
  }

  public double secondsPerMark() {
    assert numMarks > 0;
    return secondsSinceFirstMark() / numMarks;
  }
}