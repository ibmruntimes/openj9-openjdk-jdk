/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.test.lib.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.test.lib.security.DiffieHellmanGroup;
import java.util.*;

import java.util.Enumeration;

/**
 * Common library for various security test helper functions.
 */
public final class SecurityUtils {

    /*
     * Key Sizes for various algorithms.
     */
    private enum KeySize{
        RSA(2048),
        DSA(2048),
        Ed25519(256),
        EC(256),
        DH(2048);

        private final int keySize;
        KeySize(int keySize) {
            this.keySize = keySize;
        }

        @Override
        public String toString() {
            return String.valueOf(keySize);
        }
    }

    private final static int DEFAULT_SALTSIZE = 16;

    private static String getCacerts() {
        String sep = File.separator;
        return System.getProperty("java.home") + sep
                + "lib" + sep + "security" + sep + "cacerts";
    }

    /**
     * Returns the cacerts keystore with the configured CA certificates.
     */
    public static KeyStore getCacertsKeyStore() throws Exception {
        File file = new File(getCacerts());
        if (!file.exists()) {
            return null;
        }
        return KeyStore.getInstance(file, (char[])null);
    }

    /**
     * Adds the specified protocols to the jdk.tls.disabledAlgorithms
     * security property
     */
    public static void addToDisabledTlsAlgs(String... protocols) {
        addToDisabledArgs("jdk.tls.disabledAlgorithms", List.of(protocols));
    }

    /**
     * Adds constraints to the specified security property.
     */
    public static void addToDisabledArgs(String prop, List<String> constraints) {
        String value = Security.getProperty(prop);
        value = Stream.concat(Arrays.stream(value.split(",")),
                        constraints.stream())
                .map(String::trim)
                .collect(Collectors.joining(","));
        Security.setProperty(prop, value);
    }

    /**
     * Removes the specified protocols from the jdk.tls.disabledAlgorithms
     * security property.
     */
    public static void removeFromDisabledTlsAlgs(String... protocols) {
        removeFromDisabledAlgs("jdk.tls.disabledAlgorithms",
                               List.<String>of(protocols));
    }

    /**
     * Removes constraints that contain the specified constraint from the
     * specified security property. For example, List.of("SHA1") will remove
     * any constraint containing "SHA1".
     */
    public static void removeFromDisabledAlgs(String prop,
            List<String> constraints) {
        String value = Security.getProperty(prop);
        value = Arrays.stream(value.split(","))
                      .map(s -> s.trim())
                      .filter(s -> constraints.stream()
                          .allMatch(constraint -> !s.contains(constraint)))
                      .collect(Collectors.joining(","));
        Security.setProperty(prop, value);
    }

    /**
     * Removes the specified algorithms from the
     * jdk.xml.dsig.secureValidationPolicy security property. Matches any
     * part of the algorithm URI.
     */
    public static void removeAlgsFromDSigPolicy(String... algs) {
        removeFromDSigPolicy("disallowAlg", List.<String>of(algs));
    }

    /**
     * Returns a salt size for tests
     */
    public static int getTestSaltSize() {
        return DEFAULT_SALTSIZE;
    }

    /**
     * Returns a key size in bits for tests, depending on the specified algorithm
     */
    public static int getTestKeySize(String algo) {
        return switch (algo) {
            case "RSA" -> KeySize.RSA.keySize;
            case "DSA" -> KeySize.DSA.keySize;
            case "Ed25519" -> KeySize.Ed25519.keySize;
            case "EC" -> KeySize.EC.keySize;
            case "DH", "DiffieHellman" -> KeySize.DH.keySize;
            default -> throw new RuntimeException("Test key size not defined for " + algo);
        };
    }

    /**
     * Returns a DH predefined group for tests
     */
    public static DiffieHellmanGroup getTestDHGroup() {
        return getTestDHGroup(2048);
    }

    /**
     * Returns a DH predefined group for tests, depending on the specified prime size
     */
    public static DiffieHellmanGroup getTestDHGroup(int primeSize) {
        return switch(primeSize) {
            case 2048 -> DiffieHellmanGroup.ffdhe2048;
            case 3072 -> DiffieHellmanGroup.ffdhe3072;
            case 4096 -> DiffieHellmanGroup.ffdhe4096;
            default -> throw new RuntimeException("Test DH group not defined for " + primeSize);
        };
    }

