package edu.jhu.hlt.tutils;

public enum Language {
  EN {
    public boolean isRoman() { return true; }
  },
  ZH {
    public boolean isRoman() { return false; }
  };
  abstract boolean isRoman();
}