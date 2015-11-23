package edu.jhu.hlt.tutils;

public class Log {
  private static final long init = System.currentTimeMillis();
  public static boolean SHOW_CALLER = true;

  // Don't use this, as Java will evaluate msg before calling.
  // Just force the caller to use an if at the call site.
//  public static void infoIf(boolean condition, Object msg) {
//    if (condition)
//      info(msg);
//  }

  public static void debug(Object msg) {
    info(msg);
  }

  public static void info(Object msg) {
    long t = System.currentTimeMillis() - init;
    if (SHOW_CALLER) {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      StackTraceElement caller = stack[2];
      String callerStr = caller.getClassName() + "::" + caller.getMethodName(); // + ":" + caller.getLineNumber();
      System.out.println(t + "  " + callerStr + "  " + msg);
    } else {
      System.out.println(t + "  " + msg);
    }
  }

  public static void warn(Object msg) {
    long t = System.currentTimeMillis() - init;
    if (SHOW_CALLER) {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      StackTraceElement caller = stack[2];
      String callerStr = caller.getClassName() + "::" + caller.getMethodName(); // + ":" + caller.getLineNumber();
      System.err.println(t + "  " + callerStr + "  " + msg);
    } else {
      System.err.println(t + "  " + msg);
    }
  }
}
