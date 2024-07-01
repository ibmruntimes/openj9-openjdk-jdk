/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2022, 2024 All Rights Reserved
 * ===========================================================================
 */

package sun.security.ec;

import java.security.AccessController;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.ProviderException;
import java.util.HashMap;
import java.util.List;

import jdk.crypto.jniprovider.NativeCrypto;

import jdk.internal.util.OperatingSystem;

import sun.security.ec.ed.EdDSAKeyFactory;
import sun.security.ec.ed.EdDSAKeyPairGenerator;
import sun.security.ec.ed.EdDSASignature;
import sun.security.util.CurveDB;
import sun.security.util.NamedCurve;

import static sun.security.util.SecurityConstants.PROVIDER_VER;
import static sun.security.util.SecurityProviderConstants.*;

/**
 * Provider class for the Elliptic Curve provider.
 */
public final class SunEC extends Provider {

    private static final long serialVersionUID = -2279741672933606418L;

    private static final boolean nativeCryptTrace = NativeCrypto.isTraceEnabled();

    /* The property 'jdk.nativeEC' is used to control enablement of the native
     * ECDH implementation.
     */
    private static final boolean useNativeECDH = NativeCrypto.isAlgorithmEnabled("jdk.nativeEC", "SunEC");

    /* The property 'jdk.nativeECKeyGen' is used to control enablement of the native
     * ECKeyGeneration implementation.
     * OpenSSL 1.1.0 or above is required for EC key generation support.
     */
    private static final boolean useNativeECKeyGen = NativeCrypto.isAlgorithmEnabled("jdk.nativeECKeyGen", "SunEC");

    /* The property 'jdk.nativeECDSA' is used to control enablement of the native
     * ECDSA signature implementation.
     */
    private static final boolean useNativeECDSA = NativeCrypto.isAlgorithmEnabled("jdk.nativeECDSA", "SunEC");

    /* The property 'jdk.nativeXDHKeyAgreement' is used to control enablement of the native
     * XDH key agreement. XDH key agreement is only supported in OpenSSL 1.1.1 and above.
     */
    private static final boolean useNativeXDHKeyAgreement =
        NativeCrypto.isAlgorithmEnabled("jdk.nativeXDHKeyAgreement", "SunEC");

    /* The property 'jdk.nativeXDHKeyGen' is used to control enablement of the native
     * XDH key generation. XDH key generation is only supported in OpenSSL 1.1.1 and above.
     */
    private static final boolean useNativeXDHKeyGen =
        NativeCrypto.isAlgorithmEnabled("jdk.nativeXDHKeyGen", "SunEC");

    private static class ProviderServiceA extends ProviderService {
        ProviderServiceA(Provider p, String type, String algo, String cn,
            HashMap<String, String> attrs) {
            super(p, type, algo, cn, getAliases(algo), attrs);
        }
    }

    private static class ProviderService extends Provider.Service {

        ProviderService(Provider p, String type, String algo, String cn) {
            super(p, type, algo, cn, null, null);
        }

        ProviderService(Provider p, String type, String algo, String cn,
            List<String> aliases, HashMap<String, String> attrs) {
            super(p, type, algo, cn, aliases, attrs);
        }

