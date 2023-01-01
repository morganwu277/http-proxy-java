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

import ai.safekids.httpproxy.tls.CertUtil;
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
import java.nio.file.Files;
import java.nio.file.Paths;

public final class CertGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertGenerator.class);

    private static final String DEFAULT_SUBJECT = "C=US, ST=VA, L=Vienna, O=Nitm, OU=Nitm, CN=Nitm CA Root";
    private static final int DEFAULT_KEYSIZE = 2048;

    private CertGenerator() {
    }

    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();

        Options options = new Options();

        options.addOption(
                Option.builder("s")
                      .longOpt("subject")
                      .hasArg()
                      .argName("SUBJECT")
                      .desc("subject of certificate, default: " + DEFAULT_SUBJECT)
                      .build());
        options.addOption(
                Option.builder("k")
                        .longOpt("keysize")
                        .hasArg()
                        .argName("KEYSIZE")
                        .desc("key size of certificate, default: 2048")
                        .build());

        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("certgenerator", options, true);
            System.exit(-1);
        }

        CertGeneratorConfig config = parse(commandLine);
        LOGGER.info("Generating certificate with subject:{} and keysize:{}",
                config.getSubject(), config.getKeySize());

        File serverPem = new File("server.pem");
        File keyPem = new File("key.pem");

        CertUtil.createCACertificates(serverPem, keyPem, config.getSubject(), config.getKeySize());

        //we'll copy server.pem to server.crt for easy import
        Files.copy(Paths.get(serverPem.toURI()), Paths.get("server.crt"));
    }

    private static CertGeneratorConfig parse(CommandLine commandLine) {
        CertGeneratorConfig config = new CertGeneratorConfig();
        if (commandLine.hasOption("s")) {
            config.setSubject(commandLine.getOptionValue("s"));
        }
        if (commandLine.hasOption("k")) {
            try {
                config.setKeySize(Integer.parseInt(commandLine.getOptionValue("k")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a valid key size: " + commandLine.getOptionValue("k"));
            }
        }
        return config;
    }

    private static class CertGeneratorConfig {
        String subject = DEFAULT_SUBJECT;
        int keySize = DEFAULT_KEYSIZE;

        public int getKeySize() {
            return keySize;
        }

        public void setKeySize(int keySize) {
            this.keySize = keySize;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }
    }
}