/*
 * Copyright (c) 2006, 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2024, 2024 All Rights Reserved
 * ===========================================================================
 */

/*
 * @test
 * @bug 6405536 8042967
 * @summary basic test of SHA1withECDSA and NONEwithECDSA signing/verifying
 * @author Andreas Sterbenz
 * @library /test/lib ..
 * @library ../../../../java/security/testlibrary
 * @key randomness
 * @modules jdk.crypto.cryptoki
 * @run main/othervm TestECDSA
 * @run main/othervm -Djava.security.manager=allow TestECDSA sm policy
 */

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

public class TestECDSA extends PKCS11Test {

    // values of the keys we use for the tests

    // keypair using NIST P-192
    private final static String pub192 =
"30:49:30:13:06:07:2a:86:48:ce:3d:02:01:06:08:2a:86:48:ce:3d:03:01:01:03:32:00:04:ee:b4:7f:60:3a:25:6a:0c:3c:86:d9:a0:62:be:f6:11:25:42:0e:19:fa:f3:1a:df:0c:98:b4:f8:b3:8f:f5:c1:82:74:e5:e7:71:d6:f9:d0:26:3b:2d:53:a6:37:fc:ab";
    private final static String priv192 =
"30:39:02:01:00:30:13:06:07:2a:86:48:ce:3d:02:01:06:08:2a:86:48:ce:3d:03:01:01:04:1f:30:1d:02:01:01:04:18:2c:eb:c2:9e:96:de:df:70:d4:a6:33:43:9b:4c:59:4a:6e:a6:f4:5b:6b:a6:b7:6a";

    // keypair using NIST B-163
    private final static String pub163 =
"30:40:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:0f:03:2c:00:04:04:af:bc:e6:a1:d3:1a:74:76:dc:51:d6:8d:39:2f:b6:68:22:b3:0f:78:05:79:f7:5d:65:7d:42:9b:de:51:85:0a:a7:b8:89:79:0a:f2:c7:35:0e";
    private final static String priv163 =
"30:33:02:01:00:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:0f:04:1c:30:1a:02:01:01:04:15:02:0c:07:60:e6:0a:25:ff:5a:19:c8:35:9d:4b:79:97:06:87:9b:a1:3d";

    // keypair using NIST P-521
    private final static String pub521 =
"30:81:9b:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:23:03:81:86:00:04:01:6c:e1:03:96:a4:ad:1e:18:b0:c3:ab:63:e6:6a:f6:e5:33:e8:75:e5:7e:33:ce:fd:3a:48:03:08:5a:32:04:f2:f7:00:46:e5:7e:c3:92:bb:bc:5e:c7:d2:e6:01:f3:17:d9:31:2d:07:fc:a1:93:57:28:b8:3a:7f:46:00:f9:bc:b2:01:35:45:9f:3f:0c:bf:6f:3e:29:a7:92:be:c0:83:c0:90:1c:fe:3c:2f:fd:2e:d6:12:0d:45:d3:d3:19:c2:5a:5b:26:37:2d:8e:ba:1c:9e:fb:3a:b6:02:7a:1c:45:ec:72:c8:4b:7d:1f:65:11:78:43:8f:70:92:27:24:ba:c4:af:e5";
    private final static String priv521 =
"30:60:02:01:00:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:23:04:49:30:47:02:01:01:04:42:01:e7:a7:5d:13:f8:4d:5a:5d:64:10:e6:a1:e0:01:a3:b0:92:e0:72:55:f5:87:62:7e:30:f2:b1:f2:a0:1e:ef:30:65:9c:88:16:53:71:2d:05:a3:d8:4d:bc:c9:50:84:2c:d1:b5:9d:6e:84:57:60:ee:46:a1:26:f9:8b:2c:d5:86:4d";

    // keypair using NIST K-571
    private final static String pub571 =
"30:81:a7:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:26:03:81:92:00:04:00:93:a7:c3:d7:90:8f:e5:3c:37:5a:8a:88:d9:b0:04:d7:5d:59:7e:83:42:b6:ef:c2:9c:72:56:3c:9f:28:24:7e:46:95:a8:cd:2c:06:67:a3:81:43:e9:1f:61:b4:66:7d:e6:91:ec:89:5c:4d:ed:bc:c0:8b:33:44:64:3f:5b:44:29:42:e8:a6:8a:e9:47:05:44:69:ca:f0:76:81:d5:e9:e1:9b:c1:31:73:53:69:6d:99:1f:05:bd:b7:62:b6:99:cf:73:c5:24:0e:6c:9f:d3:00:f3:21:58:33:be:a1:de:2e:fc:9e:b1:2b:89:4e:bb:e1:75:da:8c:c1:a1:d2:19:52:5b:57:41:83:11:e3:70:61:63:68:6e:b5:c2:91";
    private final static String priv571 =
"30:65:02:01:00:30:10:06:07:2a:86:48:ce:3d:02:01:06:05:2b:81:04:00:26:04:4e:30:4c:02:01:01:04:47:cb:b0:84:c9:5e:d5:bb:d1:27:6b:8e:36:51:5d:ed:8d:0f:69:f4:b0:34:c2:4f:e8:e5:a5:3a:a9:38:52:ca:b6:b2:c7:04:8b:09:b7:ac:68:11:00:22:7a:d7:4b:11:77:0f:3f:ba:72:e5:8b:a7:4d:82:8e:a7:d9:55:cf:60:9c:23:f4:a7:22:47:b8:3e";

