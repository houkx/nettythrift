# nettythrift
A Java Server IO framework use netty and thrift.

# Server Example

     public void startServer() {
      // different from nify, This Server:
      // support TCP/HTTP/WebSocket At Same time.
      // support sync/async (notFrame or Frame) At Same time.
      // support TBinaryProtocol/TCompactProtocol/TJSONProtocol/TSimpleJSONProtocol At Same time.
      
      int port = 8081; // The port to bind.
      ExecutorService threadPoolExecutor = ... // business Executor
      
      // Create the handler, the interface impl
      MyService.Iface serviceInterface = new MyServiceHandler();

      // Create the processor, you no need give a TProtocolFactory here,the protocol is dynamic.
      TBaseProcessor processor = new MyService.Processor<>(serviceInterface);
    
      ThriftServerDef serverDef = ThriftServerDef.newBuilder().listen(port)//
				.withProcessor(processor)//
				.using(threadPoolExecutor)//
				.clientIdleTimeout(TimeUnit.SECONDS.toMillis(60))//
				.build();
       ServerBootstrap server = new ServerBootstrap(serverDef);
       server.start();// Start Server
    }
 

# The Client
  Client Example:
    see the TestCase in project: io.nettythrift
    or the project client.* ;
    
  the project "client.json" is A Client use Http and TSimpleJSONProtocol.  
  
  there is a also a Simple Connection Pool in project: client.framedCommpact
