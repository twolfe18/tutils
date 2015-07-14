package edu.jhu.hlt.tutils.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.jhu.hlt.tutils.Log;

/**
 * Code to parse the Propbank frame index XML files.
 *
 * @author travis
 */
public class PropbankFrameIndex implements Serializable {
  private static final long serialVersionUID = 3986362260702735369L;

  // e.g. "cease-n.xml" contains the <predicate lemma="cease_fire">
  // If false, use "cease_file-n-1" for the id. If true use "cease-n-1".
  public static boolean USE_FILENAME_FOR_PROP_NAME = true;

  // Taken from
  // file:///home/travis/code/fnparse/data/ontonotes-release-4.0/docs/propbank/english-propbank.pdf
  // as it appears to be a longer list than:
  // http://verbs.colorado.edu/~mpalmer/projects/ace/PBguidelines.pdf
  public static final List<String> MODIFIER_ROLES = Arrays.asList(
      "ARGM-COM",
      "ARGM-LOC",
      "ARGM-DIR",
      "ARGM-GOL",
      "ARGM-MNR",
      "ARGM-TMP",
      "ARGM-EXT",
      "ARGM-REC",
      "ARGM-PRD",
      "ARGM-PRP",
      "ARGM-CAU",
      "ARGM-DIS",
      "ARGM-MOD",
      "ARGM-NEG",
      "ARGM-DSP",
      "ARGM-LVB",
      "ARGM-ADV",
      "ARGM-ADJ",
      "LINK-SLC",
      "LINK-PCR",
      "ARGM-PNC",
      "ARGM-PRR",
      "ARGM-PRX");

  public static String get(NamedNodeMap attr, String key) {
    return get(attr, key, true);
  }

  public static String get(NamedNodeMap attr, String key, boolean allowFailure) {
    Node n = attr.getNamedItem(key);
    if (n == null) {
      if (!allowFailure)
        throw new RuntimeException("could not find " + key);
      return null;
    }
    String r = n.getNodeValue();
    if (!allowFailure && r == null)
      throw new RuntimeException("could not find " + key);
    return r;
  }

  public static Node getChild(Node n, String childName) {
    NodeList children = n.getChildNodes();
    Node r = null;
    for (int i = 0; i < children.getLength(); i++) {
      Node c = children.item(i);
      if (c.getNodeName().equals(childName)) {
        assert r == null : "childName=" + childName + " r=" + r + " c=" + c;
        r = c;
      }
    }
    return r;
  }

  /**
   * Note that this class does not include ARGM roles.
   */
  public static class PropbankFrame implements Serializable {
    private static final long serialVersionUID = -5093553346009596687L;
    public final String id;        // e.g. "steal.01"
    public final String vncls;     // e.g. "10.6"
    public final String name;      // e.g. "steal, remove illegally"
    private List<PropbankRole> roles; // no guarantees on order

    /**
     * @param rolesetNode is a "roleset" node in the Propbank XML schema
     * @param pos is the part of speech determined from the file name, e.g. "v"
     */
    public PropbankFrame(File frameFile, Node rolesetNode, String pos) {
      NamedNodeMap attr = rolesetNode.getAttributes();

      String id = get(attr, "id", false);  // e.g. "drop.01"
      String[] idtoks = id.split("\\.");
      if (idtoks.length != 2) throw new RuntimeException();
      String pred;
      if (USE_FILENAME_FOR_PROP_NAME) {
        int i = frameFile.getName().lastIndexOf('.');
        assert i == frameFile.getName().length() - 4;
        pred = frameFile.getName().substring(0, i);
        this.id = pred + "-" + idtoks[1].replaceFirst("^0+", "");
      } else {
        pred = idtoks[0];
        this.id = pred + "-" + pos + "-" + idtoks[1].replaceFirst("^0+", "");
      }
      // e.g. "drop-v-1"
      // not necessarily a number at the end because of things like "take.LV",
      // where LV == "light verb"

      this.vncls = get(attr, "vncls");
      this.name = get(attr, "name");
      this.roles = new ArrayList<>();
      NodeList children = getChild(rolesetNode, "roles").getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node n = children.item(i);
        if (!n.getNodeName().equals("role"))
          continue;

        // For some reason, some of these have role appear to have an empty role
        // name... Discard these (there is no way to refer to them anyway).
        String roleName = get(n.getAttributes(), "n", false);
        if (roleName.isEmpty()) {
          Log.warn(this.id + " has role with no name!");
          continue;
//          throw new RuntimeException(this.id + " has a role with no name!");
        }

        roles.add(new PropbankRole(n));
      }
    }

    /** Does not include ARM-* roles */
    public int numRoles() {
      return roles.size();
    }

    /** Does not include ARM-* roles */
    public PropbankRole getRole(int i) {
      return roles.get(i);
    }

    /** Does not include ARM-* roles */
    public List<PropbankRole> getRoles() {
      return roles;
    }

