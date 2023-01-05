/*
 * Copyright 2021 - Safe Kids LLC
 *
 * The complete license agreement is available at https://safekids.ai/eula
 *
 *
 * LICENSE GRANT
 * ==============================================
 * Licensor hereby grants to you a non-exclusive and non-transferable license to use the Software and
 * related documentation (the "Documentation") solely for the intended purposes of the Software as set forth in the
 * Documentation, according to the provisions contained herein and subject to payment of applicable license fees.
 * You are not permitted to lease, rent, distribute or sublicense the Software or any rights therein.
 * You also may not install the Software on a network server, use the Software in a time-sharing arrangement or
 * in any other unauthorized manner. Further, no license is granted to you in the human readable code of the Software
 *  (source code). Except as provided below, this Agreement does not grant you any rights to patents, copyrights,
 *  trade secrets, trademarks, or any other rights in the Software and Documentation.
 *
 * NO MODIFICATION, NO REVERSE ENGINEERING
 * ===============================================
 * You agree not to, without the prior written permission of Licensor: (i); disassemble, decompile or "unlock",
 * decode or otherwise reverse translate or engineer, or attempt in any manner to reconstruct or discover any source
 * code or underlying algorithms of the Software, if provided in object code form only; (ii) use, copy,
 * modify, translate,reverse engineer, decompile, disassemble, or create derivative works of the Software and
 * any accompanying documents, or assist someone in performing such prohibited acts; or (iii) transfer, rent,
 * lease, or sub license the Software.
 *
 * NO WARRANTIES.
 * ===============================================
 * LICENSOR MAKES NO WARRANTIES, EXPRESS OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT OF THIRD PARTIES' RIGHTS.
 * THE SOFTWARE (INCLUDING SOURCE CODE) IS PROVIDED TO YOU ON AN "AS IS" BASIS. TO THE FULL EXTENT PERMITTED BY LAW,
 * THE DURATION OF STATUTORILY REQUIRED WARRANTIES, IF ANY, SHALL BE LIMITED TO THE ABOVE LIMITED WARRANTY PERIOD.
 * MOREOVER, IN NO EVENT WILL WARRANTIES PROVIDED BY LAW, IF ANY, APPLY UNLESS THEY ARE REQUIRED TO APPLY BY
 * STATUTE NOTWITHSTANDING THEIR EXCLUSION BY CONTRACT. NO DEALER, AGENT, OR EMPLOYEE OF LICENSOR IS AUTHORIZED TO
 * MAKE ANY MODIFICATIONS, EXTENSIONS, OR ADDITIONS TO THIS LIMITED WARRANTY. THE ENTIRE RISK ARISING OUT OF USE OR
 * PERFORMANCE OF THE SOFTWARE REMAINS WITH YOU.
 *
 */

package ai.safekids.httpproxy;

import ai.safekids.httpproxy.enums.ProxyMode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.spi.NumberFormatProvider;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static ai.safekids.httpproxy.tls.CertUtil.*;

