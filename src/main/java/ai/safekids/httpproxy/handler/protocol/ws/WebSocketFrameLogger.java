package ai.safekids.httpproxy.handler.protocol.ws;

import ai.safekids.httpproxy.ConnectionContext;
import ai.safekids.httpproxy.enums.ProxyMode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketFrameLogger extends ChannelDuplexHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketFrameLogger.class);

    private ConnectionContext connectionContext;
    private String name;

    public WebSocketFrameLogger(String name, ConnectionContext connectionContext) {
        this.connectionContext = connectionContext;
        this.name = name;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        logObject(ctx, "READ", msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        logObject(ctx, "WRITE", msg);
        ctx.write(msg, promise);
    }

    void logObject(ChannelHandlerContext ctx, String readOrWrite, Object msg) {
        ProxyMode proxyMode = (connectionContext != null &&
                               connectionContext.master() != null &&
                               connectionContext.master().config() != null)?
            connectionContext.master().config().getProxyMode() : ProxyMode.HTTP;

        if (msg instanceof WebSocketFrame) {
            LOGGER.trace("{} : {} {} WebSocket frame \n {}", connectionContext, name, readOrWrite, log(ctx, msg));
            return;
        }

        if (ProxyMode.TRANSPARENT.equals(proxyMode) &&
            msg instanceof ByteBuf) {
            LOGGER.trace("{} : {} {} raw ByteBuf frame data. \n {}", connectionContext, name, readOrWrite,
                         log(ctx, msg));
            return;
        }

        if (msg instanceof HttpObject) {
            LOGGER.debug("{} : {} {} {} http data received", connectionContext, name, readOrWrite,
                         msg.getClass().getSimpleName());
            return;
        }

        String content = getContentData(msg);
        LOGGER.warn("{} : {} {} UNKNOWN \n {}", connectionContext,
                    name,
                    readOrWrite,
                    log(ctx, msg));
    }

    String log(ChannelHandlerContext ctx, Object obj) {
        return "\n" + getContentData(obj);
    }

    String getContentData(Object msg) {
        String content = "";
        if (!(msg instanceof WebSocketFrame)) {
            if (msg instanceof ByteBuf || msg instanceof ByteBufHolder) {
                ByteBuf buf = (msg instanceof ByteBuf)? (ByteBuf) msg : ((ByteBufHolder) msg).content();
                content = msg.getClass().getName() + "\n" + ByteBufUtil.prettyHexDump(buf);
            } else {
                content = "not ByteBuf but " + msg.getClass().getSimpleName();
            }
            return content;
        }
        return msg.getClass().getName() + " content:\n" + ByteBufUtil.prettyHexDump(((WebSocketFrame) msg).content());
    }

    String getPipelines(ChannelHandlerContext ctx) {
        StringBuilder res = new StringBuilder("pipelines:\n");
        ctx.pipeline().names().forEach(n -> res.append("[" + n + "],"));
        return res.toString();
    }
}
