Basic Commands

```
cert.cmd (will create the server certificates)
run.cmd (will run the proxy with default settings)
debug.cmd (will run it in debug mode with SSL decryption)

or directly with gradle

gradlew run --args <OPTIONS>
gradlew debug
gradlew cert
```

```
ANALYZE PACKETS USING WIRESHARK
===============================
Capture Browser Traffic
 - Set an environment variable:
 - SSLKEYLOGFILE = <PATH>\http-proxy-java\logs\sslbrowser.txt

Capture Netty Traffic to the server (Ethernet Outbound)
 - tshark -w logs\nlog.pcap -i "Ethernet 6" -o "tls.keylog_file:logs/sslkeys.log"
 - wireshark -r logs\nlog.pcap

 OR
 
 debug/shark.cmd (to capture)
 debug/view-shark.cmd (to view)

Configure Wireshark to use the file Edit -> Preferences -> Protocol -> TLS -> Master Secret Log File Name
and point to either sslbrowser.log or sslnitm.log to do full decryption.
```

```
Basic Principles:
1. Channel is essentially a socket. Similar to InputStream, InBoundHandler only handle read events. 
Similarly, OutoundHandlers OutputStream 
2. The flow goes from a -> b -> c in channel handler pipeline where each read fires of the next channel handler. 
For writes, it's the other way around. c then b and then a.


Nitm Proxy
1. Browser connects to NITM. That is handled by Front End. 
2. The real requests is made by the back end. 

Let's take a look. 

-->TlsFrontEndHandler 
is the first handler to execute. It detects SSL, find the host using SNI and then
finds the right protocol using ALPN handler. By the time it is done, HTTP1 or HTTP2 front end 
handlers would have been assigned.

--> HTTP1 front end handler would get a HTTP request. It would then connect to the backend using:
connectionContext.connect which then added tlsBackendHandler, and tailBackendHandler to the pipeline

At the inception, the sequence of pipelines are as following

1) TlsFrontendHandler
   TailFrontEndHandler 

2) TlsFrontendHandler -> DelectSSL, SNI and ALPN handlers. 
   TailFrontendHandler
   
3) HTTP1FrontEndHandler/TailFrontEndHandler OR
   HTTP2FrontEndHandler/TailFrontEndHandler
   
4) HTTP1 Intermediate (HTTPServerCodec/ObjectAggregator will capture the FullHttpRequest
   CLIENT CHANNEL
   - HttpServerCodec
   - HttpObjectAggregator
   - HTTP1EventHandler
   - **HTTP1FrontEndHandler** <-- this is the main handler
   - WebSocketFrontEndHandler (only if websocket)
   - WebSocketServerCompressionHandler (only if websocket)
   - WebSocketEventHandler (only if websocket)
   - TailFrontEndHandler

5) Upon connection where a FullHTTPRequest is captured, the pipeline will look as follows:
   CLIENT CHANNEL
   - HttpServerCodec
   - HttpObjectAggregator
   - HTTP1EventHandler
   - **HTTP1FrontEndHandler** <-- this is the main handler
   - WebSocketFrontEndHandler (only if websocket)
   - WebSocketServerCompressionHandler (only if websocket)
   - WebSocketEventHandler (only if websocket)
   - TailFrontEndHandler
   
   SERVER CHANNEL
   - TlsBackendHandler
   - TailBackendHandler

6) Upon server connection, the pipeline is updated as follows:
   CLIENT CHANNEL (read event such as HTTP request goes top down)
   - HttpServerCodec
   - HttpObjectAggregator
   - HTTP1EventHandler
   - **HTTP1FrontEndHandler** <-- this is the main handler
   - WebSocketFrontEndHandler (only if websocket)
   - WebSocketServerCompressionHandler (only if websocket)
   - WebSocketEventHandler (only if websocket)
   - TailFrontEndHandler --> Writes to SERVER CHANNEL DIRECTLY

   SERVER CHANNEL (HTTP response flows does as a read event)
   - HttpClientCodec
   - Http1BackendHandler
   - WebSocketSBackendHandler
   - WebSocketClientCompressionHandler
   - TailBackendHandler --> Write directly to CLIENT CHANNEL 
   
7) For web sockets, the handlers are replaced with the following after a websocket upgrade:

   CLIENT CHANNEL
   - WebSocket13FrameEncoder
   - WebSocket13FrameDecoder
   - WebSocketFrontEndHandler
   - WebSocketServerCompressionHandler
   - WebSocketEventHandler
   - TailFrontEndHandler --> Writes to SERVER CHANNEL DIRECTLY
   

   SERVER CHANNEL
   - WebSocket13FrameEncoder
   - WebSocket13FrameDecoder
   - WebSocketSBackendHandler
   - WebSocketClientCompressionHandler
   - TailBackendHandler --> Writes (Text or Binary frame) directly to CLIENT CHANNEL 
    
   Any request received from the browser will go through the folloiwng:
   
   BROWSER Websocket Frame:   
   -------------------------
   CLIENT CHANNEL:
   WebSocket13FrameDecoder
   TailFrontEndHandler -> this will write to server channel (backend)
   
   SERVER CHANNEL:
   WebSocket08FrameEncoder (this will encode the frame with masking since client-> server requires it)
   TailBackendHandler (sends the final encoded frame to the server)
   
   Please note that at any point the client masking is not done, the server will close the connection.
   
   
IMPORTANT:      
Each channel has it's own pipeline. When a writeAndFlish is called from the Tail Front/Backend, it runs
through the channel pipeline in their "write" methods. The order is backwards from the read pipeline. 
 
 
 WebSocker Frame Format
 
  0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+

|Opcode  | Meaning                             | Reference |
 -+--------+-------------------------------------+-----------|
  | 0      | Continuation Frame                  | RFC XXXX  |
 -+--------+-------------------------------------+-----------|
  | 1      | Text Frame                          | RFC XXXX  |
 -+--------+-------------------------------------+-----------|
  | 2      | Binary Frame                        | RFC XXXX  |
 -+--------+-------------------------------------+-----------|
  | 8      | Connection Close Frame              | RFC XXXX  |
 -+--------+-------------------------------------+-----------|
  | 9      | Ping Frame                          | RFC XXXX  |
 -+--------+-------------------------------------+-----------|
  | 10     | Pong Frame                          | RFC XXXX  |
 -+--------+-------------------------------------+-----------|
```