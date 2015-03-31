package edu.jhu.hlt.tutils;

public class WordShape {

  public static String wordShape(String s) {
    return s.replaceAll("[A-Z]", "X")
        .replaceAll("[a-z]", "x")
        .replaceAll("\\d", "0")
        .replaceAll("X{4,}", "X+")
        .replaceAll("X{3}", "X3")
        .replaceAll("X{2}", "X2")
        .replaceAll("x{4,}", "x+")
        .replaceAll("x{3}", "x3")
        .replaceAll("x{2}", "x2")
        .replaceAll("0{5,}", "0+");
  }
}
