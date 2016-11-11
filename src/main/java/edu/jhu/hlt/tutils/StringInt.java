package edu.jhu.hlt.tutils;

public class StringInt {

  public final String string;
  public final int integer;
  
  public StringInt(String s, int i) {
    string = s;
    integer = i;
  }
  
  @Override
  public String toString() {
    return "(" + string + " " + integer + ")";
  }
}