    // keypair using brainpoolP512r1
    private final static String pubBrainpoolP512 =
"30:81:9b:30:14:06:07:2a:86:48:ce:3d:02:01:06:09:2b:24:03:03:02:08:01:01:0d:03:81:82:00:04:3c:ae:c6:f8:c9:71:51:59:d1:7d:bd:0d:b9:76:23:25:df:2c:4e:b4:b1:6e:22:79:5b:97:1b:60:0b:3e:87:f3:9f:af:44:84:55:c3:64:6b:1e:dd:4d:12:27:81:31:07:21:4e:b0:a5:73:3c:91:11:8f:ad:f4:74:12:fd:dc:74:76:c6:44:b2:57:d6:c4:ed:99:71:c8:46:6c:b7:f7:a7:ef:36:5c:7d:6c:4a:a3:6f:f9:4b:0a:ea:34:58:80:05:ac:15:ae:82:84:f2:f3:c6:85:2c:5a:ae:45:4b:64:4c:4f:ef:50:a5:6b:84:fd:52:11:08:09:09:fb:b5:1a:5b";
    private final static String privBrainpoolP512 =
"30:81:ec:02:01:00:30:14:06:07:2a:86:48:ce:3d:02:01:06:09:2b:24:03:03:02:08:01:01:0d:04:81:d0:30:81:cd:02:01:01:04:40:1d:dc:04:b7:49:a9:2f:45:96:cb:d9:a0:39:ba:5a:af:a9:1b:7e:a3:81:4c:fa:be:b7:a9:94:96:5d:7c:54:94:03:5d:6a:07:d1:3d:6e:ca:00:80:9d:0a:90:2c:69:ac:86:5b:d7:13:f8:f2:6c:c6:97:6f:e5:f5:cc:65:9e:f4:a1:81:85:03:81:82:00:04:3c:ae:c6:f8:c9:71:51:59:d1:7d:bd:0d:b9:76:23:25:df:2c:4e:b4:b1:6e:22:79:5b:97:1b:60:0b:3e:87:f3:9f:af:44:84:55:c3:64:6b:1e:dd:4d:12:27:81:31:07:21:4e:b0:a5:73:3c:91:11:8f:ad:f4:74:12:fd:dc:74:76:c6:44:b2:57:d6:c4:ed:99:71:c8:46:6c:b7:f7:a7:ef:36:5c:7d:6c:4a:a3:6f:f9:4b:0a:ea:34:58:80:05:ac:15:ae:82:84:f2:f3:c6:85:2c:5a:ae:45:4b:64:4c:4f:ef:50:a5:6b:84:fd:52:11:08:09:09:fb:b5:1a:5b";

    // data for test 1, original and SHA-1 hashed
    private final static byte[] data1Raw = b("0102030405060708090a0b0c0d0e0f10111213");
    private final static byte[] data1SHA = b("00:e2:5f:c9:1c:8f:d6:8c:6a:dc:c6:bd:f0:46:60:5e:a2:cd:8d:ad");

