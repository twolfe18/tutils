package edu.jhu.hlt.tutils;

import java.io.File;
import java.io.Serializable;
import java.util.function.IntConsumer;

/**
 * The interface for a common pattern: You have a method/function which
 * orchestrates some costly computation (e.g. looping over a bunch of files
 * counting things) where a useful result can be written out at intermediate
 * points in the computation. This method/function doesn't know what to do, and
 * this class wraps up the answer.
 * 
 * For example, say you want to build a sketch over a bunch of data, and save
 * the sketch to disk while its being built, every 5 minutes. Have the
 * method/function which loops over the input data take a TimedCallback which
 * says how and where to serialize the sketch.
 * 
 * TODO Here the whatToDo function only takes the number of ticks which have passed,
 * you can build richer tick functions which expect to be given more information.
 * This increases the coupling between the caller and called, and may not be necessary.
 *
 * @author travis
 */
public class TimedCallback {
  private TimeMarker tm;
  private IntConsumer doEverySoOften;
  private double howOftenInSeconds;
  private int ticks;
  
  public TimedCallback(double howOftenInSeconds, IntConsumer whatToDo) {
    this.tm = new TimeMarker();
    this.howOftenInSeconds = howOftenInSeconds;
    this.doEverySoOften = whatToDo;
    this.ticks = 0;
  }

  public void tick() {
    ticks++;
    if (tm.enoughTimePassed(howOftenInSeconds))
      doEverySoOften.accept(ticks);
  }
  
  public static TimedCallback serialize(Serializable s, File dest) {
    return serialize(s, dest, 10 * 60);
  }
  public static TimedCallback serialize(Serializable s, File dest, double seconds) {
    return new TimedCallback(seconds, ticks -> {
      Log.info("saving=" + s + " to=" + dest.getPath());
      FileUtil.serialize(s, dest);
    });
  }
}
