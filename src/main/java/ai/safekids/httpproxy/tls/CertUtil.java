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

package ai.safekids.httpproxy.tls;

import ai.safekids.httpproxy.exception.NitmProxyException;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.google.common.io.Files.*;
import static java.nio.charset.StandardCharsets.*;

public class CertUtil {

    private static final Provider PROVIDER = new BouncyCastleProvider();

    private CertUtil() {
    }

    @SuppressWarnings("deprecation")
    public static Certificate newCert(X509CertificateHolder parent, PrivateKeyInfo key, String host) {
        try {
            //need a date before today to adjust for other time zones
            Date before = Date.from(
                    Instant.now()
                    .atZone(ZoneId.systemDefault())
                    .minusMonths(6).toInstant());
            Date after = Date.from(
                    Year.now()
                        .plus(1, ChronoUnit.YEARS)
                        .atDay(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant());

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(PROVIDER);
            PublicKey publicKey = converter.getPublicKey(parent.getSubjectPublicKeyInfo());
            PrivateKey privateKey = converter.getPrivateKey(key);
            X509v3CertificateBuilder x509 = new JcaX509v3CertificateBuilder(
                    parent.getSubject(),
                    new BigInteger(64, new SecureRandom()),
                    before,
                    after,
                    new X500Name("CN=" + host),
                    publicKey);
            GeneralNames generalNames = GeneralNames.getInstance(
                    new DERSequence(new GeneralName(GeneralName.dNSName, host)));
            x509.addExtension(Extension.subjectAlternativeName, true, generalNames);

            //add extended key usage needed for newer Mac OS requirements
            x509.addExtension(
                    Extension.extendedKeyUsage,
                    true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                    .build(privateKey);

            JcaX509CertificateConverter x509CertificateConverter = new JcaX509CertificateConverter()
                    .setProvider(PROVIDER);

            return new Certificate(
                    new KeyPair(publicKey, privateKey),
                    x509CertificateConverter.getCertificate(x509.build(signer)),
                    x509CertificateConverter.getCertificate(parent));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Read pem from file.
     *
     * @param pemFile the pem file
     * @param <T> the type of the pem
     * @return the parsed pem
     * @throws NitmProxyException if there is any exception while reading the pem file
     */
    @SuppressWarnings({ "UnstableApiUsage", "unchecked" })
    public static <T> T readPemFromFile(String pemFile) {
        try (PEMParser pemParser = new PEMParser(newReader(new File(pemFile), US_ASCII))) {
            return (T) pemParser.readObject();
        } catch (IOException e) {
            throw new NitmProxyException("Parse pem failed: " + pemFile, e);
        }
    }

    @SuppressWarnings("deprecation")
    public static byte[] toPem(Object object) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(outputStream))) {
            writer.writeObject(object);
            writer.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new NitmProxyException("Transform to pem failed: " + object, e);
        }
    }

    @SuppressWarnings("deprecation")
    public static byte[] toPem(Object... objects) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(outputStream))) {
            for (Object object : objects) {
                writer.writeObject(object);
            }
            writer.flush();
            return outputStream.toByteArray();
        }
    }

    @SuppressWarnings("deprecation")
    public static PrivateKeyInfo readPrivateKeyFromFile(String pemFile) {
        Object object = readPemFromFile(pemFile);
        if (object instanceof PEMKeyPair) {
            return ((PEMKeyPair) object).getPrivateKeyInfo();
        } else if (object instanceof PrivateKeyInfo) {
            return (PrivateKeyInfo) object;
        }
        throw new NitmProxyException("Not a valid private key: " + pemFile);
    }

    /**
     * Generate a key pair for private and public key generation
     *
     * @param keySize
     * @return
     * @throws NoSuchAlgorithmException
     */
    @SuppressWarnings("deprecation")
    public static KeyPair generateKeyPair(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(keySize, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Generate a server certificate
     *
     *
     * @param subject
     * @param beforeDate
     * @param notAfterDate
     * @param keyPair
     * @return
     * @throws CertIOException
     * @throws OperatorCreationException
     * @throws CertificateException
     */
    @SuppressWarnings("deprecation")
    public static X509Certificate generateCertificate(String subject, Date beforeDate, Date notAfterDate,
                                                      KeyPair keyPair)
            throws CertIOException, OperatorCreationException, CertificateException {

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(new X500Name(subject),
                new BigInteger(Long.toString(System.currentTimeMillis() / 1000)),
                beforeDate,
                notAfterDate,
                new X500Name(subject),
                keyPair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }

    /**
     * Used as a helper for creating server certificates which do not depend on openssl being installed. It
     * does overwrite existing certificates if they are present.
     *
     * @param caCertFile
     * @param caPrivateFile
     * @param keySize
     * @throws Exception
     */
    @SuppressWarnings("deprecation")
    public static void createCACertificates(File caCertFile, File caPrivateFile, String subject, int keySize)
            throws Exception {

        KeyPair keyPair = generateKeyPair(keySize);
        if (caCertFile.exists()) {
            caCertFile.delete();
        }

        //need a date before today to adjust for other time zones
        Date before = Date.from(
                Instant.now()
                        .atZone(ZoneId.systemDefault())
                        .minusMonths(6).toInstant());

        Date after = Date.from(
                Year.now()
                        .plus(1, ChronoUnit.YEARS)
                        .atDay(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant());

        Files.write(Paths.get(caCertFile.toURI()),
                toPem(generateCertificate(
                        subject,
                        before,
                        after,
                        keyPair)));

        if (caPrivateFile.exists()) {
            caPrivateFile.delete();
        }

        Files.write(caPrivateFile.toPath(),
                toPem(keyPair.getPrivate()));
    }
}