    // valid signatures of data1.
    private final static byte[] sig192 = b("30:35:02:19:00:91:ba:19:b2:01:da:ce:77:ed:08:6d:70:77:84:25:46:9f:56:a0:40:9a:04:e6:1b:02:18:14:7e:cd:a5:8a:3b:25:e9:f8:c3:20:9b:a9:90:5a:ca:91:5d:60:5e:a8:2f:3e:a4");
    private final static byte[] sig163 = b("30:2d:02:15:02:8d:aa:95:06:f4:4f:fa:44:59:ec:4b:cb:86:59:8c:1f:25:36:64:f5:02:14:6b:d1:ea:82:ed:0c:2a:19:a1:c5:fa:d6:05:78:4b:eb:bf:83:d5:fa");
    private final static byte[] sig521 = b("30:81:87:02:42:01:32:a5:be:dd:fb:c3:07:66:01:48:0a:12:dd:ae:e7:4d:cf:c2:69:ba:37:bc:fb:47:f3:5b:0f:9e:80:2c:c4:c4:40:6f:82:a1:25:39:65:4f:37:9c:b2:59:e0:4c:d6:a2:63:27:b4:fd:fd:ca:72:c8:de:c9:38:8b:02:87:bf:13:d8:02:41:0b:03:0f:3f:f9:cc:93:cb:f5:30:4d:d2:23:f3:cb:3d:b8:ee:8b:76:96:b9:4b:91:2e:b3:8e:26:47:a9:56:89:01:3a:5e:92:79:8f:00:f0:1c:a9:32:f7:70:e2:18:71:35:2c:4d:b7:68:84:2f:56:49:86:eb:96:5d:82:31:a2:de");
    private final static byte[] sig571 = b("30:81:94:02:48:01:4b:81:77:93:cf:bc:98:26:4c:0d:e2:18:f0:d5:b0:bd:b0:a4:a3:b3:8e:1d:3f:7b:21:5d:65:08:42:f7:e6:7e:87:a0:a9:62:9a:79:b0:9d:d6:d6:f0:10:3b:7c:54:aa:cd:f0:d0:5e:5b:f8:f4:36:ec:64:cf:b4:e0:4e:03:db:12:96:e2:25:0c:3b:01:02:48:01:0d:9e:1d:3b:bf:7d:c6:e1:ea:54:92:c4:6b:95:bb:5b:c9:2b:ea:f2:e6:bf:8d:b2:4f:c4:0e:12:f9:35:70:a3:ed:49:f1:11:97:07:a0:05:16:f0:f5:01:8d:15:53:4d:df:51:a0:cf:bc:f0:9f:01:99:e5:2e:e4:9d:02:05:0e:7f:fa:b5:c3:20:eb:5e");
    private final static byte[] sigBrainpool512 = b("30:81:85:02:40:05:92:EC:9C:7B:60:30:0F:54:82:6B:A1:94:CF:16:20:C5:00:08:2F:C6:99:FD:4A:53:4D:EB:B8:74:15:A1:24:08:DE:F1:8D:70:9C:F3:2C:63:CE:37:B6:21:12:5A:82:60:7A:8F:A2:1C:DE:22:DD:5D:D9:77:ED:08:80:D0:6C:02:41:00:A8:DB:47:9B:53:FA:4B:B0:4D:A1:EE:C7:AE:9D:FB:CE:82:4E:8D:C4:32:A4:8A:C1:8A:31:FD:F3:D4:D8:2F:0D:5F:91:C6:A7:E7:9C:3C:2E:B3:22:EF:CB:77:DE:AC:3F:C5:41:01:06:D8:04:46:A1:16:88:5D:5B:C2:38:47:AC");

    // data for test 2 (invalid signatures)
    private final static byte[] data2Raw = {};
    private final static byte[] data2SHA = b("da:39:a3:ee:5e:6b:4b:0d:32:55:bf:ef:95:60:18:90:af:d8:07:09");

    private static void verify(Provider provider, String alg, PublicKey key,
            byte[] data, byte[] sig, boolean result) throws Exception {
        Signature s = Signature.getInstance(alg, provider);
        s.initVerify(key);
        boolean r;
        s.update(data);
        r = s.verify(sig);
        if (r != result) {
            throw new Exception("Result mismatch, actual: " + r);
        }
        s.update(data);
        r = s.verify(sig);
        if (r != result) {
            throw new Exception("Result mismatch, actual: " + r);
        }
        System.out.println("Passed");
    }

    private static void sign(Provider provider, String alg, PrivateKey key, byte[] data) throws Exception {
        Signature s = Signature.getInstance(alg, provider);
        s.initSign(key);
        s.update(data);
        byte[] sig = s.sign();
        System.out.println(toString(sig));
    }

    public static void main(String[] args) throws Exception {
        main(new TestECDSA(), args);
    }

    @Override
    protected boolean skipTest(Provider provider) {
        if (provider.getService("Signature", "SHA1withECDSA") == null) {
            System.out.println("ECDSA not supported, skipping");
            return true;
        }

        if (isBadNSSVersion(provider)) {
            return true;
        }

        return false;
    }

    @Override
    public void main(Provider provider) throws Exception {
        long start = System.currentTimeMillis();

        /*
         * PKCS11Test.main will remove this provider if needed
         */
        Providers.setAt(provider, 1);

        if (false) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", provider);
            kpg.initialize(571);
            KeyPair kp = kpg.generateKeyPair();
            PrivateKey priv = kp.getPrivate();
            ECPublicKey pub = (ECPublicKey)kp.getPublic();
            System.out.println("Keys for " + pub.getParams());
            System.out.println("public key:");
            System.out.println(toString(pub.getEncoded()));
            System.out.println("private key:");
            System.out.println(toString(priv.getEncoded()));
            return;
        }

