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
import ai.safekids.httpproxy.handler.protocol.ProtocolDetector;
import ai.safekids.httpproxy.handler.protocol.http1.Http1ProtocolDetector;
import ai.safekids.httpproxy.listener.NitmProxyListenerProvider;
import ai.safekids.httpproxy.listener.NitmProxyListenerStore;
import ai.safekids.httpproxy.tls.CertUtil;
import ai.safekids.httpproxy.tls.UnsafeAccessSupport;
import com.google.common.base.Joiner;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import java.security.Provider;
import java.util.Collections;
import java.util.List;

import static java.lang.String.*;
import static java.lang.System.*;
import static java.util.Arrays.*;

public class NitmProxyConfig {

    private static final String DEFAULT_CERT = "server.pem";
    private static final String DEFAULT_KEY = "key.pem";

    private ProxyMode proxyMode;

    private String host;
    private int port;

    // TLS related
    private X509CertificateHolder certificate;
    private PrivateKeyInfo key;
    private boolean insecure;
    private Provider sslProvider;
    private List<String> tlsProtocols;
    private KeyManagerFactory clientKeyManagerFactory;

    private int maxContentLength;

    private NitmProxyStatusListener statusListener;
    private NitmProxyListenerStore listenerStore;

    private TrustManager trustManager;
    private UnsafeAccessSupport unsafeAccessSupport = UnsafeAccessSupport.DENY;

    private List<ProtocolDetector> detectors;

    // Default values
    public NitmProxyConfig() {
        proxyMode = ProxyMode.HTTP;

        host = "127.0.0.1";
        port = 8080;

        insecure = false;
        tlsProtocols = asList("TLSv1.3", "TLSv1.2");

        maxContentLength = 50 * 1024 * 1024;

        listenerStore = new NitmProxyListenerStore();
        detectors = Collections.singletonList(Http1ProtocolDetector.INSTANCE);
    }

    public void init() {
        if (certificate == null) {
            certificate = CertUtil.readPemFromFile(DEFAULT_CERT);
        }
        if (key == null) {
            key = CertUtil.readPrivateKeyFromFile(DEFAULT_KEY);
        }
    }

    public ProxyMode getProxyMode() {
        return proxyMode;
    }

    public void setProxyMode(ProxyMode proxyMode) {
        this.proxyMode = proxyMode;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public X509CertificateHolder getCertificate() {
        return certificate;
    }

    public void setCertificate(X509CertificateHolder certificate) {
        this.certificate = certificate;
    }

    public PrivateKeyInfo getKey() {
        return key;
    }

    public void setKey(PrivateKeyInfo key) {
        this.key = key;
    }

    public boolean isInsecure() {
        return insecure;
    }

    public void setInsecure(boolean insecure) {
        this.insecure = insecure;
    }

    public Provider getSslProvider() {
        return sslProvider;
    }

    public void setSslProvider(Provider sslProvider) {
        this.sslProvider = sslProvider;
    }

    public List<String> getTlsProtocols() {
        return tlsProtocols;
    }

    public void setTlsProtocols(List<String> tlsProtocols) {
        this.tlsProtocols = tlsProtocols;
    }

    public KeyManagerFactory getClientKeyManagerFactory() {
        return clientKeyManagerFactory;
    }

    public void setClientKeyManagerFactory(KeyManagerFactory clientKeyManagerFactory) {
        this.clientKeyManagerFactory = clientKeyManagerFactory;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public NitmProxyStatusListener getStatusListener() {
        return statusListener;
    }

    public void setStatusListener(NitmProxyStatusListener statusListener) {
        this.statusListener = statusListener;
    }

    public NitmProxyListenerStore getListenerStore() {
        return listenerStore;
    }

    @Deprecated
    public List<NitmProxyListenerProvider> getListeners() {
        return listenerStore.getListeners();
    }

    @Deprecated
    public void setListeners(List<NitmProxyListenerProvider> listeners) {
        listenerStore = new NitmProxyListenerStore(listeners);
    }

    public UnsafeAccessSupport getUnsafeAccessSupport() {
        return unsafeAccessSupport;
    }

    public void setUnsafeAccessSupport(UnsafeAccessSupport unsafeAccessSupport) {
        this.unsafeAccessSupport = unsafeAccessSupport;
    }

    public List<ProtocolDetector> getDetectors() {
        return detectors;
    }

    public void setDetectors(List<ProtocolDetector> detectors) {
        this.detectors = detectors;
    }

    @Override
    public String toString() {
        String serverCert = (certificate != null)? certificate.getIssuer().toString() : "null";
        String serverKey = (key != null)? key.getPrivateKeyAlgorithm().getAlgorithm().getId() : null;
        List<String> properties = asList(
            format("proxyMode=%s", proxyMode),
            format("host=%s", host),
            format("port=%s", port),
            format("cert=%s", serverCert),
            format("key=%s", serverKey),
            format("insecure=%b", insecure),
            format("tlsProtocols=%s", tlsProtocols),
            format("sslProvider=%s", sslProvider),
            format("keyManagerFactory=%b", clientKeyManagerFactory),
            format("maxContentLength=%d", maxContentLength));
        return format("NitmProxyConfig%n%s", Joiner.on(lineSeparator()).join(properties));
    }
}