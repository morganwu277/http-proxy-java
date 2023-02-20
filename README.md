## Netty in the Middle

A proxy server based on [netty](https://github.com/netty/netty).

### Features

#### Support Proxy
- HTTP Proxy
- HTTP Proxy (Tunnel)
- Socks Proxy
- Transparent Proxy

#### Support Protocol
- HTTP/1
- HTTP/2
- WebSocket
- TLS

#### Support Functionality
- Display network traffic
- Modify network traffic

## Installation
### Step 1: Generate and Import server certificate into trust store (this will create a server.crt, server.pem and key.pem)
```
gradlew cert
certutil.exe -addstore root server.crt
```

### Step 2: Start proxy. Default port is 8080
```
> ./proxy.cmd (or proxy.sh) --help
usage: proxy [--cert <CERTIFICATE>] [--clientNoHttp2] [-h <HOST>] [-k]
       [--key <KEY>] [-m <MODE>] [-p <PORT>] [--serverNoHttp2]
    --cert <CERTIFICATE>   x509 certificate used by server(*.pem),
                           default: server.pem
 -h,--host <HOST>          listening host, default: 127.0.0.1
 -k,--insecure             not verify on server certificate
    --key <KEY>            key used by server(*.pem), default: key.pem
 -m,--mode <MODE>          proxy mode(HTTP, SOCKS, TRANSPARENT), default: HTTP
 -p,--port <PORT>          listening port, default: 8080
```

### Step 3: Configure platform to use the proxy
<p align="center">
 <img src="https://github.com/safekids-ai/http-proxy-java/blob/main/docs/images/proxy1.jpg" height="280">
 <img src="https://github.com/safekids-ai/http-proxy-java/blob/main/docs/images/proxy2.jpg" height="280">
 <img src="https://github.com/safekids-ai/http-proxy-java/blob/main/docs/images/proxy3.jpg" height="280">
</p>


### How to setup proxy video
https://user-images.githubusercontent.com/22925551/220187454-4c82dbdb-715a-4d50-adf4-c813a03a2f0a.mp4

## Packet Capture (from proxy to the internet)
You can capture traffic received in and out of the browser and also the packets send from the proxy to outbound ethernet adapter. The run proxy in packet capture mode, please execute the proxy in "debug" mode. That is done using the following:

```
debug.cmd
```

Under debug.cmd, all encryption keys are stored in a log files called <b>debug/logs/sslkeys.log </b>. These keys will be required to decrypt SSL traffic in transit. You can use wireshark to observe the traffic using the following:

```
tshark -w logs\nlog.pcap -i "Ethernet 6" 
wireshark -r logs\nlog.pcap -o "tls.keylog_file:logs/sslkeys.log"
```
Please replace your interface "Ethernet 6" with the interface you are using for internet access. Once you're done with capture with tshark, you can use wireshark to view the unencrypted nlog.pcap file.

### How to capture traffic from proxy to ethernet video
https://user-images.githubusercontent.com/22925551/220200031-87fa3a4b-8c0b-40c9-9080-70b84599d421.mp4

## Packet Capture (in and out of browser)
By default, chrome allows all encryption keys to be written to a file for debugging. You have to set the following environment variable:

```
set environment variable:
       SSLKEYLOGFILE = <PATH>\http-proxy-java\logs\sslbrowser.txt
tshark -w logs\nlog.pcap -i "Ethernet 6" 
wireshark -r logs\nlog.pcap -o "tls.keylog_file:logs/sslbrowser.txt"
```

## Development

### Coding Style

We are using same coding style with netty, please follow the instructions from the [netty#Setting up development environment](https://netty.io/wiki/setting-up-development-environment.html) to setup.
 

### WebSocket Protocol
``` 
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
