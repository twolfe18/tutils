package edu.jhu.hlt.tutils.datasets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
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

/**
 * Code to parse the Propbank frame index XML files.
 *
 * @author travis
 */
public class PropbankFrameIndex implements Serializable {
  private static final long serialVersionUID = 3986362260702735369L;

  public static String get(NamedNodeMap attr, String key) {
    Node n = attr.getNamedItem(key);
    if (n == null)
      return null;
    return n.getNodeValue();
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

  public static class Frame implements Serializable {
    private static final long serialVersionUID = -5093553346009596687L;
    private String id;        // e.g. "steal.01"
    private String vncls;     // e.g. "10.6"
    private String name;      // e.g. "steal, remove illegally"
    private List<Role> roles; // no guarantees on order

    /**
     * @param rolesetNode is a "roleset" node in the Propbank XML schema
     * @param pos is the part of speech determined from the file name, e.g. "v"
     */
    public Frame(Node rolesetNode, String pos) {
      NamedNodeMap attr = rolesetNode.getAttributes();
      //System.out.println("  attr=" + attr.getNamedItem("id").getNodeValue());

      this.id = get(attr, "id");  // e.g. "drop.01"
      String[] idtoks = this.id.split("\\.");
      if (idtoks.length != 2) throw new RuntimeException();
      this.id = idtoks[0] + "-" + pos + "-" + Integer.parseInt(idtoks[1]);
      // e.g. "drop-v-1"

      this.vncls = get(attr, "vncls");
      this.name = get(attr, "name");
      this.roles = new ArrayList<>();
      NodeList children = getChild(rolesetNode, "roles").getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node n = children.item(i);
        //System.out.println("  frame.n=" + n.getNodeName());
        if (!n.getNodeName().equals("role"))
          continue;
        roles.add(new Role(n));
      }
    }

    @Override
    public String toString() {
      return "<" + id + " " + name + " vn=" + vncls + " roles=" + roles + ">";
    }
  }

  public static class Role implements Serializable {
    private static final long serialVersionUID = 3914325013279654223L;
    private String name;            // e.g. "0"
    private String description;     // e.g. "agent, driver, yachter"
    private List<String> vncls;     // values e.g. "51.4.1"
    private List<String> vntheta;   // values e.g. "agent"

    public Role(Node roleNode) {
      //System.out.println("   roleNode=" + roleNode);
      NamedNodeMap attr = roleNode.getAttributes();
      this.name = get(attr, "n");
      this.description = get(attr, "descr");
      this.vncls = new ArrayList<>();
      this.vntheta = new ArrayList<>();
      NodeList children = roleNode.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node n = children.item(i);
        if (n.getNodeName().equals("vnrole")) {
          NamedNodeMap attr2 = n.getAttributes();
          this.vncls.add(get(attr2, "vncls"));
          this.vntheta.add(get(attr2, "vntheta"));
        }
      }
      if (vncls.size() == 0) {
        vncls = Collections.emptyList();
        vntheta = Collections.emptyList();
      }
    }

    @Override
    public String toString() {
      return "<ARG" + name + " " + description + " vn=" + vncls + "/" + vntheta + ">";
    }
  }

  // e.g. /home/travis/code/fnparse/data/ontonotes-release-4.0/data/files/data/english/metadata/frames
  private File dir;
  private Map<String, Frame> byName;

  public PropbankFrameIndex(File dir) {
    this.dir = dir;
    this.byName = new HashMap<>();
  }

  private void parse() throws Exception {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

    for (File frameFile : dir.listFiles()) {
      if (!frameFile.getName().endsWith(".xml") || frameFile.getName().startsWith("."))
        continue;
//      System.out.println(frameFile.getName());

//      String[] toks = frameFile.getName().split("-");
//      System.out.println("toks=" + Arrays.toString(toks));
//      if (toks.length != 2) throw new RuntimeException();
//      String pos = toks[1].split("\\.")[0];

      String fn = frameFile.getName();
      int s = fn.lastIndexOf('-');
      int e = fn.lastIndexOf('.');
      String pos = fn.substring(s + 1, e);
//      System.out.println("pos=" + pos);


      Document doc = dBuilder.parse(frameFile);
      Element frameset = doc.getDocumentElement();
      //System.out.println(frameset.getChildNodes());
      NodeList pnl = frameset.getElementsByTagName("predicate");
      for (int i = 0; i < pnl.getLength(); i++) {
        //Frame f = new Frame(pnl.item(i));
        //System.out.println(f);
        NodeList rolesetsAndJunk = pnl.item(i).getChildNodes();
        for (int j = 0; j < rolesetsAndJunk.getLength(); j++) {
          Node n = rolesetsAndJunk.item(j);
          if (!n.getNodeName().equals("roleset"))
            continue;
          Frame f = new Frame(n, pos);
          //System.out.println(f);
          Frame old = byName.put(f.id, f);
          if (old != null) throw new RuntimeException("key=" + f.id);
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    File dir = new File("/home/travis/code/fnparse/data/ontonotes-release-4.0/data/files/data/english/metadata/frames");
    PropbankFrameIndex fi = new PropbankFrameIndex(dir);
    long start = System.currentTimeMillis();
    fi.parse();
    System.out.println(System.currentTimeMillis() - start);

    // See how long it takes to (de)serialize with Java serialization
    // => 3200 ms vs 500 ms, about a 6x speedup (maybe more)
    File f = new File("/tmp/foo.ser");
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
    oos.writeObject(fi);
    oos.close();
    start = System.currentTimeMillis();
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
    Object foo = ois.readObject();
    ois.close();
    System.out.println(System.currentTimeMillis() - start);
  }
}
