/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2022, 2026 All Rights Reserved
 * ===========================================================================
 */

package sun.security.ec;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

import jdk.crypto.jniprovider.NativeCrypto;

import sun.security.ec.point.Point;
import sun.security.util.ArrayUtil;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;
import sun.security.util.KeyUtil;
import sun.security.util.NamedCurve;

/**
 * Native KeyAgreement implementation for ECDH.
 */
public final class NativeECDHKeyAgreement extends KeyAgreementSpi {

    private static NativeCrypto nativeCrypto;
    private static final boolean nativeCryptTrace = NativeCrypto.isTraceEnabled();

    /* stores whether a curve is supported by OpenSSL (true) or not (false) */
    private static final Map<String, Boolean> curveSupported = new ConcurrentHashMap<>();

    /* private key, if initialized */
    private ECPrivateKey privateKey;

    /* native private key, created from privateKey */
    private NativeECUtil.NativeKey nativePrivateKey;

    /* operations associated with private key, if initialized */
    private ECOperations privateKeyOps;

    /* public key, non-null between doPhase() & generateSecret() only */
    private ECPublicKey publicKey;

    /* native public key, non-null only while publicKey is non-null */
    private NativeECUtil.NativeKey nativePublicKey;

    /* the type of EC curve */
    private String curve;

    /* length of the secret to be derived */
    private int secretLen;

    /* the java implementation, initialized if needed */
    private ECDHKeyAgreement javaImplementation;

    /**
     * Constructs a new NativeECDHKeyAgreement.
     */
    public NativeECDHKeyAgreement() {
    }

    /* Clears privateKey and frees its native key. */
    private void clearPrivateKey() {
        NativeECUtil.releaseNativeKey(this.nativePrivateKey);
        this.nativePrivateKey = null;
        this.privateKey = null;
    }

    private void init(Key key)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        this.privateKeyOps = null;
        NativeECUtil.releaseNativeKey(this.nativePublicKey);
        this.nativePublicKey = null;
        this.publicKey = null;

