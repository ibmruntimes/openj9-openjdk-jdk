/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

//
// Please run in othervm mode.  SunJSSE does not support dynamic system
// properties, no way to re-use system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8242294
 * @summary JSSE Client does not throw SSLException when an alert occurs during
 *          handshaking.
 * @library /test/lib
 * @run main/othervm ClientExcOnAlert TLSv1.2
 * @run main/othervm ClientExcOnAlert TLSv1.3
 */

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Base64;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import jdk.test.lib.Utils;
import jdk.test.lib.security.SecurityUtils;

public class ClientExcOnAlert {
    // This is a PKCS#12 keystore created with the following command:
    // keytool -genkeypair -alias testcert -keyalg rsa -keysize 2048
    //         -sigalg SHA256withRSA
    //         -dname "CN=Test TLS Self-Signed Cert, O=Test" -validity 365
    //         -storetype pkcs12 -keystore p12ks.p12
    //
    // The resulting keystore was then converted to PEM for inclusion in this
    // file.
    private static int serverPort = -1;
    private static final String KEYSTORE_PASS = "password";
    private static final String KEYSTORE_PEM_FIPS =
        "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";
    private static final String KEYSTORE_PEM =
        "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";

    static final Lock lock = new ReentrantLock();
    static final Condition serverReady = lock.newCondition();

    public static void main(String[] args) throws Exception {
        System.setProperty("javax.net.ssl.trustStore", "keystore.p12");
        System.setProperty("javax.net.ssl.trustStorePassword", KEYSTORE_PASS);
        Thread serverThread = new Thread(() -> {
                    try {
                        doServerSide();
                    }
                    catch (Exception exc) {
                        log("Caught exception: %s", exc);
                    }
                }
        );
        serverThread.start();
        try {
            doClientSide((args == null || args.length < 1) ? null : args[0]);
            throw new RuntimeException("Expected SSLException did not occur!");
        } catch (SSLException ssle) {
            log("Caught expected exception on client: " + ssle);
        } finally {
            serverThread.join();
        }
    }

    static void doServerSide() throws Exception {
        Thread.currentThread().setName("ServerThread");
        SSLContext sslc = SSLContext.getInstance("TLS");
        log("doServerSide start");
        KeyManagerFactory kmf;
        if (!(SecurityUtils.isFIPS())) {
            kmf = createKeyManagerFactory(KEYSTORE_PEM,
                KEYSTORE_PASS);
        } else {
            kmf = createKeyManagerFactory(KEYSTORE_PEM_FIPS,
                KEYSTORE_PASS);
        }    
        sslc.init(kmf.getKeyManagers(), null, null);
        SSLServerSocketFactory ssf =
                (SSLServerSocketFactory)sslc.getServerSocketFactory();
        try (SSLServerSocket sslServerSocket =
                (SSLServerSocket)ssf.createServerSocket(0)) {
            sslServerSocket.setReuseAddress(true);
            // Set the server port and wake up the client thread who is waiting
            // for the port to be set.
            lock.lock();
            try {
                serverPort = sslServerSocket.getLocalPort();
                log("Server listening on port %d", serverPort);
                serverReady.signalAll();
                log("Server ready");
            } finally {
                lock.unlock();
            }

            // Go into the accept wait state until the client initiates the
            // TLS handshake.
            try (SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
                    PrintWriter pw =
                        new PrintWriter(sslSocket.getOutputStream());
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(sslSocket.getInputStream()))) {
                log("Incoming connection from %s",
                        sslSocket.getRemoteSocketAddress());
                String data = br.readLine();
                log("Got mesage from client: ", data);
                pw.write("I am server\n");
                pw.close();
            }
        }
    }

    private static KeyManagerFactory createKeyManagerFactory(
            String ksPem, String ksAuth) throws IOException,
            GeneralSecurityException {
        KeyManagerFactory kmf = null;
        if (ksPem != null && ksAuth != null) {
            Base64.Decoder b64dec = Base64.getMimeDecoder();
            ByteArrayInputStream bais =
                    new ByteArrayInputStream(b64dec.decode(ksPem));
            KeyStore ks = KeyStore.getInstance("PKCS12");
            char[] ksPass = ksAuth.toCharArray();
            ks.load(bais, ksPass);

            kmf = KeyManagerFactory.getInstance("PKIX");
            kmf.init(ks, ksAuth.toCharArray());
        }

        return kmf;
    }

    static void doClientSide(String proto) throws Exception {
        Thread.currentThread().setName("ClientThread");
        log("doClientSide start");

        // Wait for the server to be ready and wake up this thread
        // so the client knows which port to communicate with
        lock.lock();
        try {
            serverReady.await();
            log("Client ready to contact port %d", serverPort);
        } finally {
            lock.unlock();
        }

        SSLSocketFactory sslsf =
                (SSLSocketFactory)SSLSocketFactory.getDefault();
        try (SSLSocket sslSocket = (SSLSocket)sslsf.createSocket(
                InetAddress.getLocalHost(), serverPort);
            BufferedReader br = new BufferedReader(
                new InputStreamReader(sslSocket.getInputStream()));
            PrintWriter pw = new PrintWriter(sslSocket.getOutputStream())) {

            if (proto != null) {
                sslSocket.setEnabledProtocols(new String[] { proto });
            }
            pw.write("I am client\n");
            pw.flush();

            String response = br.readLine();
            System.out.println("response is: " + response);
        }
    }

    private static void log(String msgFmt, Object ... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d | %s | ",
                System.currentTimeMillis(), Thread.currentThread().getName()));
        sb.append(String.format(msgFmt, args));
        System.out.println(sb.toString());
    }
}