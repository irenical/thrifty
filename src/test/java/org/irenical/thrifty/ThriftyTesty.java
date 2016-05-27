package org.irenical.thrifty;

import java.util.function.Function;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.irenical.jindy.Config;
import org.irenical.jindy.ConfigFactory;
import org.irenical.jindy.ConfigNotFoundException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Parallel testing not possible due to server's TCP port takeover
 */
public class ThriftyTesty {

  private static Config config;

  @BeforeClass
  public static void setup() throws ConfigNotFoundException {
    config = ConfigFactory.getConfig();
  }

  @Before
  public void prepare() {
    config.setProperty(ThriftServerSettings.PORT, 12345);
  }

  @After
  public void cleanup() {
    config.clear();
  }

  private static void startCallStop(Parrot.Iface parrot, Function<TTransport, TProtocol> protocolizer) throws InterruptedException, TException, ConfigNotFoundException {
    int port = config.getMandatoryInt(ThriftServerSettings.PORT);
    ThriftServerLifeCycle thriftServer = new ThriftServerLifeCycle(new Parrot.Processor<Parrot.Iface>(parrot), config);
    thriftServer.start();

    TTransport transport = new TSocket("localhost", port);
    transport = new TFramedTransport(transport);
    TProtocol protocol = protocolizer.apply(transport);
    Parrot.Client client = new Parrot.Client(protocol);
    transport.open();

    String got = client.hi();

    Assert.assertEquals(got, "Poopsies");

    transport.close();
    thriftServer.stop();
  }

  @Test
  public void testBinaryToBinary() throws InterruptedException, TException, ConfigNotFoundException {
    startCallStop(new PottyMouthParrot(), t -> new TBinaryProtocol(t));
  }

  @Test(expected = TTransportException.class)
  public void testJsonToBinary() throws InterruptedException, TException, ConfigNotFoundException {
    startCallStop(new PottyMouthParrot(), t -> new TJSONProtocol(t));
  }

  @Test(expected = TTransportException.class)
  public void testCompactToBinary() throws InterruptedException, TException, ConfigNotFoundException {
    startCallStop(new PottyMouthParrot(), t -> new TCompactProtocol(t));
  }

  @Test
  public void testJsonToJSon() throws InterruptedException, TException, ConfigNotFoundException {
    config.setProperty(ThriftServerSettings.PROTOCOL, "json");
    startCallStop(new PottyMouthParrot(), t -> new TJSONProtocol(t));
  }

  @Test
  public void testCompactToCompact() throws InterruptedException, TException, ConfigNotFoundException {
    config.setProperty(ThriftServerSettings.PROTOCOL, "compact");
    startCallStop(new PottyMouthParrot(), t -> new TCompactProtocol(t));
  }

}