    @Override
    public String toString() {
      return "<" + id + " " + name + " vn=" + vncls + " roles=" + roles + ">";
    }
  }

  public static class PropbankRole implements Serializable {
    private static final long serialVersionUID = 3914325013279654223L;
    public final String role;           // e.g. "ARG0" or "ARGM"
    public final String roleFeatures;   // e.g. "EXT", only defined for ARGM
    public final String description;    // e.g. "agent, driver, yachter"
    private List<String> vncls;         // values e.g. "51.4.1"
    private List<String> vntheta;       // values e.g. "agent"

    public PropbankRole(Node roleNode) {
      NamedNodeMap attr = roleNode.getAttributes();
      this.role = "ARG" + get(attr, "n", false).toUpperCase();
      assert !role.equals("ARG");

      String feats = get(attr, "f");
      this.roleFeatures = feats == null || feats.isEmpty() ? null : feats.toUpperCase();

      this.description = get(attr, "descr");
      this.vncls = new ArrayList<>();
      this.vntheta = new ArrayList<>();
      NodeList children = roleNode.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node n = children.item(i);
        if (n.getNodeName().equals("vnrole")) {
          NamedNodeMap attr2 = n.getAttributes();
          this.vncls.add(get(attr2, "vncls", false));
          this.vntheta.add(get(attr2, "vntheta", false));
        }
      }
      if (vncls.size() == 0) {
        vncls = Collections.emptyList();
        vntheta = Collections.emptyList();
      }
    }

    /** Returns a full string like "ARG2" or "ARGM-LOC" */
    public String getLabel() {
      if (roleFeatures != null && role.equals("ARGM"))
        return role + "-" + roleFeatures;
      return role;
    }

    public int numVerbNetMappings() {
      assert vncls.size() == vntheta.size();
      return vncls.size();
    }

    public String getVncls(int i) {
      return vncls.get(i);
    }

    public String getVntheta(int i) {
      return vntheta.get(i);
    }

    @Override
    public String toString() {
      return "<" + getLabel() + " \"" + description + "\" vn=" + vncls + "/" + vntheta + ">";
    }
  }

  // e.g. /home/travis/code/fnparse/data/ontonotes-release-4.0/data/files/data/english/metadata/frames
  private File dir;
  private Map<String, PropbankFrame> byName;

  public PropbankFrameIndex(File dir) {
    if (!dir.isDirectory())
      throw new IllegalArgumentException("not a directory: " + dir.getPath());
    this.dir = dir;
    this.byName = new HashMap<>();
    try {
      parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public List<PropbankFrame> getAllFrames() {
    List<PropbankFrame> l = new ArrayList<>();
    l.addAll(byName.values());
    return l;
  }

  private void parse() throws Exception {
    Log.info("reading frames from " + dir.getPath());
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    for (File frameFile : dir.listFiles()) {
      if (!frameFile.getName().endsWith(".xml") || frameFile.getName().startsWith("."))
        continue;

      String fn = frameFile.getName();
      int s = fn.lastIndexOf('-');
      int e = fn.lastIndexOf('.');
      String pos = fn.substring(s + 1, e);
      assert Arrays.asList("n", "v").contains(pos);

      Document doc = dBuilder.parse(frameFile);
      Element frameset = doc.getDocumentElement();
      NodeList pnl = frameset.getElementsByTagName("predicate");
      for (int i = 0; i < pnl.getLength(); i++) {
        NodeList rolesetsAndJunk = pnl.item(i).getChildNodes();
        for (int j = 0; j < rolesetsAndJunk.getLength(); j++) {
          Node n = rolesetsAndJunk.item(j);
          if (!n.getNodeName().equals("roleset"))
            continue;
          PropbankFrame f = new PropbankFrame(frameFile, n, pos);
          //System.out.println("adding " + f);
          PropbankFrame old = byName.put(f.id, f);
          if (old != null)
            throw new RuntimeException("key=" + f.id + " f1=" + old + " f2=" + f);
        }
      }
    }
    Log.info("read " + byName.size() + " frames");
  }

  public static void main(String[] args) throws Exception {
    //File dir = new File("/home/travis/code/fnparse/data/ontonotes-release-4.0/data/files/data/english/metadata/frames");
    File dir = new File("/home/travis/code/fnparse/data/ontonotes-release-5.0/LDC2013T19/data/files/data/english/metadata/frames");
    PropbankFrameIndex fi = new PropbankFrameIndex(dir);
    long start = System.currentTimeMillis();
    System.out.println(System.currentTimeMillis() - start);

    // See how long it takes to (de)serialize with Java serialization
    // => 3200 ms vs 500 ms, about a 6x speedup (maybe more)
    File f = new File("/tmp/foo.ser");
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
    oos.writeObject(fi);
    oos.close();
    start = System.currentTimeMillis();
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
    ois.readObject();
    ois.close();
    System.out.println(System.currentTimeMillis() - start);
  }
}
