/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2018, 2021 All Rights Reserved
 * ===========================================================================
 */

package com.sun.crypto.provider;

import java.util.Arrays;
import java.io.*;
import java.security.*;
import javax.crypto.*;
import com.sun.crypto.provider.AESCrypt;
import sun.security.jca.JCAUtil;
import sun.security.util.ArrayUtil;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.nio.ByteBuffer;
import jdk.crypto.jniprovider.NativeCrypto;

import sun.nio.ch.DirectBuffer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import javax.crypto.spec.GCMParameterSpec;

/**
 * This class represents ciphers in GaloisCounter (GCM) mode.
 *
 * <p>This mode currently should only be used w/ AES cipher.
 * Although no checking is done, caller should only pass AES
 * Cipher to the constructor.
 *
 * <p>NOTE: Unlike other modes, when used for decryption, this class
 * will buffer all processed outputs internally and won't return them
 * until the tag has been successfully verified.
 *
 * @since 1.8
 */
abstract class NativeGaloisCounterMode extends CipherSpi {
    private GCMEngine engine;
    private boolean initialized;

    private byte[] key;
    private boolean encryption = true;

    private static final int DEFAULT_TAG_LEN = 16; // in bytes
    private static final int DEFAULT_IV_LEN = 12; // in bytes

    // In NIST SP 800-38D, GCM input size is limited to be no longer
    // than (2^36 - 32) bytes. Otherwise, the counter will wrap
    // around and lead to a leak of plaintext.
    // However, given the current GCM spec requirement that recovered
    // text can only be returned after successful tag verification,
    // we are bound by limiting the data size to the size limit of
    // java byte array, e.g. Integer.MAX_VALUE, since all data
    // can only be returned by the doFinal(...) call.
    private static final int MAX_BUF_SIZE = Integer.MAX_VALUE;
    private static final byte[] EMPTY_BUF = new byte[0];

    // the embedded block cipher
    SymmetricCipher blockCipher;

    // in bytes; need to convert to bits (default value 128) when needed
    private int tagLenBytes;

    // Key size if the value is passed, in bytes.
    int keySize;

    // Prevent reuse of iv or key
    boolean reInit;
    byte[] lastKey = EMPTY_BUF;
    byte[] lastIv = EMPTY_BUF;

    byte[] iv;
    SecureRandom random;

    private static final NativeCrypto nativeCrypto = NativeCrypto.getNativeCrypto();

    /*
     * Constructor
     */
    NativeGaloisCounterMode(int keySize, SymmetricCipher embeddedCipher) {
        tagLenBytes = DEFAULT_TAG_LEN;
        blockCipher = embeddedCipher;
        this.keySize = keySize;
    }

    /**
     * Initializes the cipher in the specified mode with the given key
     * and iv.
     */
    void init(int opmode, Key key, GCMParameterSpec spec)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        encryption = (opmode == Cipher.ENCRYPT_MODE) ||
            (opmode == Cipher.WRAP_MODE);

        int tagLen = spec.getTLen();
        if ((tagLen < 96) || (tagLen > 128) || ((tagLen & 0x07) != 0)) {
            throw new InvalidAlgorithmParameterException
                ("Unsupported TLen value.  Must be one of " +
                    "{128, 120, 112, 104, 96}");
        }

        byte[] keyValue;
        synchronized (this) {
            tagLenBytes = tagLen >> 3;

            // Check the Key object is valid and the right size
            if (key == null) {
                throw new InvalidKeyException("The key must not be null");
            }
            keyValue = key.getEncoded();
            if (keyValue == null) {
                throw new InvalidKeyException("Key encoding must not be null");
            } else if ((keySize != -1) && (keyValue.length != keySize)) {
                Arrays.fill(keyValue, (byte) 0);
                throw new InvalidKeyException("The key must be " +
                    keySize + " bytes");
            }
            this.key = keyValue.clone();

            // Check for reuse
            if (encryption) {
                if (MessageDigest.isEqual(keyValue, lastKey) &&
                    MessageDigest.isEqual(iv, lastIv)) {
                    Arrays.fill(keyValue, (byte) 0);
                    throw new InvalidAlgorithmParameterException(
                        "Cannot reuse iv for GCM encryption");
                }

                // Both values are already clones
                if (lastKey != null) {
                    Arrays.fill(lastKey, (byte) 0);
                }
                lastKey = keyValue;
                lastIv = iv;
            }
        }
        reInit = false;

