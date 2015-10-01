package org.irenical.thrifty;

import java.util.Map;
import java.util.WeakHashMap;

import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.server.TThreadedSelectorServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.irenical.jindy.Config;
import org.irenical.jindy.ConfigFactory;
import org.irenical.jindy.ConfigNotFoundException;
import org.irenical.lifecycle.LifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftServerLifeCycle implements ThriftServerSettings, LifeCycle {

  private static final Logger LOG = LoggerFactory.getLogger(ThriftServerLifeCycle.class);

  private static final int DEFAULT_SHUTDOWN_TIMEOUT_MILLIS = 10000;

  private static final int DEFAULT_SELECTOR_THREADS = 2;

  private static final int DEFAULT_WORKER_THREADS = 4;

  private final Config config;

  private final TProcessor processor;

  private final Map<ServerContext, Long> pendingSessions = new WeakHashMap<ServerContext, Long>();

  private volatile boolean shutdown = false;

  private volatile Thread serverThread;

  private volatile UberThriftServer thriftServer;

  private final String configPropertyPrefix;

  /**
   * A thrift server lifecycle start() will fire a new thread The following
   * properties will be read: <br>
   * <b>[prefix.]thrift.listenPort: mandatory</b><br>
   * [prefix.]thrift.selectorThreads<br>
   * [prefix.]thrift.workerThreads<br>
   * [prefix.]thrift.clientTimeout<br>
   * [prefix.]thrift.shutdownTimeoutMillis<br>
   * 
   * @param processor
   *          - your thrift processor
   * @param config
   *          - the config object to read properties from,
   *          ConfigFactory.getConfig() will be used by default
   * @param configPropertyPrefix
   *          - an optional prefix for your properties
   */
  public ThriftServerLifeCycle(TProcessor processor, Config config, String configPropertyPrefix) {
    this.processor = processor;
    this.config = config == null ? ConfigFactory.getConfig() : config;
    this.configPropertyPrefix = configPropertyPrefix == null || configPropertyPrefix.trim().isEmpty() ? "" : (configPropertyPrefix.endsWith(".") ? configPropertyPrefix : (configPropertyPrefix + "."));
  }

  @Override
  public void start() {
    fireup(processor);
  }

  @Override
  public void stop() {
    doShutdown(config.getInt(configPropertyPrefix + SHUTDOWN_TIMEOUT_MILLIS, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS));
  }

  @Override
  public boolean isRunning() {
    return thriftServer.isServing();
  }

  /**
   * Non-blocking: creates and lauches a thread running thrift server
   * 
   * @param processor
   *          - thrift processor
   */
  private void fireup(TProcessor processor) {
    serverThread = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          boot(processor);
        } catch (Throwable e) {
          LOG.error("An error occurred while running the server... exiting", e);
          shutdown(config.getInt(configPropertyPrefix + SHUTDOWN_TIMEOUT_MILLIS, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS));
        } finally {
          synchronized (ThriftServerLifeCycle.this) {
            ThriftServerLifeCycle.this.notifyAll();
          }
        }
      }
    }, "Thrift Server");
    serverThread.setDaemon(false);
    serverThread.start();
  }

  /**
   * Blocks current thread until server is serving Returns instantly if the
   * server was shutdown previously
   * 
   * @return - wether the thrift server started serving or not. Will return
   *         false if a shutdown occured.
   * @throws InterruptedException
   *           - can happen while waiting
   */
  // public synchronized boolean waitUntilServing() throws InterruptedException
  // {
  // while (!shutdown && (thriftServer == null || !thriftServer.isServing())) {
  // wait(180); // magic number L
  // }
  // return !shutdown;
  // }

  public void shutdown(int timeoutMillis) {
    doShutdown(timeoutMillis);
  }

  private void doShutdown(int timeoutMillis) {
    shutdown = true;
    if (thriftServer != null) {
      LOG.info("Stop accepting new connections");
      thriftServer.haltListening();
      LOG.info("Waiting for pending requests");
      waitForPendingRequests(timeoutMillis);
      LOG.info("Stopping server");
      thriftServer.stop();
      LOG.info("Server stopped");
    } else {
      LOG.info("Server not running, there is nothing to stop");
    }
  }

  private void waitForPendingRequests(int timeoutMillis) {
    synchronized (pendingSessions) {
      long start = System.currentTimeMillis();
      long remaining = start + timeoutMillis - System.currentTimeMillis();
      while (pendingSessions.size() > 0 && remaining > 0) {
        LOG.info("Waiting for " + pendingSessions.size() + " pending requests");
        try {
          pendingSessions.wait(remaining);
          remaining = start + timeoutMillis - System.currentTimeMillis();
        } catch (InterruptedException e) {
          LOG.warn("Interrupted while waiting for pending requests", e);
        }
      }
      LOG.info("Done waiting for pending requests (" + pendingSessions.size() + " remaining)");
    }
  }

  private void boot(TProcessor processor) throws NumberFormatException, TTransportException, InterruptedException, ConfigNotFoundException {
    int port = config.getMandatoryInt(configPropertyPrefix + PORT);
    int selectors = config.getInt(configPropertyPrefix + SELECTOR_THREADS, DEFAULT_SELECTOR_THREADS);
    int workers = config.getInt(configPropertyPrefix + WORKER_THREADS, DEFAULT_WORKER_THREADS);
    fireServer(port, selectors, workers, processor);
  }

  private <PROCESSOR extends TProcessor> void fireServer(int port, int selectorThreads, int workerThreads, PROCESSOR processor) throws TTransportException, InterruptedException {
    TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
    TThreadedSelectorServer.Args args = new TThreadedSelectorServer.Args(transport);
    args.transportFactory(new TFramedTransport.Factory());
    args.protocolFactory(new TBinaryProtocol.Factory());
    args.processor(processor);
    args.selectorThreads(selectorThreads);
    args.workerThreads(workerThreads);
    thriftServer = new UberThriftServer(args);
    LOG.info("Starting server on port " + port);
    thriftServer.setServerEventHandler(new TServerEventHandler() {

      @Override
      public void preServe() {
      }

      @Override
      public ServerContext createContext(TProtocol input, TProtocol output) {
        ThriftRequest serverContext = new ThriftRequest();
        synchronized (pendingSessions) {
          LOG.debug("Received new session");
          pendingSessions.put(serverContext, System.currentTimeMillis());
          LOG.debug("Current sessions: " + pendingSessions.size());
        }
        return serverContext;
      }

      @Override
      public void processContext(ServerContext serverContext, TTransport input, TTransport output) {
        if (shutdown) {
          LOG.debug("Closing connection for new request");
          output.close();
        } else {
          LOG.debug("Processing new request");
        }
      }

      @Override
      public void deleteContext(ServerContext serverContext, TProtocol input, TProtocol output) {
        synchronized (pendingSessions) {
          pendingSessions.remove(serverContext);
          LOG.debug("Pending session dispached");
          LOG.debug("Current sessions: " + pendingSessions.size());
          if (shutdown) {
            pendingSessions.notifyAll();
          }
        }
      }

    });
    thriftServer.serve();
  }

}
