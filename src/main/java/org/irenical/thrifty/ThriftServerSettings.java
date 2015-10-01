package org.irenical.thrifty;

public interface ThriftServerSettings {
    
    String PORT = "thrift.listenPort";

    String SELECTOR_THREADS = "thrift.selectorThreads";

    String WORKER_THREADS = "thrift.workerThreads";
    
    String CLIENT_TIMEOUT = "thrift.clientTimeout";
    
    String SHUTDOWN_TIMEOUT_MILLIS = "thrift.shutdownTimeoutMillis";

}
