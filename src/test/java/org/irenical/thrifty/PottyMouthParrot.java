package org.irenical.thrifty;

import org.apache.thrift.TException;

public class PottyMouthParrot implements Parrot.Iface {

  @Override
  public String hi() throws TException {
    return "Poopsies";
  }

}