        /*
         * privateKey is kept so it can be compared with the incoming key, and
         * is cleared on every exit that does not end on the native path.
         */
        if (!(key instanceof PrivateKey)) {
            clearPrivateKey();
            throw new InvalidKeyException
                ("Key must be an instance of PrivateKey");
        }
        /* attempt to translate the key if it is not an ECKey */
        ECKey ecKey;
        try {
            ecKey = ECKeyFactory.toECKey(key);
        } catch (InvalidKeyException e) {
            clearPrivateKey();
            throw e;
        }
        if (ecKey instanceof ECPrivateKey ecPrivateKey) {
            ECParameterSpec params = ecPrivateKey.getParams();
            ECOperations ops = ECOperations.forParameters(params).orElse(null);
            if ((ops == null) && !NativeECUtil.isBrainpoolP512r1(params)) {
                clearPrivateKey();
                NamedCurve nc = CurveDB.lookup(params);
                throw new InvalidAlgorithmParameterException(
                        "Curve not supported: " +
                        ((nc != null) ? nc.toString() : "unknown"));
            }
            try {
                ECUtil.checkPrivateKey(ecPrivateKey);
            } catch (InvalidKeyException e) {
                clearPrivateKey();
                throw e;
            }

            this.privateKeyOps = ops;

            this.curve = NativeECUtil.getCurveName(params);
            if ((this.curve == null) || !NativeECUtil.isCurveSupported(this.curve, params)) {
                clearPrivateKey();
                this.initializeJavaImplementation(key);
                return;
            }

            /* reuse the native key if the key is unchanged */
            if ((this.nativePrivateKey == null) || (ecPrivateKey != this.privateKey)) {
                clearPrivateKey();
                this.nativePrivateKey = NativeECUtil.createPrivateKeyNative(ecPrivateKey);
            }
            if (this.nativePrivateKey == null) {
                clearPrivateKey();
                if (nativeCryptTrace) {
                    System.err.println("Init: Could not create a pointer to a native private key."
                            + " Using Java implementation.");
                }
                this.initializeJavaImplementation(key);
                return;
            }

            this.privateKey = ecPrivateKey;
            this.javaImplementation = null;
        } else {
            clearPrivateKey();
            boolean absent = NativeECUtil.putCurveIfAbsent("ECKeyImpl", Boolean.FALSE);
            /* only print the first time a curve is used */
            if (absent && nativeCryptTrace) {
                System.err.println("Only ECPrivateKey" +
                        " is supported by the native implementation, " +
                        "using Java crypto implementation for key agreement.");
            }
            this.initializeJavaImplementation(key);
        }
    }

    @Override
    protected void engineInit(Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            init(key);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    protected void engineInit(Key key, AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException
                        ("Parameters not supported");
        }
        init(key);
    }

    @Override
    protected Key engineDoPhase(Key key, boolean lastPhase)
            throws InvalidKeyException, IllegalStateException {
        if (this.javaImplementation != null) {
            return this.javaImplementation.engineDoPhase(key, lastPhase);
        }
        if (this.privateKey == null) {
            throw new IllegalStateException("Not initialized");
        }
        if (this.publicKey != null) {
            throw new IllegalStateException("Phase already executed");
        }
        if (!lastPhase) {
            throw new IllegalStateException
                ("Only two party agreement supported, lastPhase must be true");
        }
        if (!(key instanceof ECPublicKey ecKey)) {
            throw new InvalidKeyException
                ("Key must be an instance of PublicKey");
        }

        // Validate public key when we are not making use of a brainpoolP512r1 based key.
        if (!NativeECUtil.isBrainpoolP512r1(this.privateKey.getParams())) {
            validate(privateKeyOps, ecKey);
        }

        this.publicKey = ecKey;
        this.nativePublicKey = NativeECUtil.createPublicKeyNative(ecKey);
        if (this.nativePublicKey == null) {
            if (nativeCryptTrace) {
                System.err.println("DoPhase: Could not create a pointer to a native public key."
                        + " Using Java implementation.");
            }
            try {
                this.initializeJavaImplementation(this.privateKey);
                this.javaImplementation.engineDoPhase(ecKey, true);
            } catch (InvalidKeyException e) {
                /* should not happen */
                throw new InternalError(e);
            }
        }

        int keyLenBits = this.publicKey.getParams().getCurve().getField().getFieldSize();
        this.secretLen = (keyLenBits + 7) >> 3;

        return null;
    }

    // Verify that x and y are integers in the interval [0, p - 1].
    private static void validateCoordinate(BigInteger c, BigInteger mod)
            throws InvalidKeyException {
        if ((c.signum() < 0) || (c.compareTo(mod) >= 0)) {
            throw new InvalidKeyException("Invalid coordinate");
        }
    }

    // Check whether a public key is valid, following the ECC
    // Full Public-key Validation Routine (See section 5.6.2.3.3,
    // NIST SP 800-56A Revision 3).
    private static void validate(ECOperations ops, ECPublicKey key)
            throws InvalidKeyException {
        // Note: Per the NIST 800-56A specification, it is required
        // to verify that the public key is not the identity element
        // (point of infinity).  However, the point of infinity has no
        // affine coordinates, although the point of infinity could
        // be encoded.  Per IEEE 1363.3-2013 (see section A.6.4.1),
        // the point of infinity is represented by a pair of
        // coordinates (x, y) not on the curve.  For EC prime finite
        // field (q = p^m), the point of infinity is (0, 0) unless
        // b = 0; in which case it is (0, 1).
        //
        // It means that this verification could be covered by the
        // validation that the public key is on the curve.  As will be
        // verified in the following steps.

        // Ensure that integers are in proper range.
        BigInteger x = key.getW().getAffineX();
        BigInteger y = key.getW().getAffineY();
        BigInteger p = ops.getField().getSize();
        validateCoordinate(x, p);
        validateCoordinate(y, p);

        ECParameterSpec spec = key.getParams();

        // Ensure the point is on the curve.
        EllipticCurve curve = spec.getCurve();
        BigInteger rhs = x.modPow(BigInteger.valueOf(3), p)
                          .add(curve.getA().multiply(x))
                          .add(curve.getB())
                          .mod(p);
        BigInteger lhs = y.modPow(BigInteger.TWO, p);
        if (!rhs.equals(lhs)) {
            throw new InvalidKeyException("Point is not on curve");
        }

        // Check the order of the point.
        //
        // Compute nQ (using elliptic curve arithmetic), and verify that
        // nQ is the identity element.
        byte[] order = spec.getOrder().toByteArray();
        ArrayUtil.reverse(order);
        Point product = ops.multiply(key.getW(), order);
        if (!ops.isNeutral(product)) {
            throw new InvalidKeyException("Point has incorrect order");
        }
    }

    @Override
    protected byte[] engineGenerateSecret() throws IllegalStateException {
        if (this.javaImplementation != null) {
            return this.javaImplementation.engineGenerateSecret();
        }
        byte[] secret = new byte[this.secretLen];
        try {
            engineGenerateSecret(secret, 0);
        } catch (ShortBufferException e) {
            /* should not happen */
            throw new InternalError(e);
        }
        return secret;
    }

    @Override
    protected int engineGenerateSecret(byte[] sharedSecret, int offset)
            throws IllegalStateException, ShortBufferException {
        if (this.javaImplementation != null) {
            return this.javaImplementation.engineGenerateSecret(sharedSecret, offset);
        }

        boolean absent;
        if ((offset + this.secretLen) > sharedSecret.length) {
            throw new ShortBufferException("Need " + this.secretLen
                    + " bytes, only " + (sharedSecret.length - offset)
                    + " available");
        }
        if ((this.privateKey == null) || (this.publicKey == null)) {
            throw new IllegalStateException("Not initialized correctly");
        }

        absent = NativeECUtil.putCurveIfAbsent(this.curve, Boolean.TRUE);
        if (absent && nativeCryptTrace) {
            System.err.println(this.curve +
                    " is supported by OpenSSL, using native crypto implementation for generating secret.");
        }

        int ret;
        if (nativeCrypto == null) {
            nativeCrypto = NativeCrypto.getNativeCrypto();
        }
        synchronized (this.privateKey) {
            ret = nativeCrypto.ECDeriveKey(nativePublicKey.pointer, nativePrivateKey.pointer, sharedSecret, offset, this.secretLen);
        }
        if (ret == -1) {
            throw new ProviderException("Could not derive key");
        }
        NativeECUtil.releaseNativeKey(this.nativePublicKey);
        this.nativePublicKey = null;
        this.publicKey = null;
        return this.secretLen;
    }

    @Override
    protected SecretKey engineGenerateSecret(String algorithm)
            throws IllegalStateException, NoSuchAlgorithmException,
            InvalidKeyException {
        if (algorithm == null) {
            throw new NoSuchAlgorithmException("Algorithm must not be null");
        }
        if (!KeyUtil.isSupportedKeyAgreementOutputAlgorithm(algorithm)) {
            throw new NoSuchAlgorithmException(
                    "Unsupported secret key algorithm: " + algorithm);
        }
        byte[] bytes = engineGenerateSecret();
        try {
            return new SecretKeySpec(bytes, algorithm);
        } finally {
            Arrays.fill(bytes, (byte)0);
        }
    }

    /**
     * Initializes the java implementation.
     *
     * @param key the private key
     */
    private void initializeJavaImplementation(Key key) throws InvalidKeyException {
        this.javaImplementation = new ECDHKeyAgreement();
        this.javaImplementation.engineInit(key, null);
    }
}
