package edu.jhu.hlt.tutils.features;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.DocumentTester;
import edu.jhu.hlt.tutils.IntPair;
import edu.jhu.hlt.tutils.IntTrip;

/**
 * Features for things in a document.
 *
 * @author travis
 */
public class DocumentFeatures<T extends Serializable> implements Serializable {
  private static final long serialVersionUID = 8555233879685286282L;

  // TODO could make this a trie
  // TODO could have smart trie node which tries to be 1) dense, then 2) dense but with shift, then 3) sparse
  private final Map<Object, T> feats;
  private final Document doc;

  // Value returned by get when the key does not appear in the map
  private T missing = null;

  public DocumentFeatures(Document doc) {
    this.doc = doc;
    int n = Math.max(16, Math.max(doc.numConstituents(), doc.numTokens()));
    this.feats = new HashMap<>(n);
  }

  /**
   * Useful for setting this to a singleton empty vector for example.
   * By default this is null.
   */
  public void setMissingElement(T missing) {
    this.missing = missing;
  }

  public Document getDocument() {
    return doc;
  }

  public void clear() {
    feats.clear();
  }

  public int size() {
    return feats.size();
  }

  private T get(Object key) {
    T val = feats.get(key);
    if (val == null)
      val = missing;
    return val;
  }

  private T set(Object key, T value) {
    return feats.put(key, value);
  }

  public static final int TOKEN_FEAT = 0;
  public T setTokenFeatures(int tokenIndex, T features) {
    return set(new IntPair(TOKEN_FEAT, tokenIndex), features);
  }
  public T getTokenFeatures(int tokenIndex) {
    return get(new IntPair(TOKEN_FEAT, tokenIndex));
  }

  public static final int CONS_FEAT = 1;
  public T setConstituentFeatures(int consIndex, T features) {
    return set(new IntPair(CONS_FEAT, consIndex), features);
  }
  public T getConstituentFeatures(int consIndex) {
    return get(new IntPair(CONS_FEAT, consIndex));
  }

  public static final int TOKEN2_FEAT = 2;
  public T setTokenFeatures(int token1Index, int token2Index, T features) {
    return set(new IntTrip(TOKEN2_FEAT, token1Index, token2Index), features);
  }
  public T getTokenFeatures(int token1Index, int token2Index) {
    return get(new IntTrip(TOKEN2_FEAT, token1Index, token2Index));
  }

  public static final int CONS2_FEAT = 3;
  public T setConstituentFeatures(int cons1Index, int cons2Index, T features) {
    return set(new IntTrip(CONS2_FEAT, cons1Index, cons2Index), features);
  }
  public T getConstituentFeatures(int cons1Index, int cons2Index) {
    return get(new IntTrip(CONS2_FEAT, cons1Index, cons2Index));
  }

  public static final int AD_HOC_FEAT = 4;
  public T setAdHocFeatures(int index, T features) {
    return set(new IntPair(AD_HOC_FEAT, index), features);
  }
  public T getAdHocFeatures(int index) {
    return get(new IntPair(AD_HOC_FEAT, index));
  }

  public static final int AD_HOC2_FEAT = 5;
  public T setAdHocFeatures(int index1, int index2, T features) {
    return set(new IntTrip(AD_HOC2_FEAT, index1, index2), features);
  }
  public T getAdHocFeatures(int index1, int index2) {
    return get(new IntTrip(AD_HOC2_FEAT, index1, index2));
  }

  /*
   * NOTE: Don't make the mistake of making Templates/Features stateful and mutable!
   * This ruins thread safety.
   *
   * This is only a problem if:
   * - the feature modifies some internal state (Context fixes this)
   * - the feature contains some state like PMI or other counts/averages
   *
   * If templates have some setup to be done (e.g. read in data from a text file
   * into a data structure), then this is fine, as long as feature creation is
   * thread safe.
   */

