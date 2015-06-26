package edu.jhu.hlt.tutils.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.GZIPOutputStream;

import edu.jhu.hlt.tutils.Log;
import edu.jhu.hlt.tutils.TimeMarker;

/**
 * Class designed to allow relatively easy parameter averaging over a network.
 * Not designed to be fault tolerant: there is a single server node responsible
 * for averaging weights and many client nodes which run (presumably) SGD.
 *
 * Communication occurs as follows: the client sends its current parameters to
 * the server, the server updates its running average, then the server sends the
 * updated running average to the client. Clients may choose how often they wish
 * to send/receive parameter updates.
 *
 * TODO Should I scrap this whole thing and use redis?
 * http://redis.io/commands/incrbyfloat
 *
 * @author travis
 */
public class NetworkParameterAveraging {
  public static final int SERVER_PORT = 7777;


  /**
   * Interfaces used for parameters.
   *
   * NOTE: Neither of these classes have to be thread-safe for this module,
   * which implements the locking needed for safe usage in isolation.
   */
  public static interface Params {
    /**
     * Read from data and set internals. If you want to use Java Serialization,
     * then create a Params class which simply wraps your actual params and
     * creates an ObjectInputStream.
     * @see Params#get(OutputStream)
     */
    void set(InputStream data);
    void get(OutputStream data);
  }
  public static interface AvgParams extends Params {
    void add(InputStream other);
    void getAverage(OutputStream data);
  }


  /** Responsible for parameter averaging */
  public static class Server implements Runnable {
    private AvgParams average;
    private int port;
    public boolean debug = false;

    /**
     * You must give a zero parameter vector so that this class doens't have to
     * construct one (it doesn't know how).
     */
    public Server(AvgParams zero) {
      this(zero, SERVER_PORT);
    }

    public Server(AvgParams zero, int port) {
      this.average = zero;
      this.port = port;
    }

    private File checkpointDir;
    private int saveIntervalInSeconds;
    public void saveModels(File checkpointDir, int saveIntervalInSecons) {
      Log.info("saving parameters to " + checkpointDir.getPath() + " every " + saveIntervalInSecons + " seconds");
      if (!checkpointDir.isDirectory() || checkpointDir.listFiles().length > 0)
        throw new IllegalArgumentException("must provide an empty directory");
      if (saveIntervalInSecons < 1)
        throw new IllegalArgumentException();
      this.checkpointDir = checkpointDir;
      this.saveIntervalInSeconds = saveIntervalInSecons;
    }

    public int getPort() {
      return port;
    }

    public AvgParams getParams() {
      return average;
    }

    @Override
    public String toString() {
      try {
        return "(" + getClass().getName()
            + " hostName=" + InetAddress.getLocalHost().getHostName()
            + " port=" + port + ")";
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void run() {
      try {
        TimeMarker timer = new TimeMarker();
        ServerSocket ss = new ServerSocket(port);
        while (true) {
          synchronized (average) {
            Socket client = ss.accept();

            if (debug) {
              Log.info("just accepted connection on server: "
                  + client.getInetAddress() + ":" + client.getPort());
            }

            InputStream is = client.getInputStream();
            OutputStream os = client.getOutputStream();

            // Receive the params, update average
            average.add(is);

            if (debug)
              Log.info("received update about to send back average");

            // Send back the average
            average.getAverage(os);
            os.flush();

            if (debug)
              Log.info("done transaction, cleaning up");

            client.close();


            // Check if we should save the parameters
            if (checkpointDir != null && timer.enoughTimePassed(saveIntervalInSeconds))
              saveModel();
          }
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void saveModel() {
      // Remove the oldest file
      int maxFiles = 10;
      File[] old = checkpointDir.listFiles();
      if (old != null && old.length >= maxFiles) {
        Arrays.sort(old, new Comparator<File>() {
          @Override
          public int compare(File o1, File o2) {
            long d = o1.lastModified() - o2.lastModified();
            assert d < Integer.MAX_VALUE && d > Integer.MIN_VALUE;
            return (int) d;
          }
        });
        Log.info("removing " + old[0].getPath() + " to make room, maxFiles=" + maxFiles);
        old[0].delete();
      }

      // Save the current one
      File f = new File(checkpointDir,
          "average-" + (System.currentTimeMillis()/1000) + ".jser.gz");
      try (FileOutputStream fos = new FileOutputStream(f)) {
        average.getAverage(new GZIPOutputStream(fos));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  /** Client */
  public static class Client {
    private Params params;
    private String serverHostName;
    private int serverPort;
    private TimeMarker timer;
    public int secondsBetweenContactingServer = 2 * 60;
    public boolean debug = false;
 
    public Client(Params params, String serverHostName) {
      this(params, serverHostName, SERVER_PORT);
    }

    public Client(Params params, String serverHostName, int port) {
      this.params = params;
      this.serverHostName = serverHostName;
      this.serverPort = port;
      this.timer = new TimeMarker();
      timer.enoughTimePassed(1);
    }

    public Params getParams() {
      return params;
    }

    /**
     * Call this every time the parameters change, and this will occasionally
     * trigger communication with the server in order to average parameters.
     *
     * @return true if the parameters were averaged.
     */
    public boolean paramsChanged() {
      if (!timer.enoughTimePassed(secondsBetweenContactingServer))
        return false;
      try {
        averageParameters();
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public void averageParameters() throws UnknownHostException, IOException {
      synchronized (params) {
        if (debug)
          Log.info("sending params to: " + serverHostName + ":" + serverPort);
        Socket s = new Socket(serverHostName, serverPort);
        InputStream is = s.getInputStream();
        OutputStream os = s.getOutputStream();

        // Send the parameters
        params.get(os);
        os.flush();

        if (debug)
          Log.info("done sending params, about to receive average");

        // Receive the average
        params.set(is);

        if (debug)
          Log.info("done, cleaning up");

        s.close();
      }
    }
  }

}
