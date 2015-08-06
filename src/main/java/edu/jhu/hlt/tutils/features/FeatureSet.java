package edu.jhu.hlt.tutils.features;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.jhu.hlt.tutils.Document;
import edu.jhu.hlt.tutils.DocumentTester;
import edu.jhu.hlt.tutils.PennTreeReader;
import edu.jhu.prim.vector.IntDoubleVector;

/**
 * Takes a string descripting a feature set and constructs {@link TemplateTree}s
 * to represent it. This is a convenience class primarily centered around the
 * {@link FeatureSet#extract(DocumentFeatures)} method.
 *
 * @author travis
 */
public class FeatureSet implements Serializable {
  private static final long serialVersionUID = -1774745034595045590L;

  enum Mode {
    TOKEN,
    CONSTITUENT,
    // TODO more, see DocumentFeatures
  }

  private Mode mode;
  private String description; // original string used to fully describe the feature set
  private transient List<TemplateTree> roots;
  private transient Features feats;
  private transient boolean debug = false;

  private FeatureSet(String description) {
    this.description = description;
    this.roots = new ArrayList<>();
    this.feats = new Features();
  }

  public Mode getMode() {
    return mode;
  }

  public String getDescription() {
    return description;
  }

  public DocumentFeatures<IntDoubleVector> extract(Document doc) {
    DocumentFeatures<IntDoubleVector> df = new DocumentFeatures<>(doc);
    extract(df);
    return df;
  }

  public void extract(DocumentFeatures<IntDoubleVector> addTo) {
    Document doc = addTo.getDocument();
    Context ctx = new Context(doc, 1);
    if (mode == Mode.TOKEN) {
      int n = doc.numTokens();
      for (int i = 0; i < n; i++) {
        ctx.clear();
        ctx.token = i;
        ctx.doc = doc;
        for (TemplateTree tt : roots)
          tt.apply(ctx);
        IntDoubleVector fv = ctx.getExtractionsAsIDV();
        addTo.setTokenFeatures(i, fv);
        if (debug)
          System.out.println("token=" + i + " features=" + fv);
      }
    } else {
      throw new RuntimeException("implement me");
    }
  }

  public static FeatureSet fromLines(String mode, List<String> products) {
    return fromLines(mode, products, "\\*", false);
  }
  public static FeatureSet fromLines(String mode, List<String> products, String prodSymbolRegexp, boolean packIntoTree) {
    // Build description
    StringBuilder sb = new StringBuilder();
    for (String p : products) {
      int i = p.indexOf('\n');
      if (i >= 0)
        throw new IllegalArgumentException("products may not contain newlines");
      sb.append(p);
      sb.append('\n');
    }
    FeatureSet fs = new FeatureSet(sb.toString());
    fs.mode = Mode.valueOf(mode);

    if (packIntoTree) {
      List<String> temp = new ArrayList<>();
      temp.addAll(products);
      Collections.sort(temp);
      products = temp;
    }

    Features feats = new Features();
    TemplateManager tm = TemplateManager.getInstance();
    for (String p : products) {
      String[] terms = p.split(prodSymbolRegexp);
      if (packIntoTree) {
        throw new RuntimeException("implement me");
      } else {
        TemplateTree node = null;
        TemplateTree preTerm = null;
        for (int i = terms.length - 1; i >= 0; i--) {
          terms[i] = terms[i].trim();
          Template t = tm.get(terms[i]);
          if (t == null)
            throw new RuntimeException("couldn't find template: " + terms[i]);
          TemplateTree tt = new TemplateTree(t);
          if (node != null)
            tt.addChild(node);
          else
            preTerm = tt;
          node = tt;
        }
        preTerm.addChild(feats);
        fs.roots.add(node);
      }
    }
    return fs;
  }

  /** I never got this working */
  public static FeatureSet fromSExp(String sexp) {
    TemplateManager tm = TemplateManager.getInstance();
    PennTreeReader.Node root = PennTreeReader.parse(sexp);
    FeatureSet fs = new FeatureSet(sexp);
    fs.mode = Mode.valueOf(root.getCategory());
    System.out.println("name=" + sexp);
    for (PennTreeReader.Node child : root.getChildren()) {
      TemplateTree r = build(child, fs.feats, tm);
      fs.roots.add(r);
      System.out.println("child=" + child.getContents());
      System.out.println("r=" + r);
    }
    return fs;
  }

  private static TemplateTree build(PennTreeReader.Node sexp, Features feats, TemplateManager tm) {
    System.out.println("sexp=" + sexp);
    String tName = sexp.getCategory();
    Template t = tm.get(tName);
    if (t == null)
      throw new RuntimeException("failed to find template: tName=" + tName);
    TemplateTree tt = new TemplateTree(t);
    if (sexp.isLeaf()) {
      tt.addChild(feats);
    } else {
      for (PennTreeReader.Node child : sexp.getChildren()) {
        TemplateTree ttc = build(child, feats, tm);
        tt.addChild(ttc);
      }
    }
    return tt;
  }

  public static void main(String[] args) {
//    String desc = "(FS"
//        + "(word[0] "
//          + "(pos[-1] (pos[0]) (pos[-1]) (pos[1] (word[2]))) (pos[-2])"
//        + ")";
    List<String> feats = Arrays.asList(
        "word[0]",
        "word[0] * word[1]",
        "word[0] * pos[1] * pos[-1]");
    FeatureSet fs = FeatureSet.fromLines("TOKEN", feats);
    fs.debug = true;
    Document doc = DocumentTester.getMockDocument();
    DocumentFeatures<IntDoubleVector> df = fs.extract(doc);
    System.out.println(df);
    DocumentFeatures<IntDoubleVector> df2 = fs.extract(doc);
    System.out.println(df2);
    DocumentFeatures<IntDoubleVector> df3 = fs.extract(doc);
    System.out.println(df3);
  }
}
