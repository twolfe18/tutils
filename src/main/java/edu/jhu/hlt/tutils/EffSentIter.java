package edu.jhu.hlt.tutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

public class EffSentIter implements Iterator<EffSent>, AutoCloseable {
  private DepNode.ConllxFileReader iter;
  private BufferedReader r;
  private EffSent cur;
  private MultiAlphabet parseAlph;

  public EffSentIter(File parses, File mentions, MultiAlphabet a, boolean hideDeprels) throws IOException {
    //      Log.info("parses=" + parses.getPath() + " mentions=" + mentions.getPath());
    iter = new DepNode.ConllxFileReader(parses, a, hideDeprels);
    r = FileUtil.getReader(mentions);
    this.parseAlph = a;
    if (iter.hasNext())
      advance();
  }

  public EffSentIter(DepNode.ConllxFileReader parses, InputStream mentions, MultiAlphabet a) throws IOException {
    iter = parses;
    r = new BufferedReader(new InputStreamReader(mentions));
    this.parseAlph = a;
    if (iter.hasNext())
      advance();
  }

  public MultiAlphabet getParseAlph() {
    return parseAlph;
  }

  private void advance() throws IOException {
    cur = null;
    String ms = r.readLine();
    if (ms == null) {
      assert !iter.hasNext();
      return;
    }
    DepNode[] parse = iter.next();
    cur = new EffSent(parse);
    boolean setHeads = true;
    cur.setMentions(ms, setHeads);
  }

  @Override
  public void close() throws IOException {
    iter.close();
    r.close();
  }

  @Override
  public boolean hasNext() {
    //      return iter.hasNext();
    return cur != null;
  }

  @Override
  public EffSent next() {
    EffSent c = cur;
    try {
      advance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return c;
  }
}