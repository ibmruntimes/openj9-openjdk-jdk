/*
 * Copyright (c) 1996, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2018, 2022 All Rights Reserved
 * ===========================================================================
 */

package sun.security.provider;

import java.io.*;
import java.net.*;
import java.util.*;
import java.security.*;

import jdk.crypto.jniprovider.NativeCrypto;
import jdk.internal.util.StaticProperty;
import sun.security.action.GetBooleanAction;
import sun.security.action.GetPropertyAction;
import sun.security.util.SecurityProviderConstants;
import static sun.security.util.SecurityProviderConstants.getAliases;

/**
 * Defines the entries of the SUN provider.
 *
 * Algorithms supported, and their names:
 *
 * - SHA is the message digest scheme described in FIPS 180-1.
 *   Aliases for SHA are SHA-1 and SHA1.
 *
 * - SHA1withDSA is the signature scheme described in FIPS 186.
 *   (SHA used in DSA is SHA-1: FIPS 186 with Change No 1.)
 *   Aliases for SHA1withDSA are DSA, DSS, SHA/DSA, SHA-1/DSA, SHA1/DSA,
 *   SHAwithDSA, DSAWithSHA1, and the object
 *   identifier strings "OID.1.3.14.3.2.13", "OID.1.3.14.3.2.27" and
 *   "OID.1.2.840.10040.4.3".
 *
 * - SHA-2 is a set of message digest schemes described in FIPS 180-2.
 *   SHA-2 family of hash functions includes SHA-224, SHA-256, SHA-384,
 *   and SHA-512.
 *
 * - [SHA-224|SHA-256|SHA-384|SHA-512]withDSA are the signature schemes
 *   described in FIPS 186-3. The associated object identifiers are
 *   "OID.2.16.840.1.101.3.4.3.[1|2|3|4]" respectively.
 *
 * - [SHA3-224|SHA3-256|SHA3-384|SHA3-512]withDSA are the signature schemes
 *   using SHA-3 family of digests with DSA. The associated object identifiers
 *   are "OID.2.16.840.1.101.3.4.3.[5|6|7|8]" respectively.
 *
 * - DSA is the key generation scheme as described in FIPS 186.
 *   Aliases for DSA include the OID strings "OID.1.3.14.3.2.12"
 *   and "OID.1.2.840.10040.4.1".
 *
 * - MD5 is the message digest scheme described in RFC 1321.
 *   There are no aliases for MD5.
 *
 * - X.509 is the certificate factory type for X.509 certificates
 *   and CRLs. Aliases for X.509 are X509.
 *
 * - PKIX is the certification path validation algorithm described
 *   in RFC 5280. The ValidationAlgorithm attribute notes the
 *   specification that this provider implements.
 *
 * - JavaPolicy is the default file-based Policy type.
 *
 * - JavaLoginConfig is the default file-based LoginModule Configuration type.
 */

public final class SunEntries {

    /*
     * Check whether native crypto is enabled with property.
     * By default, the native crypto is enabled  and uses native library crypto.
     * The property 'jdk.nativeDigest' is used to disable Native digest alone
     * and 'jdk.nativeCrypto' is used to disable all native cryptos (Digest,
     * CBC, GCM, RSA, and ChaCha20).
     */
    private static boolean useNativeDigest = true;

    // the default algo used by SecureRandom class for new SecureRandom() calls
    public static final String DEF_SECURE_RANDOM_ALGO;

