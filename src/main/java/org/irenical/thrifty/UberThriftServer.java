package org.irenical.thrifty;

import org.apache.thrift.server.TThreadedSelectorServer;

public class UberThriftServer extends TThreadedSelectorServer {

  public UberThriftServer(Args args) {
    super(args);
  }

  public void haltListening() {
    super.stopListening();
  }

}