public class NitmProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(NitmProxy.class);

    private NitmProxyConfig config;

    private int port;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private NitmProxyStatus status = NitmProxyStatus.NOTCONFIGURED;

    final Object lockObject = new Object();

    public NitmProxy(NitmProxyConfig config) {
        this.config = config;
    }

    public void startAsync(Executor executor) {
        config.setStatusListener(new NitmProxyStatusListener() {
            @Override
            public void onStart() {
                synchronized (lockObject) {
                    lockObject.notify();
                }
            }
        });

        synchronized (lockObject) {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            start();
                        } catch (Exception e) {
                            LOGGER.error("Unable to start NitmProxy async", e);
                        }
                    }
                });

                lockObject.wait();

                LOGGER.info("NitmProxy started on port: {}", port);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void start() throws Exception {
        config.init();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(new NitmProxyInitializer(config));

            Channel channel = bootstrap
                .bind(config.getHost(), config.getPort())
                .sync()
                .channel();

            InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
            this.port = socketAddress.getPort();

            LOGGER.info("nitmproxy is listening at {}:{}",
                        config.getHost(), this.port);

            status = NitmProxyStatus.STARTED;

            if (config.getStatusListener() != null) {
                config.getStatusListener().onStart();
            }

            channel.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            status = NitmProxyStatus.STOPPED;
        }
    }

    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        status = NitmProxyStatus.STOPPED;

        if (config.getStatusListener() != null) {
            config.getStatusListener().onStop();
        }
    }

    public NitmProxyStatus getStatus() {
        return status;
    }

    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption(
            Option.builder("m")
                  .longOpt("mode")
                  .hasArg()
                  .argName("MODE")
                  .desc("proxy mode(HTTP, SOCKS, TRANSPARENT), default: HTTP")
                  .build());
        options.addOption(
            Option.builder("h")
                  .longOpt("host")
                  .hasArg()
                  .argName("HOST")
                  .desc("listening host, default: 127.0.0.1")
                  .build());
        options.addOption(
            Option.builder("p")
                  .longOpt("port")
                  .hasArg()
                  .argName("PORT")
                  .desc("listening port, default: 8080")
                  .build());
        options.addOption(
            Option.builder()
                  .longOpt("cert")
                  .hasArg()
                  .argName("CERTIFICATE")
                  .desc("x509 certificate used by server(*.pem), default: server.pem")
                  .build());
        options.addOption(
            Option.builder()
                  .longOpt("key")
                  .hasArg()
                  .argName("KEY")
                  .desc("key used by server(*.pem), default: key.pem")
                  .build());
        options.addOption(
            Option.builder("k")
                  .longOpt("insecure")
                  .hasArg(false)
                  .desc("not verify on server certificate")
                  .build());

        options.addOption(
            Option.builder()
                  .longOpt("tls")
                  .hasArg(true)
                  .argName("TLSPROTOCOLS")
                  .desc("TLSv1.3, TLSv1.2")
                  .build());

        options.addOption(
            Option.builder()
                  .longOpt("maxlength")
                  .hasArg(true)
                  .argName("MAXLENGTH")
                  .desc("1000000")
                  .build());

        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("nitmproxy", options, true);
            System.exit(-1);
        }

        new NitmProxy(parse(commandLine)).start();
    }

    private static NitmProxyConfig parse(CommandLine commandLine) {
        NitmProxyConfig config = new NitmProxyConfig();
        if (commandLine.hasOption("m")) {
            config.setProxyMode(ProxyMode.of(commandLine.getOptionValue("m")));
        }
        if (commandLine.hasOption("h")) {
            config.setHost(commandLine.getOptionValue("h"));
        }
        if (commandLine.hasOption("p")) {
            try {
                config.setPort(Integer.parseInt(commandLine.getOptionValue("p")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a legal port: " + commandLine.getOptionValue("p"));
            }
        }
        if (commandLine.hasOption("cert")) {
            String certFile = commandLine.getOptionValue("cert");
            if (!new File(certFile).exists()) {
                throw new IllegalArgumentException("No cert file found: " + certFile);
            }
            config.setCertificate(readPemFromFile(certFile));
        }
        if (commandLine.hasOption("key")) {
            String certKey = commandLine.getOptionValue("key");
            if (!new File(certKey).exists()) {
                throw new IllegalArgumentException("No key found: " + certKey);
            }
            config.setKey(readPrivateKeyFromFile(certKey));
        }
        if (commandLine.hasOption("tls")) {
            String tlsProtocols = commandLine.getOptionValue("tls");
            List<String> res = Arrays.asList(tlsProtocols.split(","));
            List<String> invalid = res.stream()
                                      .filter(p -> !Arrays.asList("TLSv1.3", "TLSv1.2").contains(p))
                                      .collect(Collectors.toList());
            if (invalid.size() > 0) {
                throw new IllegalArgumentException("Invalid TLS protocols specified " + invalid.get(0));
            }
            config.setTlsProtocols(res);
        }

        if (commandLine.hasOption("maxlength")) {
            String maxLengthStr = commandLine.getOptionValue("maxlength");
            Integer maxLength = Integer.parseInt(maxLengthStr);
            config.setMaxContentLength(maxLength);
        }

        if (commandLine.hasOption("k")) {
            config.setInsecure(true);
        }

        LOGGER.info("{}", config);
        return config;
    }

    public int getPort() {
        return port;
    }
}