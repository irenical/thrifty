package org.irenical.thrifty;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
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

  private static final int DEFAULT_STARTUP_TIMEOUT_SECONDS = 180;

  private final Config config;

  private final TProcessor processor;

  private final Map<ServerContext, Long> pendingSessions = Collections.synchronizedMap(new WeakHashMap<ServerContext, Long>());

  private volatile boolean shutdown = false;

  private volatile UberThriftServer thriftServer;

  /**
   * A thrift server lifecycle start() will fire a new thread The following
   * properties will be read: <br>
   * <b>[prefix.]thrift.listenPort: mandatory</b><br>
   * [prefix.]thrift.selectorThreads<br>
   * [prefix.]thrift.workerThreads<br>
   * [prefix.]thrift.clientTimeout<br>
   * [prefix.]thrift.shutdownTimeoutMillis<br>
   * [prefix.]thrift.protocol
   * 
   * The thrift.protocol property can have the following values: binary,
   * compact, json
   * 
   * @param processor
   *          - your thrift processor
   * @param config
   *          - the config object to read properties from,
   *          ConfigFactory.getConfig() will be used by default
   */
  public ThriftServerLifeCycle(TProcessor processor, Config config) {
    this.processor = processor;
    this.config = config == null ? ConfigFactory.getConfig() : config;
  }

  @Override
  public void start() throws InterruptedException {
    fireup(processor);
  }

  @Override
  public void stop() {
    doShutdown(config.getInt(SHUTDOWN_TIMEOUT_MILLIS, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS));
  }

  @Override
  public boolean isRunning() {
    return thriftServer != null && thriftServer.isServing();
  }

  /**
   * Creates and launches a thread running a thrift server. Block the current
   * thread until the server is accepting requests, or the thread is interrupted
   * 
   * @param processor
   *          - thrift processor
   * @throws InterruptedException
   *           - can happen while waiting the thrift server to boot
   */
  private void fireup(TProcessor processor) throws InterruptedException {
    Thread serverThread = new Thread(() -> {
      try {
        boot(processor);
      } catch (Throwable e) {
        LOG.error("An error occurred while running the server... exiting", e);
        shutdown(config.getInt(SHUTDOWN_TIMEOUT_MILLIS, DEFAULT_SHUTDOWN_TIMEOUT_MILLIS));
      } finally {
        synchronized (ThriftServerLifeCycle.this) {
          ThriftServerLifeCycle.this.notifyAll();
        }
      }
    }, "Thrift Server");
    serverThread.setDaemon(false);
    serverThread.start();
    waitUntilServing();
  }

  /**
   * Blocks current thread until server is serving. Returns instantly if the
   * server was shutdown previously
   * 
   * @return - wether the thrift server started serving or not. Will return
   *         false if a shutdown occured.
   * @throws InterruptedException
   *           - can happen while waiting
   */
  public synchronized boolean waitUntilServing() throws InterruptedException {
    int startTimeout = config.getInt(STARTUP_TIMEOUT_SECONDS, DEFAULT_STARTUP_TIMEOUT_SECONDS);
    while (!shutdown && (thriftServer == null || !thriftServer.isServing())) {
      wait(startTimeout);
    }
    return !shutdown;
  }

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
    long start = System.currentTimeMillis();
    long remaining = start + timeoutMillis - System.currentTimeMillis();
    while (pendingSessions.size() > 0 && remaining > 0) {
      LOG.info("Waiting for " + pendingSessions.size() + " pending requests");
      try {
        synchronized (pendingSessions) {
          pendingSessions.wait(remaining);
        }
        remaining = start + timeoutMillis - System.currentTimeMillis();
      } catch (InterruptedException e) {
        LOG.warn("Interrupted while waiting for pending requests", e);
      }
    }
    LOG.info("Done waiting for pending requests (" + pendingSessions.size() + " remaining)");
  }

  private void boot(TProcessor processor) throws NumberFormatException, TTransportException, InterruptedException, ConfigNotFoundException {
    int port = config.getMandatoryInt(PORT);
    int selectors = config.getInt(SELECTOR_THREADS, DEFAULT_SELECTOR_THREADS);
    int workers = config.getInt(WORKER_THREADS, DEFAULT_WORKER_THREADS);
    fireServer(port, selectors, workers, processor, config.getString(PROTOCOL, "binary"));
  }

  private <PROCESSOR extends TProcessor> void fireServer(int port, int selectorThreads, int workerThreads, PROCESSOR processor, String protocol) throws TTransportException, InterruptedException {
    TNonblockingServerTransport transport = new TNonblockingServerSocket(port);
    TThreadedSelectorServer.Args args = new TThreadedSelectorServer.Args(transport);
    args.transportFactory(new TFramedTransport.Factory());
    if ("binary".equalsIgnoreCase(protocol)) {
      args.protocolFactory(new TBinaryProtocol.Factory());
    } else if ("compact".equalsIgnoreCase(protocol)) {
      args.protocolFactory(new TCompactProtocol.Factory());
    } else if ("json".equalsIgnoreCase(protocol)) {
      args.protocolFactory(new TJSONProtocol.Factory());
    } else {
      throw new IllegalArgumentException("Thrift protocol not supported: " + protocol);
    }

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
        LOG.debug("Received new session");
        pendingSessions.put(serverContext, System.currentTimeMillis());
        LOG.debug("Current sessions: " + pendingSessions.size());
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
        pendingSessions.remove(serverContext);
        LOG.debug("Pending session dispached");
        LOG.debug("Current sessions: " + pendingSessions.size());
        if (shutdown) {
          synchronized (pendingSessions) {
            pendingSessions.notifyAll();
          }
        }
      }

    });
    thriftServer.serve();
  }

}
