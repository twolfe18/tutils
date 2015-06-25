package edu.jhu.hlt.tutils.net;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.jhu.hlt.tutils.net.NetworkParameterAveraging.Client;
import edu.jhu.hlt.tutils.net.NetworkParameterAveraging.Server;

public class NetworkParameterAveragingTest {

  double tol = 1e-8;

  @Test
  public void test0() throws Exception {
    int dim = 10;
    int numClients = 12;

    NetworkDenseParams.Avg serverParams = new NetworkDenseParams.Avg(dim);
    Server server = new Server(serverParams);
    Thread serverThread = new Thread(server);
    serverThread.start();

    Client[] clients = new Client[numClients];
    for (int i = 0; i < clients.length; i++)
      clients[i] = new Client(new NetworkDenseParams(dim), "localhost");

//    DenseParams buf = new DenseParams(dim);

    for (int i = 0; i < dim; i++)
      assertEquals(0d, serverParams.getWeight(i), tol);

    int i = 0;
    double[] w = ((NetworkDenseParams) clients[i].getParams()).getWeights();
    w[0] = 10;
    clients[i].averageParameters();
    assertEquals(10, w[0], tol);
    w[0] = 0;
    clients[i].averageParameters();
    assertEquals(5, w[0], tol);
    w[0] = 0;
    clients[i].averageParameters();
    assertEquals(10/3d, w[0], tol);
  }

}