    private static void removeFromDSigPolicy(String rule, List<String> algs) {
        String value = Security.getProperty("jdk.xml.dsig.secureValidationPolicy");
        value = Arrays.stream(value.split(","))
                      .filter(v -> !v.contains(rule) ||
                              !anyMatch(v, algs))
                      .collect(Collectors.joining(","));
        Security.setProperty("jdk.xml.dsig.secureValidationPolicy", value);
    }

    private static boolean anyMatch(String value, List<String> algs) {
        for (String alg : algs) {
           if (value.contains(alg)) {
               return true;
           }
        }
        return false;
    }

    public static void inspectTlsBuffer(ByteBuffer buffer) throws IOException {
        if (buffer == null || !buffer.hasRemaining()) {
            return;
        }

        ByteBuffer packet = buffer.slice();
        System.err.printf("---TLS Buffer Inspection. Bytes Remaining: %d---\n",
                          packet.remaining());

        for (int i = 1; packet.position() < packet.limit(); i++) {
            byte contentType = packet.get();                   // pos: 0
            byte majorVersion = packet.get();                  // pos: 1
            byte minorVersion = packet.get();                  // pos: 2
            int contentLen = getInt16(packet);                 // pos: 3, 4

            System.err.printf(
                "Flight %d: contentType: %d; majorVersion: %d; "
                + "minorVersion: %d; contentLen: %d\n", i, (int) contentType,
                (int) majorVersion, (int) minorVersion, contentLen);

            packet.position(packet.position() + contentLen);
        }
    }

    public static int getInt16(ByteBuffer m) throws IOException {
        return ((m.get() & 0xFF) << 8) | (m.get() & 0xFF);
    }

    private SecurityUtils() {}

    public static final List<String> TLS_PROTOCOLS = new ArrayList<>();
    public static final Map<String, String> TLS_CIPHERSUITES = new HashMap<>();

    static {
        TLS_PROTOCOLS.add("TLSv1.2");
        TLS_PROTOCOLS.add("TLSv1.3");

        TLS_CIPHERSUITES.put("TLS_AES_128_GCM_SHA256", "TLSv1.3");
        TLS_CIPHERSUITES.put("TLS_AES_256_GCM_SHA384", "TLSv1.3");
        TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLSv1.2");
        TLS_CIPHERSUITES.put("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLSv1.2");
    }

    public static final String isFIPS = System.getProperty("semeru.fips");
    public static boolean isFIPS() {
        System.out.println("semeru.fips is: " + System.getProperty("semeru.fips"));
        return Boolean.parseBoolean(isFIPS);
    }

    public static final String FIPS_PROFILE = System.getProperty("semeru.customprofile");
    public static String getFipsProfile() {
        System.out.println("semeru.customprofile is: " + System.getProperty("semeru.customprofile"));
        return FIPS_PROFILE;
    }

    public static String revertJKSToPKCS12(String keyFilename, String passwd) {
        String p12keyFilename = keyFilename + ".p12";
        try {
            KeyStore jksKeystore = KeyStore.getInstance("JKS");
            try (FileInputStream fis = new FileInputStream(keyFilename)) {
                jksKeystore.load(fis, passwd.toCharArray());
            }

            KeyStore pkcs12Keystore = KeyStore.getInstance("PKCS12");
            pkcs12Keystore.load(null, null);

            Enumeration<String> aliasesKey = jksKeystore.aliases();
            while (aliasesKey.hasMoreElements()) {
                String alias = aliasesKey.nextElement();
                if (jksKeystore.isKeyEntry(alias)) {
                    char[] keyPassword = passwd.toCharArray();
                    Key key = jksKeystore.getKey(alias, keyPassword);
                    Certificate[] chain = jksKeystore.getCertificateChain(alias);
                    pkcs12Keystore.setKeyEntry(alias, key, passwd.toCharArray(), chain);
                } else if (jksKeystore.isCertificateEntry(alias)) {
                    Certificate cert = jksKeystore.getCertificate(alias);
                    pkcs12Keystore.setCertificateEntry(alias, cert);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(p12keyFilename)) {
                pkcs12Keystore.store(fos, passwd.toCharArray());
            }
            System.out.println("JKS keystore converted to PKCS12 successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return p12keyFilename;
    }

}
