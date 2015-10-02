# thrifty
Thrift server boilerplate

## What is it?
Thrifty is a general-purpose implementation of a Thrift server, with boot and shutdown logic done for you.

## Where is it?
At maven central
```maven
<dependency>
  <groupId>org.irenical.thrifty</groupId>
  <artifactId>thrifty</artifactId>
  <version>1.0.0</version>
</dependency>
```

## How to use
Thrifty deppends on https://github.com/irenical/jindy, so make sure you have a jindy binding on your classpath.
In your code (possibly in your main class), you can instantiate a thrift server lifecycle with your thrift processor, a jindy Config instance (optional) and a property prefix (optional)
Then press start!

```java
  ThriftServerLifeCycle thrift = new ThriftServerLifeCycle(new MyThriftStub.Processor<MyThriftStub.Iface>(myThriftImplementation), ConfigFactory.getConfig(), "myappname");
  thrift.start();
```

.start() is non-blocking, as a new thread will be fired when you call the method.
Calling .stop() will first stop accepting new requests, then wait for the pending ones to finish and only then kill the server.

The following properties will be read by ThriftServerLifeCycle
```properties
myappname.thrift.listenPort=1337
myappname.thrift.selectorThreads=2
myappname.thrift.workerThreads=4
myappname.thrift.shutdownTimeoutMillis=10000
```

Thrifty uses TNonblockingServerTransport and TThreadedSelectorServer
