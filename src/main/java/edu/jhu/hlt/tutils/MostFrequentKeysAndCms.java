package edu.jhu.hlt.tutils;

import java.io.Serializable;

import edu.jhu.hlt.tutils.CountMinSketch.StringCountMinSketch;

public class MostFrequentKeysAndCms implements Serializable {
  private static final long serialVersionUID = -8469097555538237946L;

  public StringMostFrequentKeys mfk;
  public StringCountMinSketch cms;
  
  public MostFrequentKeysAndCms(int topk, int nhash, int logb) {
    Log.info("topk=" + topk + " nhash=" + nhash + " logb=" + logb);
    mfk = new StringMostFrequentKeys(topk);
    cms = new StringCountMinSketch(nhash, logb, true);
  }

  public void add(String key) {
    int c = cms.apply(key, true);
    mfk.add(key, c);
  }
}
