package edu.jhu.hlt.tutils.ling;

import edu.jhu.hlt.tutils.Document;

public interface HeadFinder {
  public int head(Document doc, int first, int last);
}
