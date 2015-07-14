package edu.jhu.hlt.tutils.ling;

public enum Language {
  EN {
    public boolean isRoman() { return true; }
  },
  ZH {
    public boolean isRoman() { return false; }
  };
  public abstract boolean isRoman();
}