        // always encrypt mode for embedded cipher
        try {
            blockCipher.init(false, key.getAlgorithm(), keyValue);
        } finally {
            if (!encryption) {
                Arrays.fill(keyValue, (byte) 0);
            }
        }
    }

    private static void checkDataLength(int processed, int len) {
        if (len < 0) {
            throw new ProviderException("input size cannot be negative");
        }
        if (processed > MAX_BUF_SIZE - len) {
            throw new ProviderException("SunJCE provider only supports " +
                "input size up to " + MAX_BUF_SIZE + " bytes");
        }
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        if (!"GCM".equalsIgnoreCase(mode)) {
            throw new NoSuchAlgorithmException("Mode must be GCM");
        }
    }

    @Override
    protected void engineSetPadding(String padding)
        throws NoSuchPaddingException {
        if (!"NoPadding".equalsIgnoreCase(padding)) {
            throw new NoSuchPaddingException("Padding must be NoPadding");
        }
    }

    @Override
    protected int engineGetBlockSize() {
        return blockCipher.getBlockSize();
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        checkInit();
        return engine.getOutputSize(inputLen, true);
    }

    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        byte[] encoded = key.getEncoded();
        Arrays.fill(encoded, (byte)0);
        if (!AESCrypt.isKeySizeValid(encoded.length)) {
            throw new InvalidKeyException("Invalid key length: " +
                                          encoded.length + " bytes");
        }
        return Math.multiplyExact(encoded.length, 8);
    }

    @Override
    protected synchronized byte[] engineGetIV() {
        if (iv == null) {
            return null;
        }
        return iv.clone();
    }

    /**
     * Create a random 16-byte iv.
     *
     * @param rand a {@code SecureRandom} object.  If {@code null} is
     * provided a new {@code SecureRandom} object will be instantiated.
     *
     * @return a 16-byte array containing the random nonce.
     */
    private static byte[] createIv(SecureRandom rand) {
        byte[] iv = new byte[DEFAULT_IV_LEN];
        if (rand == null) {
            rand = JCAUtil.getDefSecureRandom();
        }
        rand.nextBytes(iv);
        return iv;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        GCMParameterSpec spec;
        synchronized (this) {
            spec = new GCMParameterSpec(tagLenBytes * 8,
                (iv == null) ? createIv(random) : iv.clone());
        }
        try {
            AlgorithmParameters params =
                AlgorithmParameters.getInstance("GCM",
                    SunJCE.getInstance());
            params.init(spec);
            return params;
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random)
        throws InvalidKeyException {
        engine = null;
        if (opmode == Cipher.DECRYPT_MODE || opmode == Cipher.UNWRAP_MODE) {
            throw new InvalidKeyException("No GCMParameterSpec specified");
        }
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            // never happen
        }
    }

    @Override
    protected void engineInit(int opmode, Key key,
        AlgorithmParameterSpec params, SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        GCMParameterSpec spec;
        this.random = random;
        engine = null;
        synchronized (this) {
            if (params == null) {
                iv = createIv(random);
                spec = new GCMParameterSpec(DEFAULT_TAG_LEN * 8, iv);
            } else {
                if (!(params instanceof GCMParameterSpec)) {
                    throw new InvalidAlgorithmParameterException(
                        "AlgorithmParameterSpec not of GCMParameterSpec");
                }
                spec = (GCMParameterSpec)params;
                iv = spec.getIV();
                if (iv == null) {
                    throw new InvalidAlgorithmParameterException("IV is null");
                }
                if (iv.length == 0) {
                    throw new InvalidAlgorithmParameterException("IV is empty");
                }
            }
        }
        init(opmode, key, spec);
        initialized = true;
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
        SecureRandom random) throws InvalidKeyException,
        InvalidAlgorithmParameterException {
        GCMParameterSpec spec = null;
        engine = null;
        if (params != null) {
            try {
                spec = params.getParameterSpec(GCMParameterSpec.class);
            } catch (InvalidParameterSpecException e) {
                throw new InvalidAlgorithmParameterException(e);
            }
        }
        engineInit(opmode, key, spec, random);
    }

    void checkInit() {
        if (!initialized) {
            throw new IllegalStateException("Operation not initialized.");
        }
        if (engine == null) {
            if (encryption) {
                engine = new GCMEncrypt(blockCipher);
            } else {
                engine = new GCMDecrypt(blockCipher);
            }
        }
    }

    void checkReInit() {
        if (reInit) {
            throw new IllegalStateException(
                "Must use either different key or " + " iv for GCM encryption");
        }
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        checkInit();
        ArrayUtil.nullAndBoundsCheck(input, inputOffset, inputLen);
        return engine.doUpdate(input, inputOffset, inputLen);
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen,
        byte[] output, int outputOffset) throws ShortBufferException {
        checkInit();
        ArrayUtil.nullAndBoundsCheck(input, inputOffset, inputLen);
        ArrayUtil.nullAndBoundsCheck(output, outputOffset,
                output.length - outputOffset);
        int len = engine.getOutputSize(inputLen, false);
        if (len > (output.length - outputOffset)) {
            throw new ShortBufferException("Output buffer too small, must be " +
                "at least " + len + " bytes long");
        }
        return engine.doUpdate(input, inputOffset, inputLen, output,
            outputOffset);
    }

    @Override
    protected int engineUpdate(ByteBuffer src, ByteBuffer dst)
        throws ShortBufferException {
        checkInit();
        int len = engine.getOutputSize(src.remaining(), false);
        if (len > dst.remaining()) {
            throw new ShortBufferException(
                "Output buffer must be at least " + len + " bytes long");
        }
        return engine.doUpdate(src, dst);
    }

    @Override
    protected void engineUpdateAAD(byte[] src, int offset, int len) {
        checkInit();
        engine.updateAAD(src, offset, len);
    }

    @Override
    protected void engineUpdateAAD(ByteBuffer src) {
        checkInit();
        if (src.hasArray()) {
            int pos = src.position();
            int len = src.remaining();
            engine.updateAAD(src.array(), src.arrayOffset() + pos, len);
            src.position(pos + len);
        } else {
            byte[] aad = new byte[src.remaining()];
            src.get(aad);
            engine.updateAAD(aad, 0, aad.length);
        }
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset,
        int inputLen) throws IllegalBlockSizeException, BadPaddingException {
        if (input == null) {
            input = EMPTY_BUF;
        }
        try {
            ArrayUtil.nullAndBoundsCheck(input, inputOffset, inputLen);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalBlockSizeException("input array invalid");
        }
        checkInit();
        byte[] output = new byte[engine.getOutputSize(inputLen, true)];
        try {
            engine.doFinal(input, inputOffset, inputLen, output, 0);
        } catch (ShortBufferException e) {
            throw new ProviderException(e);
        } finally {
            // Release crypto engine
            engine = null;
        }
        return output;
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen,
        byte[] output, int outputOffset) throws ShortBufferException,
        IllegalBlockSizeException, BadPaddingException {
        if (input == null) {
            input = EMPTY_BUF;
        }
        try {
            ArrayUtil.nullAndBoundsCheck(input, inputOffset, inputLen);
        } catch (ArrayIndexOutOfBoundsException e) {
            // Release crypto engine
            engine = null;
            throw new IllegalBlockSizeException("input array invalid");
        }
        checkInit();
        int len = engine.doFinal(input, inputOffset, inputLen, output,
            outputOffset);
        // Release crypto engine
        engine = null;
        return len;
    }

    @Override
    protected int engineDoFinal(ByteBuffer src, ByteBuffer dst)
        throws ShortBufferException, IllegalBlockSizeException,
        BadPaddingException {
        checkInit();
        int len = engine.doFinal(src, dst);
        // Release crypto engine
        engine = null;
        return len;
    }

    @Override
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException,
        InvalidKeyException {
        byte[] encodedKey = null;
        checkInit();
        try {
            encodedKey = key.getEncoded();
            if ((encodedKey == null) || (encodedKey.length == 0)) {
                throw new InvalidKeyException(
                    "Cannot get an encoding of the key to be wrapped");
            }
            return engineDoFinal(encodedKey, 0, encodedKey.length);
        } catch (BadPaddingException e) {
            // should never happen
        } finally {
            // Release crypto engine
            engine = null;
            if (encodedKey != null) {
                Arrays.fill(encodedKey, (byte) 0);
            }
        }
        return null;
    }

    @Override
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
        int wrappedKeyType) throws InvalidKeyException,
        NoSuchAlgorithmException {
        checkInit();
        byte[] encodedKey;
        try {
            encodedKey = engineDoFinal(wrappedKey, 0,
                wrappedKey.length);
        } catch (BadPaddingException ePadding) {
            throw new InvalidKeyException(
                "The wrapped key is not padded correctly");
        } catch (IllegalBlockSizeException eBlockSize) {
            throw new InvalidKeyException(
                "The wrapped key does not have the correct length");
        }
        try {
            return ConstructKeys.constructKey(encodedKey, wrappedKeyAlgorithm,
                wrappedKeyType);
        } finally {
            Arrays.fill(encodedKey, (byte)0);
        }
    }

    /**
     * Abstract class for GCMEncrypt and GCMDecrypt internal context objects
     */
    abstract class GCMEngine {
        // these following 2 fields can only be initialized after init() is
        // called, e.g. after cipher key k is set, and STAY UNCHANGED
        byte[] subkeyH;
        byte[] preCounterBlock;

        // Block size of the algorithm
        final int blockSize;
        // length of total data, i.e. len(C)
        int processed;
        // buffer for AAD data; if null, meaning update has been called
        ByteArrayOutputStream aadBuffer;
        // buffer data for crypto operation
        ByteArrayOutputStream ibuffer;

        GCMEngine(SymmetricCipher blockCipher) {
            blockSize = blockCipher.getBlockSize();
            aadBuffer = new ByteArrayOutputStream();
        }

        /**
         * Get output buffer size
         * @param inLen Contains the length of the input data and buffered data.
         * @param isFinal true if this is a doFinal operation
         * @return If it's an update operation, inLen must blockSize
         *         divisible.  If it's a final operation, output will
         *         include the tag.
         */
        abstract int getOutputSize(int inLen, boolean isFinal);

        // Update operations
        abstract byte[] doUpdate(byte[] in, int inOff, int inLen);
        abstract int doUpdate(byte[] in, int inOff, int inLen, byte[] out,
            int outOff) throws ShortBufferException;
        abstract int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException;

        // Final operations
        abstract int doFinal(byte[] in, int inOff, int inLen, byte[] out,
            int outOff) throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException;
        abstract int doFinal(ByteBuffer src, ByteBuffer dst)
            throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException;

        // Initialize internal data buffer, if not already.
        synchronized void initBuffer(int len) {
            if (ibuffer == null) {
                ibuffer = new ByteArrayOutputStream(len);
            }
        }

        // Helper method for getting ibuffer size
        synchronized int getBufferedLength() {
            return (ibuffer == null) ? 0 : ibuffer.size();
        }

        /**
         * Gets the byte array behind a buffer.
         * Tries to use ByteBuffer.array(). If this is not available, the function uses ByteBuffer.get()
         *
         * @param src the buffer whose byte[] is needed
         * @return the byte array with the buffer's content
         */
        protected byte[] getBbArray(ByteBuffer src) {
            byte[] arr;
            if (src.hasArray()) {
                arr = src.array();
            } else {
                ByteBuffer cpy = src.duplicate();
                arr = new byte[cpy.remaining()];
                if (arr.length > 0) {
                    cpy.get(arr, 0, arr.length);
                }
            }
            return arr;
        }

        /**
         * Continues a multi-part update of the Additional Authentication
         * Data (AAD), using a subset of the provided buffer. If this
         * cipher is operating in either GCM or CCM mode, all AAD must be
         * supplied before beginning operations on the ciphertext (via the
         * {@code update} and {@code doFinal} methods).
         * <p>
         * NOTE: Given most modes do not accept AAD, default impl for this
         * method throws IllegalStateException.
         *
         * @param src the buffer containing the AAD
         * @param offset the offset in {@code src} where the AAD input starts
         * @param len the number of AAD bytes
         *
         * @throws IllegalStateException if this cipher is in a wrong state
         * (e.g., has not been initialized), does not accept AAD, or if
         * operating in either GCM or CCM mode and one of the {@code update}
         * methods has already been called for the active
         * encryption/decryption operation
         * @throws UnsupportedOperationException if this method
         * has not been overridden by an implementation
         *
         * @since 1.8
         */
        void updateAAD(byte[] src, int offset, int len) {
            if (encryption) {
                checkReInit();
            }
            synchronized (this) {
                if (aadBuffer != null) {
                    aadBuffer.write(src, offset, len);
                } else {
                    // update has already been called
                    throw new IllegalStateException
                        ("Update has been called; no more AAD data");
                }
            }
        }
    }

    /**
     * Encryption Engine object
     */
    final class GCMEncrypt extends GCMEngine {

        GCMEncrypt(SymmetricCipher blockCipher) {
            super(blockCipher);
        }

        @Override
        public int getOutputSize(int inLen, boolean isFinal) {
            int len = getBufferedLength();
            if (isFinal) {
                synchronized (this) {
                    return len + inLen + tagLenBytes;
                }
            } else {
                len += inLen;
                return len - (len % blockCipher.getBlockSize());
            }
        }

        /*
        * This method is to insert the remainder of the buffer into ibuffer before
        * a doFinal(ByteBuffer, ByteBuffer) operation
        */
        @Override
        byte[] doUpdate(byte[] in, int inOff, int inLen) {
            checkReInit();
            try {
                doUpdate(in, inOff, inLen, null, 0);
            } catch (ShortBufferException e) {
                // update encryption has no output
            }
            return new byte[0];
        }

        /**
         * Performs encryption operation.
         *
         * <p>The input plain text <code>in</code>, starting at <code>inOff</code>
         * and ending at <code>(inOff + inLen - 1)</code>, is encrypted. The result
         * is stored in <code>out</code>, starting at <code>outOfs</code>.
         *
         * @param in the buffer with the input data to be encrypted
         * @param inOfs the offset in <code>in</code>
         * @param inLen the length of the input data
         * @param out the buffer for the result
         * @param outOfs the offset in <code>out</code>
         * @exception ProviderException if <code>inLen</code> is not
         * a multiple of the block size
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
            checkReInit();
            int bLen = getBufferedLength();
            checkDataLength(bLen, inLen);
            if (inLen > 0) {
                // store internally until doFinal is called because
                // spec mentioned that only return recovered data after tag
                // is successfully verified
                initBuffer(inLen);
                synchronized (this) {
                    ibuffer.write(in, inOfs, inLen);
                }
            }
            return 0;
        }

        @Override
        public int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException {
            checkReInit();
            int bLen = getBufferedLength();
            checkDataLength(bLen, src.remaining());
            if (src.remaining() > 0) {
                // store internally until doFinal is called because
                // spec mentioned that only return recovered data after tag
                // is successfully verified
                initBuffer(src.remaining());
                byte[] b = new byte[src.remaining()];
                src.get(b);
                // remainder offset is based on original buffer length
                synchronized (this) {
                    try {
                        ibuffer.write(b);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return 0;
        }

        /**
         * Performs encryption operation for the last time.
         *
         * @param in the input buffer with the data to be encrypted
         * @param inOfs the offset in <code>in</code>
         * @param inLen the length of the input data
         * @param out the buffer for the encryption result
         * @param outOfs the offset in <code>out</code>
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws IllegalBlockSizeException, ShortBufferException {
            checkReInit();
            int localTagLenBytes;
            int ret;

            synchronized (this) {
                localTagLenBytes = tagLenBytes;

                if (inLen > (MAX_BUF_SIZE - localTagLenBytes)) {
                    throw new ShortBufferException
                        ("Can't fit both data and tag into one buffer");
                }

                if ((out.length - outOfs) < (inLen + localTagLenBytes)) {
                    throw new ShortBufferException("Output buffer too small");
                }

                int bLen = getBufferedLength();
                checkDataLength(bLen, inLen);
                initBuffer(inLen);

                if (inLen > 0) {
                    ibuffer.write(in, inOfs, inLen);
                }

                // refresh 'in' to all buffered-up bytes
                in = ibuffer.toByteArray();
                inOfs = 0;
                inLen = in.length;
                ibuffer.reset();
                byte[] aad = ((aadBuffer == null) || (aadBuffer.size() == 0)) ? EMPTY_BUF : aadBuffer.toByteArray();
                aadBuffer = null;

                ret = nativeCrypto.GCMEncrypt(key, key.length,
                        iv, iv.length,
                        in, inOfs, inLen,
                        out, outOfs,
                        aad, aad.length, localTagLenBytes);
            }
            if (ret == -1) {
                throw new ProviderException("Error in Native GaloisCounterMode");
            }

            reInit = true;
            return inLen + localTagLenBytes;
        }

        /**
         * Performs encryption operation for the last time.
         *
         * @param src the input buffer with the data to be encrypted
         * @param dst the output buffer with encrypted data
         * @return the number of bytes placed into the <code>dst</code> buffer
         */
        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst) throws
            IllegalBlockSizeException, ShortBufferException {
            checkReInit();
            // Get array from source
            byte[] src_arr = getBbArray(src);
            int src_ofs = src.hasArray() ? (src.position() + src.arrayOffset()) : 0;

            // Get array from destination
            byte[] dst_arr;
            int dst_offset;
            if (dst.hasArray()) {
                dst_arr = dst.array();
                dst_offset = dst.position() + dst.arrayOffset();
            } else {
                dst_arr = new byte[dst.remaining()];
                dst_offset = 0;
            }

            int len = doFinal(src_arr, src_ofs, src.remaining(), dst_arr, dst_offset);

            // Advance source buffer position
            src.position(src.limit());

            // Update destination buffer
            if (dst.hasArray()) {
                dst.position(dst.position() + len);
            } else {
                dst.put(dst_arr, 0, len);
            }

            return len;
        }
    }

    /**
     * Decryption Engine object
     */
    final class GCMDecrypt extends GCMEngine {

        GCMDecrypt(SymmetricCipher blockCipher) {
            super(blockCipher);
        }

        @Override
        public int getOutputSize(int inLen, boolean isFinal) {
            if (!isFinal) {
                return 0;
            }
            synchronized (this) {
                return Math.max(inLen + getBufferedLength() - tagLenBytes, 0);
            }
        }

        // Put the input data into the ibuffer
        @Override
        byte[] doUpdate(byte[] in, int inOff, int inLen) {
            try {
                doUpdate(in, inOff, inLen, null, 0);
            } catch (ShortBufferException e) {
                // update decryption has no output
            }
            return new byte[0];
        }

        /**
         * Performs decryption operation.
         *
         * <p>The input cipher text <code>in</code>, starting at
         * <code>inOfs</code> and ending at <code>(inOfs + len - 1)</code>,
         * is decrypted. The result is stored in <code>out</code>, starting at
         * <code>outOfs</code>.
         *
         * @param in the buffer with the input data to be decrypted
         * @param inOfs the offset in <code>in</code>
         * @param len the length of the input data
         * @param out the buffer for the result
         * @param outOfs the offset in <code>out</code>
         * @exception ProviderException if <code>len</code> is not
         * a multiple of the block size
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
            int bLen = getBufferedLength();
            checkDataLength(bLen, inLen);
            if (inLen > 0) {
                // store internally until doFinal is called because
                // spec mentioned that only return recovered data after tag
                // is successfully verified
                initBuffer(inLen);
                synchronized (this) {
                    ibuffer.write(in, inOfs, inLen);
                }
            }
            return 0;
        }

        @Override
        public int doUpdate(ByteBuffer src, ByteBuffer dst)
            throws ShortBufferException {
            int bLen = getBufferedLength();
            checkDataLength(bLen, src.remaining());
            if (src.remaining() > 0) {
                // store internally until doFinal is called because
                // spec mentioned that only return recovered data after tag
                // is successfully verified
                initBuffer(src.remaining());
                byte[] b = new byte[src.remaining()];
                src.get(b);
                // remainder offset is based on original buffer length
                synchronized (this) {
                    try {
                        ibuffer.write(b);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            return 0;
        }

        /**
         * Performs decryption operation for the last time.
         *
         * <p>NOTE: For cipher feedback modes which does not perform
         * special handling for the last few blocks, this is essentially
         * the same as <code>encrypt(...)</code>. Given most modes do
         * not do special handling, the default impl for this method is
         * to simply call <code>decrypt(...)</code>.
         *
         * @param in the input buffer with the data to be decrypted
         * @param inOfs the offset in <code>cipher</code>
         * @param len the length of the input data
         * @param out the buffer for the decryption result
         * @param outOfs the offset in <code>plain</code>
         * @return the number of bytes placed into the <code>out</code> buffer
         */
        @Override
        public int doFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException {
            if (inLen < 0) {
                throw new ProviderException("Input length is negative");
            }
            int ret;
            synchronized (this) {
                int bLen = getBufferedLength();
                if (inLen < (tagLenBytes - bLen)) {
                    throw new AEADBadTagException("Input too short - need tag");
                }
                if (inLen > (MAX_BUF_SIZE - bLen)) {
                    throw new ProviderException("SunJCE provider only supports "
                        + "a positive input size up to " + MAX_BUF_SIZE + " bytes");
                }
                if ((out.length - outOfs) < (inLen + bLen - tagLenBytes)) {
                    throw new ShortBufferException("Output buffer too small");
                }
                byte[] aad = ((aadBuffer == null) || (aadBuffer.size() == 0)) ?
                    EMPTY_BUF : aadBuffer.toByteArray();
                aadBuffer = null;
                initBuffer(inLen);
                if (inLen > 0) {
                    ibuffer.write(in, inOfs, inLen);
                }
                // refresh 'in' to all buffered-up bytes
                in = ibuffer.toByteArray();
                inOfs = 0;
                inLen = in.length;
                ibuffer.reset();
                ret = nativeCrypto.GCMDecrypt(key, key.length,
                        iv, iv.length,
                        in, inOfs, inLen,
                        out, outOfs,
                        aad, aad.length, tagLenBytes);
            }
            if (ret == -2) {
                throw new AEADBadTagException("Tag mismatch!");
            } else if (ret == -1) {
                throw new ProviderException("Error in Native GaloisCounterMode");
            }
            return ret;
        }

        /**
         * Performs decryption operation for the last time.
         *
         * @param src the input buffer with the data to be decrypted
         * @param dst the output buffer with the decrypted data
         * @return the number of bytes placed into the <code>dst</code> buffer
         */
        @Override
        public int doFinal(ByteBuffer src, ByteBuffer dst)
            throws IllegalBlockSizeException, AEADBadTagException,
            ShortBufferException {
            // Get array from source
            byte[] src_arr = getBbArray(src);
            int src_ofs = src.hasArray() ? (src.position() + src.arrayOffset()) : 0;
            // Get array from destination
            byte[] dst_arr;
            int dst_offset;
            if (dst.hasArray()) {
                dst_arr = dst.array();
                dst_offset = dst.position() + dst.arrayOffset();
            } else {
                dst_arr = new byte[dst.remaining()];
                dst_offset = 0;
            }
            int len = doFinal(src_arr, src_ofs, src.remaining(), dst_arr, dst_offset);
            // Advance source buffer position
            src.position(src.limit());
            // Update destination buffer
            if (dst.hasArray()) {
                dst.position(dst.position() + len);
            } else {
                dst.put(dst_arr, 0, len);
            }
            return len;
        }
    }

    public static final class AESGCM extends NativeGaloisCounterMode {
        public AESGCM() {
            super(-1, new AESCrypt());
        }
    }

    public static final class AES128 extends NativeGaloisCounterMode {
        public AES128() {
            super(16, new AESCrypt());
        }
    }

    public static final class AES192 extends NativeGaloisCounterMode {
        public AES192() {
            super(24, new AESCrypt());
        }
    }

    public static final class AES256 extends NativeGaloisCounterMode {
        public AES256() {
            super(32, new AESCrypt());
        }
    }
}