  public static void main(String[] args) {
    int numFeats = 20;
    int numLabels = 2;
    TemplateManager tm = TemplateManager.getInstance();

    TemplateTree root = new TemplateTree(tm.get("word[0]"));

    TemplateTree t2 = new TemplateTree(tm.get("pos[-1]"));
    root.addChild(t2);
//    t2.addChild(new IndexFlattener());
    t2.addChild(new IndexFlattener.Params(numFeats, numLabels));

    TemplateTree t3 = new TemplateTree(tm.get("pos[0]"));
    root.addChild(t3);
//    t3.addChild(new IndexFlattener());
    t3.addChild(new IndexFlattener.Params(numFeats, numLabels));

    Document doc = DocumentTester.getMockDocument();
    Context ctx = new Context(doc, numLabels, true);
    ctx.token = 2;
    root.apply(ctx);
    root.unapply(ctx);
    System.out.println(ctx);
  }


  /*
   * Why organize templates into a tree?
   * To "share sub-structure" across features.
   * This can mean a few things:
   * - extraction efficiency. Earlier I had been imagining a Feature =
   * List<Template> and FeatureSet = List<Feature>. For efficiency, you can pack
   * all of the List<Template>s into a  single tree. Any descendants of a node
   * in this tree share the List<Template> prefix of that node. This means you
   * can traverse the tree to extract the entire feature set, sharing as much
   * information as possible. If you don't want to do anything tricky, you can
   * just have List<List<Temlate>> encoded as a tree too.
   * If you want parallelism, just start with List<List<Template>> and then pack
   * them into a tree (sort the top list and then merge prefixes).
   * - estimating meaningfulness. There are many features that are not useful in
   * every dataset/task, can we learn these and mask them off? Actually... I'm
   * not sure I can make a broad statement about meaningfulness/propensity to
   * overfit across tasks which would be true, as I think there is a lot of
   * nuance in this issue. For an example of something related, see the next
   * point.
   * - shrinkage towards a mean: Maybe you want to do the "embarrassingly simple
   * domain adaptation" trick. Have a node for the feature, let that node have
   * a weight on labels, and then add children nodes which can too, but with
   * weights which are shrunk towards their parent.
   */

  /*
   * How could we encode the following:
   * Lets say we have a product of a few templates but one of them is
   * lexicalized and we want to *smooth* over this lexical feature.
   * - One way to do this is to have the lexical feature be a set of words, e.g.
   * badMovieReviewWords = [sucks, terrible, boring, ...]
   * - Another way to do this is to use embeddings to smooth away from a proto-
   * typical word. Lets say the prototype is "terrible", but if "horrendous"
   * shows up, the feature fires with weight
   * roundToZeroIfLessThan(0.5, max(0, cosine("terrible", "horrendous")))
   * - Another way to do this is to use a resource which gives you a set of
   * words to backoff to, e.g. WordNet or PPDB. This is like the first option,
   * but you don't provide the words up front. We could treat this as the first
   * option, but since its not hand-chosen, its likely to be lower precision and
   * higher recall. It may be more appropriate to use this as a related feature
   * or as something to regularize over (thing groups in group lasso). So how
   * can we use the tree framework to get leverage out of a feature like this? I
   * sort of imagine these groups as a point of non-determinism. What if at a
   * node like this, lets say we saw the word "book", we hallucinated that we
   * saw something from the resource, e.g. perturbedWord ~ p(synSet(word) |
   * context). The "| context" bit could be optional, or could be implemented as
   * the intersection of synSet(word) with a language model (which depends on
   * the extraction point).
   * This hallucintation seems like a powerful tool for fighting against
   * overfitting with lexical features.
   * This hallucination trick is a little different from group lasso because in
   * group lasso you only consider items in the group when they fire, which may
   * not occur very frequently. For example the "book" -> "novel" perturbation
   * would occur with probability proportional to p("book") and how long you
   * train, whereas with group lasso it also depends on p("novel") appearing in
   * your training corpus. This meanst that the hallucination method is sort of
   * like changing X for one particular f_i(X). I suppose if you wanted to
   * assme that f_i are not independent you could directly change X and
   * re-extract features. Nonetheless, this differs from group lasso where you
   * can imagine building your matrix of F(X) and it never changing.
   *
   * => Perturbations on X (instead of f(x) as I had imagined it) are exactly
   * what Ben was talking about w.r.t. paraphrasing giving you "more training
   * examples".
   */
}