    SunEntries(Provider p) {
        services = new LinkedHashSet<>(50, 0.9f);

        // start populating content using the specified provider

        // common attribute map
        HashMap<String, String> attrs = new HashMap<>(3);

        /*
         * SecureRandom engines
         */
        attrs.put("ThreadSafe", "true");
        if (NativePRNG.isAvailable()) {
            add(p, "SecureRandom", "NativePRNG",
                    "sun.security.provider.NativePRNG", attrs);
        }
        if (NativePRNG.Blocking.isAvailable()) {
            add(p, "SecureRandom", "NativePRNGBlocking",
                    "sun.security.provider.NativePRNG$Blocking", attrs);
        }
        if (NativePRNG.NonBlocking.isAvailable()) {
            add(p, "SecureRandom", "NativePRNGNonBlocking",
                    "sun.security.provider.NativePRNG$NonBlocking", attrs);
        }
        attrs.put("ImplementedIn", "Software");
        add(p, "SecureRandom", "DRBG", "sun.security.provider.DRBG", attrs);
        add(p, "SecureRandom", "SHA1PRNG",
                "sun.security.provider.SecureRandom", attrs);

        /*
         * Signature engines
         */
        attrs.clear();
        String dsaKeyClasses = "java.security.interfaces.DSAPublicKey" +
                "|java.security.interfaces.DSAPrivateKey";
        attrs.put("SupportedKeyClasses", dsaKeyClasses);
        attrs.put("ImplementedIn", "Software");

        attrs.put("KeySize", "1024"); // for NONE and SHA1 DSA signatures

        addWithAlias(p, "Signature", "SHA1withDSA",
                "sun.security.provider.DSA$SHA1withDSA", attrs);
        addWithAlias(p, "Signature", "NONEwithDSA",
                "sun.security.provider.DSA$RawDSA", attrs);

        // for DSA signatures with 224/256-bit digests
        attrs.put("KeySize", "2048");

        addWithAlias(p, "Signature", "SHA224withDSA",
                "sun.security.provider.DSA$SHA224withDSA", attrs);
        addWithAlias(p, "Signature", "SHA256withDSA",
                "sun.security.provider.DSA$SHA256withDSA", attrs);

        addWithAlias(p, "Signature", "SHA3-224withDSA",
                "sun.security.provider.DSA$SHA3_224withDSA", attrs);
        addWithAlias(p, "Signature", "SHA3-256withDSA",
                "sun.security.provider.DSA$SHA3_256withDSA", attrs);

        attrs.put("KeySize", "3072"); // for DSA sig using 384/512-bit digests

        addWithAlias(p, "Signature", "SHA384withDSA",
                "sun.security.provider.DSA$SHA384withDSA", attrs);
        addWithAlias(p, "Signature", "SHA512withDSA",
                "sun.security.provider.DSA$SHA512withDSA", attrs);
        addWithAlias(p, "Signature", "SHA3-384withDSA",
                "sun.security.provider.DSA$SHA3_384withDSA", attrs);
        addWithAlias(p, "Signature", "SHA3-512withDSA",
                "sun.security.provider.DSA$SHA3_512withDSA", attrs);

        attrs.remove("KeySize");

        add(p, "Signature", "SHA1withDSAinP1363Format",
                "sun.security.provider.DSA$SHA1withDSAinP1363Format");
        add(p, "Signature", "NONEwithDSAinP1363Format",
                "sun.security.provider.DSA$RawDSAinP1363Format");
        add(p, "Signature", "SHA224withDSAinP1363Format",
                "sun.security.provider.DSA$SHA224withDSAinP1363Format");
        add(p, "Signature", "SHA256withDSAinP1363Format",
                "sun.security.provider.DSA$SHA256withDSAinP1363Format");
        add(p, "Signature", "SHA384withDSAinP1363Format",
                "sun.security.provider.DSA$SHA384withDSAinP1363Format");
        add(p, "Signature", "SHA512withDSAinP1363Format",
                "sun.security.provider.DSA$SHA512withDSAinP1363Format");
        add(p, "Signature", "SHA3-224withDSAinP1363Format",
                "sun.security.provider.DSA$SHA3_224withDSAinP1363Format");
        add(p, "Signature", "SHA3-256withDSAinP1363Format",
                "sun.security.provider.DSA$SHA3_256withDSAinP1363Format");
        add(p, "Signature", "SHA3-384withDSAinP1363Format",
                "sun.security.provider.DSA$SHA3_384withDSAinP1363Format");
        add(p, "Signature", "SHA3-512withDSAinP1363Format",
                "sun.security.provider.DSA$SHA3_512withDSAinP1363Format");
        /*
         *  Key Pair Generator engines
         */
        attrs.clear();
        attrs.put("ImplementedIn", "Software");
        attrs.put("KeySize", "2048"); // for DSA KPG and APG only

        String dsaKPGImplClass = "sun.security.provider.DSAKeyPairGenerator$";
        dsaKPGImplClass += (useLegacyDSA? "Legacy" : "Current");
        addWithAlias(p, "KeyPairGenerator", "DSA", dsaKPGImplClass, attrs);

        /*
         * Algorithm Parameter Generator engines
         */
        addWithAlias(p, "AlgorithmParameterGenerator", "DSA",
                "sun.security.provider.DSAParameterGenerator", attrs);
        attrs.remove("KeySize");

        /*
         * Algorithm Parameter engines
         */
        addWithAlias(p, "AlgorithmParameters", "DSA",
                "sun.security.provider.DSAParameters", attrs);

        /*
         * Key factories
         */
        addWithAlias(p, "KeyFactory", "DSA",
                "sun.security.provider.DSAKeyFactory", attrs);

        /*
         * Digest engines
         */
        String providerSHA;
        String providerSHA224;
        String providerSHA256;
        String providerSHA384;
        String providerSHA512;
        /*
         * Set the digest provider based on whether native crypto is
         * enabled or not.
         */
        if (useNativeDigest) {
            providerSHA = "sun.security.provider.NativeSHA";
            providerSHA224 = "sun.security.provider.NativeSHA2$SHA224";
            providerSHA256 = "sun.security.provider.NativeSHA2$SHA256";
            providerSHA384 = "sun.security.provider.NativeSHA5$SHA384";
            providerSHA512 = "sun.security.provider.NativeSHA5$SHA512";
        } else {
            providerSHA = "sun.security.provider.SHA";
            providerSHA224 = "sun.security.provider.SHA2$SHA224";
            providerSHA256 = "sun.security.provider.SHA2$SHA256";
            providerSHA384 = "sun.security.provider.SHA5$SHA384";
            providerSHA512 = "sun.security.provider.SHA5$SHA512";
        }
        addWithAlias(p, "MessageDigest", "MD2", "sun.security.provider.MD2",
                attrs);
        addWithAlias(p, "MessageDigest", "MD5", "sun.security.provider.MD5",
                attrs);
        addWithAlias(p, "MessageDigest", "SHA-1", providerSHA,
                attrs);

        addWithAlias(p, "MessageDigest", "SHA-224",
                providerSHA224, attrs);
        addWithAlias(p, "MessageDigest", "SHA-256",
                providerSHA256, attrs);
        addWithAlias(p, "MessageDigest", "SHA-384",
                providerSHA384, attrs);
        addWithAlias(p, "MessageDigest", "SHA-512",
                providerSHA512, attrs);
        addWithAlias(p, "MessageDigest", "SHA-512/224",
                "sun.security.provider.SHA5$SHA512_224", attrs);
        addWithAlias(p, "MessageDigest", "SHA-512/256",
                "sun.security.provider.SHA5$SHA512_256", attrs);
        addWithAlias(p, "MessageDigest", "SHA3-224",
                "sun.security.provider.SHA3$SHA224", attrs);
        addWithAlias(p, "MessageDigest", "SHA3-256",
                "sun.security.provider.SHA3$SHA256", attrs);
        addWithAlias(p, "MessageDigest", "SHA3-384",
                "sun.security.provider.SHA3$SHA384", attrs);
        addWithAlias(p, "MessageDigest", "SHA3-512",
                "sun.security.provider.SHA3$SHA512", attrs);

        /*
         * Certificates
         */
        addWithAlias(p, "CertificateFactory", "X.509",
                "sun.security.provider.X509Factory", attrs);

        /*
         * KeyStore
         */
        add(p, "KeyStore", "PKCS12",
                "sun.security.pkcs12.PKCS12KeyStore$DualFormatPKCS12");
        add(p, "KeyStore", "JKS",
                "sun.security.provider.JavaKeyStore$DualFormatJKS", attrs);
        add(p, "KeyStore", "CaseExactJKS",
                "sun.security.provider.JavaKeyStore$CaseExactJKS", attrs);
        add(p, "KeyStore", "DKS", "sun.security.provider.DomainKeyStore$DKS",
                attrs);


        /*
         * CertStores
         */
        add(p, "CertStore", "Collection",
                "sun.security.provider.certpath.CollectionCertStore",
                attrs);
        add(p, "CertStore", "com.sun.security.IndexedCollection",
                "sun.security.provider.certpath.IndexedCollectionCertStore",
                attrs);

        /*
         * Policy
         */
        add(p, "Policy", "JavaPolicy", "sun.security.provider.PolicySpiFile");

        /*
         * Configuration
         */
        add(p, "Configuration", "JavaLoginConfig",
                "sun.security.provider.ConfigFile$Spi");

        /*
         * CertPathBuilder and CertPathValidator
         */
        attrs.clear();
        attrs.put("ValidationAlgorithm", "RFC5280");
        attrs.put("ImplementedIn", "Software");

        add(p, "CertPathBuilder", "PKIX",
                "sun.security.provider.certpath.SunCertPathBuilder",
                attrs);
        add(p, "CertPathValidator", "PKIX",
                "sun.security.provider.certpath.PKIXCertPathValidator",
                attrs);
    }

