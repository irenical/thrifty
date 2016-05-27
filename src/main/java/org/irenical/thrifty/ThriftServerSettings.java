package org.irenical.thrifty;

public interface ThriftServerSettings {

  String PORT = "thrift.listenPort";

  String SELECTOR_THREADS = "thrift.selectorThreads";

  String WORKER_THREADS = "thrift.workerThreads";

  String SHUTDOWN_TIMEOUT_MILLIS = "thrift.shutdownTimeoutMillis";

  String STARTUP_TIMEOUT_SECONDS = "thrift.startupTimeoutSeconds";
  
  String PROTOCOL = "thrift.protocol";
  
}
