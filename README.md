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