    Iterator<Provider.Service> iterator() {
        return services.iterator();
    }

    private void add(Provider p, String type, String algo, String cn) {
        services.add(new Provider.Service(p, type, algo, cn, null, null));
    }

    private void add(Provider p, String type, String algo, String cn,
            HashMap<String, String> attrs) {
        services.add(new Provider.Service(p, type, algo, cn, null, attrs));
    }

    private void addWithAlias(Provider p, String type, String algo, String cn,
            HashMap<String, String> attrs) {
        services.add(new Provider.Service(p, type, algo, cn,
            getAliases(algo), attrs));
    }

    private LinkedHashSet<Provider.Service> services;

    // name of the *System* property, takes precedence over PROP_RNDSOURCE
    private static final String PROP_EGD = "java.security.egd";
    // name of the *Security* property
    private static final String PROP_RNDSOURCE = "securerandom.source";

    private static final boolean useLegacyDSA =
        GetBooleanAction.privilegedGetProperty
            ("jdk.security.legacyDSAKeyPairGenerator");

    static final String URL_DEV_RANDOM = "file:/dev/random";
    static final String URL_DEV_URANDOM = "file:/dev/urandom";

    @SuppressWarnings("removal")
    private static final String seedSource = AccessController.doPrivileged(
                new PrivilegedAction<String>() {

            @Override
            public String run() {
                String egdSource = System.getProperty(PROP_EGD, "");
                if (egdSource.length() != 0) {
                    return egdSource;
                }
                egdSource = Security.getProperty(PROP_RNDSOURCE);
                if (egdSource == null) {
                    return "";
                }
                return egdSource;
            }
        });

