package edu.jhu.hlt.tutils;

import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.ingest.Ontonotes4;

public class PropbankIngestionTest {

  public static void main(String[] args) throws Exception {
    MultiAlphabet alph = new MultiAlphabet();
    int docIndex = 0;
//    File in = new File("/tmp/on4.concrete.tgz");
//    TarGzCompactCommunicationSerializer ts = new TarGzCompactCommunicationSerializer();
//    try (InputStream is = Files.newInputStream(in.toPath())) {
//      Iterator<Communication> iter = ts.fromTar(is);
//      Communication c = iter.next();
//      Document d = ConcreteIO.makeInstance().communication2Document(c, docIndex++, alph);
//      System.out.println(d);
//    }
    System.out.println("setting up");
    //ConcreteIO cio = ConcreteIO.makeInstance();
    ConcreteIO cio = new ConcreteIO(null, null, null);
    cio.setConstituencyParseToolname("ontonotes4");
    cio.setPropbankToolname("ontonotes4");
    cio.setPosToolName(null);
    cio.setNerToolName(null);

    System.out.println("reading");
    String baseName = "ontonotes-release-4.0/data/files/data/english/annotations/bc/cnn/00/cnn_0000";
    baseName = "/home/travis/code/fnparse/data/" + baseName;
    Ontonotes4 on4 = new Ontonotes4(baseName, "test-document", "body");
    Communication c = on4.parse().iterator().next();

    // Make this only a single sentence document to make debugging easier
    // TODO no autocomplete?

    System.out.println("converting");
    Document d = cio.communication2Document(c, docIndex++, alph);
    System.out.println(d);
  }
}
