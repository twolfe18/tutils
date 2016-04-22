package edu.jhu.hlt.tutils;

public class Timer {
  private String id;
  private int count;
  private long totalTime;
  private long lastStart = -1;
  public int printIterval;

  public boolean ignoreFirstTime;
  private long firstTime;

  // timeHistCounts[i] = number of timer events which took between t(i) and t(i+1)
  // milliseconds, where t(i) = timeHistCoef * 2^i
  private int[] timeHistCounts = new int[20];
  private long timeHistCoef = 4;   // first bucket: <8 ms, last bucket: >4.2 minutes
  public boolean showTimeHistInToString = false;
  private long timeUpperBound(int i) {
    if (i < 0 || i >= timeHistCounts.length)
      throw new IllegalArgumentException();
    return timeHistCoef * (1L << i);
  }
  private void updateHist(long ms) {
    assert ms < Integer.MAX_VALUE;
    for (int i = 0; i < timeHistCounts.length; i++) {
      long ub = timeUpperBound(i);
      if (ms < ub || i == timeHistCounts.length-1) {
        timeHistCounts[i]++;
        return;
      }
    }
  }
  private static String timeStr(long ms) {
    double sec = ms / 1000d;
    double min = sec / 60d;
    if (min > 1)
      return String.format("%.1f min", min);
    if (sec > 1)
      return String.format("%.1f sec", sec);
    return ms + " ms";
  }
  public String getTimeHist() {
    int firstNz = -1;
    int lastNz = -1;
    for (int i = 0; i < timeHistCounts.length; i++) {
      if (timeHistCounts[i] > 0) {
        if (firstNz < 0)
          firstNz = i;
        lastNz = i;
      }
    }
    if (lastNz < 0)
      return "[no times available]";
    StringBuilder sb = new StringBuilder();
    for (int i = firstNz; i < lastNz; i++) {
      if (i == firstNz)
        sb.append("[<numEvents> below <time> ");
      else
        sb.append(' ');
      sb.append(timeHistCounts[i] + " " + timeStr(timeUpperBound(i)));
    }
    sb.append(']');
    return sb.toString();
  }

  public Timer() {
    this(null, -1, false);
  }
  public Timer(String id) {
    this(id, -1, false);
  }
  public Timer(String id, int printInterval, boolean ignoreFirstTime) {
    this.id = id;
    this.printIterval = printInterval;
    this.ignoreFirstTime = ignoreFirstTime;
  }

  public static Timer start(String id) {
    Timer t = new Timer(id, 1, false);
    t.start();
    return t;
  }

  public Timer ignoreFirstTime() {
    this.ignoreFirstTime = true;
    return this;
  }

  public Timer ignoreFirstTime(boolean ignore) {
    this.ignoreFirstTime = ignore;
    return this;
  }

  public Timer setPrintInterval(int interval) {
    if(interval <= 0) throw new IllegalArgumentException();
    this.printIterval = interval;
    return this;
  }

  public Timer disablePrinting() {
    this.printIterval = -1;
    return this;
  }

  public void start() {
    lastStart = System.currentTimeMillis();
  }

  /** returns the time taken between the last start/stop pair in milliseconds */
  public long stop() {
    long t = System.currentTimeMillis() - lastStart;
    if(count == 0)
      firstTime = t;
    totalTime += t;
    count++;
    updateHist(t);
    if(printIterval > 0 && count % printIterval == 0)
      System.out.println(this);
    return t;
  }

  /**
   * like stop, returns the time taken between the last start/stop pair in
   * milliseconds, but does not stop the timer.
   */
  public long sinceStart() {
    return System.currentTimeMillis() - lastStart;
  }

  public String toString() {
    double spc = secPerCall();
    String spcUnit = "sec/call";
    if (spc < 0.001) {
      spc *= 1000;
      spcUnit = "ms/call";
    }
    String timeHist = "";
    if (showTimeHistInToString) {
      timeHist = " hist=" + getTimeHist();
    }
    return String.format("<Timer %s %.2f sec and %d calls total, %.3f %s%s>",
        id, totalTimeInSeconds(), count, spc, spcUnit, timeHist);
  }

  public double countsPerMSec() {
    if(count > 1)
      return (count - 1d) / (totalTime - firstTime);
    return ((double) count) / totalTime;
  }

  public double secPerCall() {
    if(count > 1)
      return ((totalTime - firstTime)/1000d) / (count-1);
    return (totalTime/1000d) / count;
  }

  public double minutesUntil(int iterations) {
    int remaining = iterations - count;
    double rate = secPerCall() / 60d;
    return rate * remaining;
  }

  /** How many times start/stop has been called */
  public int getCount() {
    return count;
  }

  public long totalTimeInMilliseconds() { return totalTime; }
  public double totalTimeInSeconds() { return totalTime / 1000d; }

  public static final class NoOp extends Timer {
    public NoOp(String id) { super(id); }
    public NoOp(String id, int printInterval)  { super(id, printInterval, false); }
    public void start() {}
    public long stop() { return -1; }
  }

  public static final Timer noOp = new NoOp("noOp");
}