    static {
        DEF_SECURE_RANDOM_ALGO  = (NativePRNG.isAvailable() &&
            (seedSource.equals(URL_DEV_URANDOM) ||
             seedSource.equals(URL_DEV_RANDOM)) ?
            "NativePRNG" : "DRBG");
    }

    static String getSeedSource() {
        return seedSource;
    }

    /*
     * Use a URI to access this File. Previous code used a URL
     * which is less strict on syntax. If we encounter a
     * URISyntaxException we make best efforts for backwards
     * compatibility. e.g. space character in deviceName string.
     *
     * Method called within PrivilegedExceptionAction block.
     *
     * Moved from SeedGenerator to avoid initialization problems with
     * signed providers.
     */
    static File getDeviceFile(URL device) throws IOException {
        try {
            URI deviceURI = device.toURI();
            if(deviceURI.isOpaque()) {
                // File constructor does not accept opaque URI
                URI localDir = new File(
                    StaticProperty.userDir()).toURI();
                String uriPath = localDir.toString() +
                                     deviceURI.toString().substring(5);
                return new File(URI.create(uriPath));
            } else {
                return new File(deviceURI);
            }
        } catch (URISyntaxException use) {
            /*
             * Make best effort to access this File.
             * We can try using the URL path.
             */
            return new File(device.getPath());
        }
    }

    static {
        String nativeCryptTrace = GetPropertyAction.privilegedGetProperty("jdk.nativeCryptoTrace");
        String nativeCryptStr = GetPropertyAction.privilegedGetProperty("jdk.nativeCrypto");
        String nativeDigestStr = GetPropertyAction.privilegedGetProperty("jdk.nativeDigest");

        if (Boolean.parseBoolean(nativeCryptStr) || nativeCryptStr == null) {
            /* nativeCrypto is enabled */
            if (!(Boolean.parseBoolean(nativeDigestStr) || nativeDigestStr == null)) {
                useNativeDigest = false;
            }
        } else {
            /* nativeCrypto is disabled */
            useNativeDigest = false;
        }

        if (useNativeDigest) {
            /*
             * User want to use native crypto implementation.
             * Make sure the native crypto libraries are loaded successfully.
             * Otherwise, throw a warning message and fall back to the in-built
             * java crypto implementation.
             */
            if (!NativeCrypto.isLoaded()) {
                useNativeDigest = false;

                if (nativeCryptTrace != null) {
                    System.err.println("Warning: Native crypto library load failed." +
                            " Using Java crypto implementation");
                }
            } else {
                if (nativeCryptTrace != null) {
                    System.err.println("MessageDigest load - using Native crypto library.");
                }
            }
        } else {
            if (nativeCryptTrace != null) {
                System.err.println("MessageDigest load - Native crypto library disabled.");
            }
        }
    }

}
