package edu.jhu.hlt.tutils.features;

import java.util.HashMap;
import java.util.Map;

/**
 * Supposed to be a singleton which holds all the features in the system.
 *
 * PROPOSAL: There is a singleton instance FeatureManager, but anyone may
 * add templates/features to it. This will be useful if there is some setup
 * work to be done to create a template (e.g. read in a file into a data
 * structure) which may not be appropriate to run as a static initializer
 * every time the class is loaded.
 *
 * TODO Should there be many instances of this where one is the "master"
 * instance which has all of the features/templates, and then you "sub-select"
 * the features/templates you want?
 *
 * @author travis
 */
public class TemplateManager {
  private Map<String, Template> templates;

  private TemplateManager() {
    templates = new HashMap<>();
  }

  public synchronized void add(Template t) {
    if (t.name == null)
      throw new IllegalArgumentException("templates must have names");
    if (t.name.indexOf('+') >= 0)
      throw new IllegalArgumentException("templates may not contain '+': " + t.name);
    if (t.name.indexOf('*') >= 0)
      throw new IllegalArgumentException("templates may not contain '*': " + t.name);
    t.index = templates.size();
    Template old = templates.put(t.name, t);
    if (old != null)
      throw new RuntimeException("dup: " + t.name);
  }

  public Template get(String name) {
    return templates.get(name);
  }

  private static TemplateManager SINGLETON;

  public static synchronized TemplateManager getInstance() {
    if (SINGLETON != null)
      return SINGLETON;
    // Include the "default features" here which are fast to add.
    // Other methods may add to Templates to the singleton instance.
    SINGLETON = new TemplateManager();
    for (int i = -3; i <= 3; i++) {
      final int offset = i;
      SINGLETON.add(new Template("pos[" + i + "]") {
        @Override
        public void accept(Context c) {
          if (c.token < 0) {
            c.cancel();
          } else {
            int t = c.token + offset;
            int p;
            if (t < 0)
              p = c.doc.beforeDoc();
            else if (t >= c.doc.numTokens())
              p = c.doc.afterDoc();
            else
              p = c.doc.getPosH(c.token);
            c.add(p);
          }
        }
      });
      SINGLETON.add(new Template("word[" + i + "]") {
        @Override
        public void accept(Context c) {
          if (c.token < 0) {
            c.cancel();
          } else {
            int t = c.token + offset;
            int p;
            if (t < 0)
              p = c.doc.beforeDoc();
            else if (t >= c.doc.numTokens())
              p = c.doc.afterDoc();
            else
              p = c.doc.getWord(c.token);
            c.add(p);
          }
        }
      });
    }
    return SINGLETON;
  }
}