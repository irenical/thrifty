package org.irenical.thrifty;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.irenical.jindy.Config;
import org.irenical.jindy.ConfigFactory;
import org.irenical.jindy.ConfigNotFoundException;
import org.junit.Assert;
import org.junit.Test;

public class ThriftyTesty {

  @Test
  public void testFramed() throws InterruptedException, ConfigNotFoundException, TException{
    Config config = ConfigFactory.getConfig();
    Parrot.Iface parrot = new PottyMouthParrot();
    ThriftServerLifeCycle thriftServer = new ThriftServerLifeCycle(new Parrot.Processor<Parrot.Iface>(parrot), config);
    thriftServer.start();
    
    TTransport transport = new TSocket("localhost", config.getMandatoryInt(ThriftServerSettings.PORT));
    transport = new TFramedTransport(transport);
    TProtocol protocol = new TBinaryProtocol(transport);
    Parrot.Client client = new Parrot.Client(protocol);
    transport.open();
    
    String got = client.hi();
    
    Assert.assertEquals(got, "Poopsies");
    
    transport.close();
    thriftServer.stop();
    
  }

}
