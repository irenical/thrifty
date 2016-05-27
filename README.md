[![][maven img]][maven]
[![][travis img]][travis]
[![][codecov img]][codecov]
[![][codacy img]][codacy]

# Thrifty
Thrifty is a general-purpose implementation of a Thrift server, with boot and shutdown logic.

## Usage
Thrifty uses [jindy](https://github.com/irenical/jindy) for configuration, so make sure you have a jindy binding on your classpath.  
In your code (possibly in your main class), you can instantiate a thrift server through it's lifecycle interface.

```java
TProcessor myProcessor = new MyThriftStub.Processor<MyThriftStub.Iface>(myThriftImplementation);
ThriftServerLifeCycle thrift = new ThriftServerLifeCycle(myProcessor, ConfigFactory.getConfig());
thrift.start();
```
Although a new thread will be created to run the server, for convenience the .start() method blocks until the server is ready to receive requests. 
Calling .stop() will prevent new requests from being accepted, then wait for the pending ones to finish and only then stop the server.

Thrifty will look for the following properties, using these defaults.
```properties
thrift.listenPort=1337 #Mandatory, no default value
thrift.selectorThreads=2
thrift.workerThreads=4
thrift.shutdownTimeoutMillis=10000
thrift.startupTimeoutSeconds=180
thrift.protocol=binary
```
Thrifty uses TNonblockingServerTransport and TThreadedSelectorServer

[maven]:http://search.maven.org/#search|gav|1|g:"org.irenical.thrifty"%20AND%20a:"thrifty"
[maven img]:https://maven-badges.herokuapp.com/maven-central/org.irenical.thrifty/thrifty/badge.svg

[travis]:https://travis-ci.org/irenical/thrifty
[travis img]:https://travis-ci.org/irenical/thrifty.svg?branch=master

[codecov]:https://codecov.io/gh/irenical/thrifty
[codecov img]:https://codecov.io/gh/irenical/thrifty/branch/master/graph/badge.svg

[codacy]:https://www.codacy.com/app/tiagosimao/thrifty?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=irenical/thrifty&amp;utm_campaign=Badge_Grade
[codacy img]:https://api.codacy.com/project/badge/Grade/cd28e99030eb4dd7a9489f35dcfc6be5