        @Override
        public Object newInstance(Object ctrParamObj)
            throws NoSuchAlgorithmException {
            String type = getType();
            if (ctrParamObj != null) {
                throw new InvalidParameterException
                    ("constructorParameter not used with " + type + " engines");
            }

            String algo = getAlgorithm();
            try {
                if (type.equals("Signature")) {

                    if (algo.equalsIgnoreCase("EdDSA")) {
                        return new EdDSASignature();
                    } else if (algo.equalsIgnoreCase("Ed25519")) {
                        return new EdDSASignature.Ed25519();
                    } else if (algo.equalsIgnoreCase("Ed448")) {
                        return new EdDSASignature.Ed448();
                    }

                    boolean inP1363 = algo.endsWith("inP1363Format");
                    if (inP1363) {
                        algo = algo.substring(0, algo.length() - 13);
                    }
                    if (useNativeECDSA
                        && (NativeCrypto.getVersionIfAvailable() >= NativeCrypto.OPENSSL_VERSION_1_1_1)
                    ) {
                        if (nativeCryptTrace) {
                            System.err.println("ECDSA Signature - Using OpenSSL integration.");
                        }
                        if (algo.equals("SHA1withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA1inP1363Format()
                                    : new NativeECDSASignature.SHA1();
                        } else if (algo.equals("SHA224withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA224inP1363Format()
                                    : new NativeECDSASignature.SHA224();
                        } else if (algo.equals("SHA256withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA256inP1363Format()
                                    : new NativeECDSASignature.SHA256();
                        } else if (algo.equals("SHA384withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA384inP1363Format()
                                    : new NativeECDSASignature.SHA384();
                        } else if (algo.equals("SHA512withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA512inP1363Format()
                                    : new NativeECDSASignature.SHA512();
                        } else if (algo.equals("NONEwithECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.RawinP1363Format()
                                    : new NativeECDSASignature.Raw();
                        } else if (algo.equals("SHA3-224withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA3_224inP1363Format()
                                    : new NativeECDSASignature.SHA3_224();
                        } else if (algo.equals("SHA3-256withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA3_256inP1363Format()
                                    : new NativeECDSASignature.SHA3_256();
                        } else if (algo.equals("SHA3-384withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA3_384inP1363Format()
                                    : new NativeECDSASignature.SHA3_384();
                        } else if (algo.equals("SHA3-512withECDSA")) {
                            return inP1363
                                    ? new NativeECDSASignature.SHA3_512inP1363Format()
                                    : new NativeECDSASignature.SHA3_512();
                        }
                    } else {
                        if (algo.equals("SHA1withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA1inP1363Format()
                                    : new ECDSASignature.SHA1();
                        } else if (algo.equals("SHA224withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA224inP1363Format()
                                    : new ECDSASignature.SHA224();
                        } else if (algo.equals("SHA256withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA256inP1363Format()
                                    : new ECDSASignature.SHA256();
                        } else if (algo.equals("SHA384withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA384inP1363Format()
                                    : new ECDSASignature.SHA384();
                        } else if (algo.equals("SHA512withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA512inP1363Format()
                                    : new ECDSASignature.SHA512();
                        } else if (algo.equals("NONEwithECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.RawinP1363Format()
                                    : new ECDSASignature.Raw();
                        } else if (algo.equals("SHA3-224withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA3_224inP1363Format()
                                    : new ECDSASignature.SHA3_224();
                        } else if (algo.equals("SHA3-256withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA3_256inP1363Format()
                                    : new ECDSASignature.SHA3_256();
                        } else if (algo.equals("SHA3-384withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA3_384inP1363Format()
                                    : new ECDSASignature.SHA3_384();
                        } else if (algo.equals("SHA3-512withECDSA")) {
                            return inP1363
                                    ? new ECDSASignature.SHA3_512inP1363Format()
                                    : new ECDSASignature.SHA3_512();
                        }
                    }
                } else if (type.equals("KeyFactory")) {
                    if (algo.equals("EC")) {
                        return new ECKeyFactory();
                    } else if (algo.equals("XDH")) {
                        return new XDHKeyFactory();
                    } else if (algo.equals("X25519")) {
                        return new XDHKeyFactory.X25519();
                    } else if (algo.equals("X448")) {
                        return new XDHKeyFactory.X448();
                    } else if (algo.equalsIgnoreCase("EdDSA")) {
                        return new EdDSAKeyFactory();
                    } else if (algo.equalsIgnoreCase("Ed25519")) {
                        return new EdDSAKeyFactory.Ed25519();
                    } else if (algo.equalsIgnoreCase("Ed448")) {
                        return new EdDSAKeyFactory.Ed448();
                    }
                } else  if (type.equals("AlgorithmParameters")) {
                    if (algo.equals("EC")) {
                        return new sun.security.util.ECParameters();
                    }
                } else  if (type.equals("KeyPairGenerator")) {
                    if (algo.equals("EC")) {
                        if (useNativeECKeyGen) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_1_1_0) {
                                if (nativeCryptTrace) {
                                    System.err.println("EC KeyPair Generation - Not using OpenSSL integration due to older version of OpenSSL (<1.1.0).");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("EC KeyPair Generation - Using OpenSSL integration.");
                                }
                                return new NativeECKeyPairGenerator();
                            }
                        }
                        return new ECKeyPairGenerator();
                    } else if (algo.equals("XDH")) {
                        if (useNativeXDHKeyGen) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_1_1_1) {
                                if (nativeCryptTrace) {
                                    System.err.println("XDH KeyPair Generation - Not using OpenSSL integration due to older version of OpenSSL (<1.1.1).");
                                }
                            } else if (OperatingSystem.isAix()) {
                                /* Disabling OpenSSL usage on AIX due to perfomance regression observed. */
                                if (nativeCryptTrace) {
                                    System.err.println("XDH KeyPair Generation - Not using OpenSSL integration on AIX.");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("XDH KeyPair Generation - Using OpenSSL integration.");
                                }
                                return new NativeXDHKeyPairGenerator();
                            }
                        }
                        return new XDHKeyPairGenerator();
                    } else if (algo.equals("X25519")) {
                        if (useNativeXDHKeyGen) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_1_1_1) {
                                if (nativeCryptTrace) {
                                    System.err.println("X25519 KeyPair Generation - Not using OpenSSL integration due to older version of OpenSSL (<1.1.1).");
                                }
                            } else if (OperatingSystem.isAix()) {
                                /* Disabling OpenSSL usage on AIX due to perfomance regression observed. */
                                if (nativeCryptTrace) {
                                    System.err.println("X25519 KeyPair Generation - Not using OpenSSL integration on AIX.");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("X25519 KeyPair Generation - Using OpenSSL integration.");
                                }
                                return new NativeXDHKeyPairGenerator.X25519();
                            }
                        }
                        return new XDHKeyPairGenerator.X25519();
                    } else if (algo.equals("X448")) {
                        if (useNativeXDHKeyGen) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_1_1_1) {
                                if (nativeCryptTrace) {
                                    System.err.println("X448 KeyPair Generation - Not using OpenSSL integration due to older version of OpenSSL (<1.1.1).");
                                }
                            } else if (OperatingSystem.isAix()) {
                                /* Disabling OpenSSL usage on AIX due to perfomance regression observed. */
                                if (nativeCryptTrace) {
                                    System.err.println("X448 KeyPair Generation - Not using OpenSSL integration on AIX.");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("X448 KeyPair Generation - Using OpenSSL integration.");
                                }
                                return new NativeXDHKeyPairGenerator.X448();
                            }
                        }
                        return new XDHKeyPairGenerator.X448();
                    } else if (algo.equalsIgnoreCase("EdDSA")) {
                        return new EdDSAKeyPairGenerator();
                    } else if (algo.equalsIgnoreCase("Ed25519")) {
                        return new EdDSAKeyPairGenerator.Ed25519();
                    } else if (algo.equalsIgnoreCase("Ed448")) {
                        return new EdDSAKeyPairGenerator.Ed448();
                    }
                } else  if (type.equals("KeyAgreement")) {
                    if (algo.equals("ECDH")) {
                        if (useNativeECDH && NativeCrypto.isAllowedAndLoaded()) {
                            return new NativeECDHKeyAgreement();
                        } else {
                            return new ECDHKeyAgreement();
                        }
                    } else if (algo.equals("XDH")) {
                        if (useNativeXDHKeyAgreement) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_1_1_1) {
                                if (nativeCryptTrace) {
                                    System.err.println("XDH Key Agreement - Not using OpenSSL integration due to older version of OpenSSL (<1.1.1).");
                                }
                            } else if (OperatingSystem.isAix()) {
                                /* Disabling OpenSSL usage on AIX due to perfomance regression observed. */
                                if (nativeCryptTrace) {
                                    System.err.println("XDH Key Agreement - Not using OpenSSL integration on AIX.");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("XDH Key Agreement - Using OpenSSL integration.");
                                }
                                return new NativeXDHKeyAgreement();
                            }
                        }
                        return new XDHKeyAgreement();
                    } else if (algo.equals("X25519")) {
                        if (useNativeXDHKeyAgreement) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_1_1_1) {
                                if (nativeCryptTrace) {
                                    System.err.println("X25519 Key Agreement - Not using OpenSSL integration due to older version of OpenSSL (<1.1.1).");
                                }
                            } else if (OperatingSystem.isAix()) {
                                /* Disabling OpenSSL usage on AIX due to perfomance regression observed. */
                                if (nativeCryptTrace) {
                                    System.err.println("X25519 Key Agreement - Not using OpenSSL integration on AIX.");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("X25519 Key Agreement - Using OpenSSL integration.");
                                }
                                return new NativeXDHKeyAgreement.X25519();
                            }
                        }
                        return new XDHKeyAgreement.X25519();
                    } else if (algo.equals("X448")) {
                        if (useNativeXDHKeyAgreement) {
                            if (NativeCrypto.getVersionIfAvailable() < NativeCrypto.OPENSSL_VERSION_3_0_0) {
                                if (nativeCryptTrace) {
                                    System.err.println("X448 Key Agreement - Not using OpenSSL integration due to older version of OpenSSL (<3.x).");
                                }
                            } else if (OperatingSystem.isAix()) {
                                /* Disabling OpenSSL usage on AIX due to perfomance regression observed. */
                                if (nativeCryptTrace) {
                                    System.err.println("X448 Key Agreement - Not using OpenSSL integration on AIX.");
                                }
                            } else {
                                if (nativeCryptTrace) {
                                    System.err.println("X448 Key Agreement - Using OpenSSL integration.");
                                }
                                return new NativeXDHKeyAgreement.X448();
                            }
                        }
                        return new XDHKeyAgreement.X448();
                    }
                }
            } catch (Exception ex) {
                throw new NoSuchAlgorithmException("Error constructing " +
                    type + " for " + algo + " using SunEC", ex);
            }
            throw new ProviderException("No impl for " + algo +
                " " + type);
        }
    }

    @SuppressWarnings("removal")
    public SunEC() {
        super("SunEC", PROVIDER_VER, "Sun Elliptic Curve provider");
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                putEntries();
                return null;
            }
        });
    }

    void putEntries() {
        HashMap<String, String> ATTRS = new HashMap<>(3);
        ATTRS.put("ImplementedIn", "Software");
        String ecKeyClasses = "java.security.interfaces.ECPublicKey" +
                 "|java.security.interfaces.ECPrivateKey";
        ATTRS.put("SupportedKeyClasses", ecKeyClasses);
        ATTRS.put("KeySize", "256");

        /*
         *  Key Factory engine
         */
        putService(new ProviderServiceA(this, "KeyFactory",
            "EC", "sun.security.ec.ECKeyFactory", ATTRS));

        /*
         * Algorithm Parameter engine
         */
        // "AlgorithmParameters.EC SupportedCurves" prop used by unit test
        boolean firstCurve = true;
        StringBuilder names = new StringBuilder();

        for (NamedCurve namedCurve : List.of(
                CurveDB.P_256, CurveDB.P_384, CurveDB.P_521)) {
            if (!firstCurve) {
                names.append("|");
            } else {
                firstCurve = false;
            }

            names.append("[");
            String[] commonNames = namedCurve.getNameAndAliases();
            for (String commonName : commonNames) {
                names.append(commonName);
                names.append(",");
            }

            names.append(namedCurve.getObjectId());
            names.append("]");
        }

        HashMap<String, String> apAttrs = new HashMap<>(ATTRS);
        apAttrs.put("SupportedCurves", names.toString());

        putService(new ProviderServiceA(this, "AlgorithmParameters",
            "EC", "sun.security.util.ECParameters", apAttrs));

        putXDHEntries();
        putEdDSAEntries();

        /*
         * Signature engines
         */
        if (useNativeECDSA
            && (NativeCrypto.getVersionIfAvailable() >= NativeCrypto.OPENSSL_VERSION_1_1_1)
        ) {
            putService(new ProviderService(this, "Signature",
                "NONEwithECDSA", "sun.security.ec.NativeECDSASignature$Raw",
                null, ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA1withECDSA", "sun.security.ec.NativeECDSASignature$SHA1",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA224withECDSA", "sun.security.ec.NativeECDSASignature$SHA224",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA256withECDSA", "sun.security.ec.NativeECDSASignature$SHA256",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA384withECDSA", "sun.security.ec.NativeECDSASignature$SHA384",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA512withECDSA", "sun.security.ec.NativeECDSASignature$SHA512",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-224withECDSA", "sun.security.ec.NativeECDSASignature$SHA3_224",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-256withECDSA", "sun.security.ec.NativeECDSASignature$SHA3_256",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-384withECDSA", "sun.security.ec.NativeECDSASignature$SHA3_384",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-512withECDSA", "sun.security.ec.NativeECDSASignature$SHA3_512",
                ATTRS));

            putService(new ProviderService(this, "Signature",
                "NONEwithECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$RawinP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA1withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA1inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA224withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA224inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA256withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA256inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA384withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA384inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA512withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA512inP1363Format"));

            putService(new ProviderService(this, "Signature",
                "SHA3-224withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA3_224inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA3-256withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA3_256inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA3-384withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA3_384inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA3-512withECDSAinP1363Format",
                "sun.security.ec.NativeECDSASignature$SHA3_512inP1363Format"));
        } else {
            putService(new ProviderService(this, "Signature",
                "NONEwithECDSA", "sun.security.ec.ECDSASignature$Raw",
                null, ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA1withECDSA", "sun.security.ec.ECDSASignature$SHA1",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA224withECDSA", "sun.security.ec.ECDSASignature$SHA224",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA256withECDSA", "sun.security.ec.ECDSASignature$SHA256",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA384withECDSA", "sun.security.ec.ECDSASignature$SHA384",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA512withECDSA", "sun.security.ec.ECDSASignature$SHA512",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-224withECDSA", "sun.security.ec.ECDSASignature$SHA3_224",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-256withECDSA", "sun.security.ec.ECDSASignature$SHA3_256",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-384withECDSA", "sun.security.ec.ECDSASignature$SHA3_384",
                ATTRS));
            putService(new ProviderServiceA(this, "Signature",
                "SHA3-512withECDSA", "sun.security.ec.ECDSASignature$SHA3_512",
                ATTRS));

            putService(new ProviderService(this, "Signature",
                "NONEwithECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$RawinP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA1withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA1inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA224withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA224inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA256withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA256inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA384withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA384inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA512withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA512inP1363Format"));

            putService(new ProviderService(this, "Signature",
                "SHA3-224withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA3_224inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA3-256withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA3_256inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA3-384withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA3_384inP1363Format"));
            putService(new ProviderService(this, "Signature",
                "SHA3-512withECDSAinP1363Format",
                "sun.security.ec.ECDSASignature$SHA3_512inP1363Format"));
        }

        /*
         *  Key Pair Generator engine
         */
        /* Disabling OpenSSL usage in AIX due to perfomance regression observed */
        if (useNativeECKeyGen
            && (NativeCrypto.getVersionIfAvailable() >= NativeCrypto.OPENSSL_VERSION_1_1_0)
            && !OperatingSystem.isAix()
        ) {
            putService(new ProviderServiceA(this, "KeyPairGenerator",
                "EC", "sun.security.ec.NativeECKeyPairGenerator", ATTRS));
        } else {
            putService(new ProviderServiceA(this, "KeyPairGenerator",
                "EC", "sun.security.ec.ECKeyPairGenerator", ATTRS));
        }

        /*
         * Key Agreement engine
         */
        if (useNativeECDH && NativeCrypto.isAllowedAndLoaded()) {
            putService(new ProviderService(this, "KeyAgreement",
                "ECDH", "sun.security.ec.NativeECDHKeyAgreement", null, ATTRS));
        } else {
            putService(new ProviderService(this, "KeyAgreement",
                "ECDH", "sun.security.ec.ECDHKeyAgreement", null, ATTRS));
        }
    }

    private void putXDHEntries() {

        HashMap<String, String> ATTRS = new HashMap<>(1);
        ATTRS.put("ImplementedIn", "Software");

        putService(new ProviderService(this, "KeyFactory",
            "XDH", "sun.security.ec.XDHKeyFactory", null, ATTRS));
        putService(new ProviderServiceA(this, "KeyFactory",
            "X25519", "sun.security.ec.XDHKeyFactory.X25519",
            ATTRS));
        putService(new ProviderServiceA(this, "KeyFactory",
            "X448", "sun.security.ec.XDHKeyFactory.X448",
            ATTRS));

        if (useNativeXDHKeyGen
            && (NativeCrypto.getVersionIfAvailable() >= NativeCrypto.OPENSSL_VERSION_1_1_1)
            && !OperatingSystem.isAix()
        ) {
            putService(new ProviderService(this, "KeyPairGenerator",
                "XDH", "sun.security.ec.NativeXDHKeyPairGenerator", null, ATTRS));
            putService(new ProviderServiceA(this, "KeyPairGenerator",
                "X25519", "sun.security.ec.NativeXDHKeyPairGenerator.X25519",
                ATTRS));
            putService(new ProviderServiceA(this, "KeyPairGenerator",
                "X448", "sun.security.ec.NativeXDHKeyPairGenerator.X448",
                ATTRS));
        } else {
            putService(new ProviderService(this, "KeyPairGenerator",
                "XDH", "sun.security.ec.XDHKeyPairGenerator", null, ATTRS));
            putService(new ProviderServiceA(this, "KeyPairGenerator",
                "X25519", "sun.security.ec.XDHKeyPairGenerator.X25519",
                ATTRS));
            putService(new ProviderServiceA(this, "KeyPairGenerator",
                "X448", "sun.security.ec.XDHKeyPairGenerator.X448",
                ATTRS));
        }

        if (useNativeXDHKeyAgreement
            && (NativeCrypto.getVersionIfAvailable() >= NativeCrypto.OPENSSL_VERSION_1_1_1)
            && !OperatingSystem.isAix()
        ) {
            putService(new ProviderService(this, "KeyAgreement",
                "XDH", "sun.security.ec.NativeXDHKeyAgreement", null, ATTRS));
            putService(new ProviderServiceA(this, "KeyAgreement",
                "X25519", "sun.security.ec.NativeXDHKeyAgreement.X25519",
                ATTRS));
            putService(new ProviderServiceA(this, "KeyAgreement",
                "X448", "sun.security.ec.NativeXDHKeyAgreement.X448",
                ATTRS));
        } else {
            putService(new ProviderService(this, "KeyAgreement",
                "XDH", "sun.security.ec.XDHKeyAgreement", null, ATTRS));
            putService(new ProviderServiceA(this, "KeyAgreement",
                "X25519", "sun.security.ec.XDHKeyAgreement.X25519",
                ATTRS));
            putService(new ProviderServiceA(this, "KeyAgreement",
                "X448", "sun.security.ec.XDHKeyAgreement.X448",
                ATTRS));
        }
    }

    private void putEdDSAEntries() {

        HashMap<String, String> ATTRS = new HashMap<>(1);
        ATTRS.put("ImplementedIn", "Software");

        putService(new ProviderService(this, "KeyFactory",
            "EdDSA", "sun.security.ec.ed.EdDSAKeyFactory", null, ATTRS));
        putService(new ProviderServiceA(this, "KeyFactory",
            "Ed25519", "sun.security.ec.ed.EdDSAKeyFactory.Ed25519", ATTRS));
        putService(new ProviderServiceA(this, "KeyFactory",
            "Ed448", "sun.security.ec.ed.EdDSAKeyFactory.Ed448", ATTRS));

        putService(new ProviderService(this, "KeyPairGenerator",
            "EdDSA", "sun.security.ec.ed.EdDSAKeyPairGenerator", null, ATTRS));
        putService(new ProviderServiceA(this, "KeyPairGenerator",
            "Ed25519", "sun.security.ec.ed.EdDSAKeyPairGenerator.Ed25519",
            ATTRS));
        putService(new ProviderServiceA(this, "KeyPairGenerator",
            "Ed448", "sun.security.ec.ed.EdDSAKeyPairGenerator.Ed448",
            ATTRS));

        putService(new ProviderService(this, "Signature",
            "EdDSA", "sun.security.ec.ed.EdDSASignature", null, ATTRS));
        putService(new ProviderServiceA(this, "Signature",
            "Ed25519", "sun.security.ec.ed.EdDSASignature.Ed25519", ATTRS));
        putService(new ProviderServiceA(this, "Signature",
            "Ed448", "sun.security.ec.ed.EdDSASignature.Ed448", ATTRS));

    }
}
