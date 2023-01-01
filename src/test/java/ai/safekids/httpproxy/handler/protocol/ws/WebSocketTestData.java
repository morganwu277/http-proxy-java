package ai.safekids.httpproxy.handler.protocol.ws;

public class WebSocketTestData {
    public String REQUEST_COMPRESSED = "GET /ws HTTP/1.1\n" +
                  "Host: dev-ws.infinitelogic.lan\n" +
                  "Connection: Upgrade\n" +
                  "Pragma: no-cache\n" +
                  "Cache-Control: no-cache\n" +
                  "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36\n" +
                  "Upgrade: websocket\n" +
                  "Origin: http://dev-ws.infinitelogic.lan\n" +
                  "Sec-WebSocket-Version: 13\n" +
                  "Accept-Encoding: gzip, deflate\n" +
                  "Accept-Language: en-US,en;q=0.9\n" +
                  "Sec-WebSocket-Key: TKx7Ssj7GDcYPj63k3KbXw==\n" +
                  "Sec-WebSocket-Extensions: permessage-deflate; client_max_window_bits\n\n";

    public String RESPONSE_COMPRESSED = "HTTP/1.1 101 Switching Protocols\n" +
                  "Date: Tue, 20 Dec 2022 20:58:44 GMT\n" +
                  "Sec-WebSocket-Extensions: permessage-deflate\n" +
                  "Connection: Upgrade\n" +
                  "Sec-WebSocket-Accept: joJrwgz3umgNT5guT+DClE7CVXM=\n" +
                  "Server: Jetty(9.4.49.v20220914)\n" +
                  "Upgrade: WebSocket\n" +
                  "X-Content-Type-Options: nosniff\n" +
                  "X-XSS-Protection: 1; mode=block\n" +
                  "Cache-Control: no-cache, no-store, max-age=0, must-revalidate\n" +
                  "Pragma: no-cache\n" +
                  "Expires: 0\n" +
                  "X-Frame-Options: DENY from server\n\n";
}