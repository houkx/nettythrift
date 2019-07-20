# nettythrift
en: 
A Java Server IO framework use netty and thrift.
You could send thrift json protol Http-GET request with Broswer line google Chrome,
or send compactProtocol thrift data with thrift client.

zh-CN:
  一个 netty 服务端框架, 基于 thrift协议.
  你可以通过chrome浏览器发送thrift json 协议的Http-GET 请求, 同时也可以使用thrift原生的的客户端发送压缩协议的数据.
  
  #h1 项目经过了线上高并发的考验.

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

      //en: Create the processor, you no need give a TProtocolFactory here,the protocol is dynamic, same as the client.
      //zh-CN: 创建处理器, 你不需要指定一个TProtocolFactory, 协议是动态适应客户端的协议.
      // you could sen
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
  zh-CN:
  你可以使用thrift原生客户端, 也可以使用这里的 client.* 项目,客户端项目主要适用于像app端这种追求依赖库尽可能小的场景.
  en:
  you can use the orig thrift client, or this client.* project,  the clients is used in android App
  Client Example:
    see the TestCase in project: io.nettythrift
    or the project client.* ;
    
  the project "client.json" is A Client use Http and TSimpleJSONProtocol.  
  
  there is a also a Simple Connection Pool in project: client.framedCommpact