        if (getSupportedECParameterSpec("secp192r1", provider).isPresent()) {
            test(provider, pub192, priv192, sig192);
        }
        if (getSupportedECParameterSpec("sect163r1", provider).isPresent()) {
            test(provider, pub163, priv163, sig163);
        }
        if (getSupportedECParameterSpec("sect571r1", provider).isPresent()) {
            test(provider, pub571, priv571, sig571);
        }
        test(provider, pub521, priv521, sig521);

        // This test is known to be executed in two ways:
        // 1. Direct execution of this test for testing sun.security.pkcs11
        // functionality. Skip brainpoolP512r1 tests in this case since the PKCS11
        // provider does not support them.
        // 2. Running the testcase in sun/security/ec. Expect brainpoolP512r1
        // curve to be present and execute the brainpoolP512r1 specific test.
        if (provider.getName().equalsIgnoreCase("SunEC")) {
            System.out.println("Running brainpool curve tests with SunEC provider.");
            test(provider, pubBrainpoolP512, privBrainpoolP512, sigBrainpool512);
         }

        long stop = System.currentTimeMillis();
        System.out.println("All tests passed (" + (stop - start) + " ms).");
    }

    private void test(Provider provider, String pub, String priv, byte[] sig) throws Exception {

        KeyFactory kf = KeyFactory.getInstance("EC", provider);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(parse(pub));
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(parse(priv));

        PrivateKey privateKey = kf.generatePrivate(privSpec);
        PublicKey publicKey = kf.generatePublic(pubSpec);

        if (false) {
            sign(provider, "SHA1withECDSA", privateKey, data1Raw);
//          sign(provider, "NONEwithECDSA", privateKey, data1SHA);
            return;
        }

        // verify known-good and known-bad signatures using SHA1withECDSA and NONEwithECDSA
        verify(provider, "SHA1withECDSA", publicKey, data1Raw, sig, true);
        verify(provider, "SHA1withECDSA", publicKey, data2Raw, sig, false);

        verify(provider, "NONEwithECDSA", publicKey, data1SHA, sig, true);
        verify(provider, "NONEwithECDSA", publicKey, data2SHA, sig, false);

        System.out.println("Testing with default signature format: ASN.1");
        testSigning(provider, privateKey, publicKey, false);

        System.out.println("Testing with IEEE P1363 signature format");
        testSigning(provider, privateKey, publicKey, true);
    }

    private void testSigning(Provider provider,
                             PrivateKey privateKey,
                             PublicKey publicKey,
                             boolean p1363Format) throws Exception {
        byte[] data = new byte[2048];
        new Random().nextBytes(data);

        // sign random data using SHA1withECDSA and verify using
        // SHA1withECDSA and NONEwithECDSA
        Signature s;
        if (p1363Format) {
            s = Signature.getInstance("SHA1withECDSAinP1363Format", provider);
        } else {
            s = Signature.getInstance("SHA1withECDSA", provider);
        }
        s.initSign(privateKey);
        s.update(data);
        byte[] s1 = s.sign();

        s.initVerify(publicKey);
        s.update(data);
        if (!s.verify(s1)) {
            throw new Exception("Sign/verify 1 failed");
        }

        if (p1363Format) {
            s = Signature.getInstance("NONEwithECDSAinP1363Format", provider);
        } else {
            s = Signature.getInstance("NONEwithECDSA", provider);
        }
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(data);
        s.initVerify(publicKey);
        s.update(digest);
        if (!s.verify(s1)) {
            throw new Exception("Sign/verify 2 failed");
        }

        // sign random data using NONEwithECDSA and verify using
        // SHA1withECDSA and NONEwithECDSA
        s.initSign(privateKey);
        s.update(digest);
        byte[] s2 = s.sign();

        s.initVerify(publicKey);
        s.update(digest);
        if (!s.verify(s2)) {
            throw new Exception("Sign/verify 3 failed");
        }

        if (p1363Format) {
            s = Signature.getInstance("SHA1withECDSAinP1363Format", provider);
        } else {
            s = Signature.getInstance("SHA1withECDSA", provider);
        }
        s.initVerify(publicKey);
        s.update(data);
        if (!s.verify(s2)) {
            throw new Exception("Sign/verify 4 failed");
        }

/*
        // XXX session release bug in P11Signature
        // test behavior if data of incorrect length is passed
        s = Signature.getInstance("NONEwithECDSA", provider);
        s.initSign(privateKey);
        s.update(new byte[8]);
        s.update(new byte[640]);
        try {
            s.sign();
            throw new Exception("No error NONEwithECDSA signing long data");
        } catch (SignatureException e) {
            System.out.println("OK: " + e);
        }
        System.out.println("sign/verify test ok");
/**/
    }

    private static byte[] b(String s) {
        return parse(s);
    }

}
