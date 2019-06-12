/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2019, 2019 All Rights Reserved
 * ===========================================================================
 */

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.ListeningConnector;
import jdk.test.lib.apps.LingeredApp;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

/*
 * @test
 * @bug 8225474
 * @summary JDI connector accept fails "Address already in use" with concurrent listeners
 * @library /test/lib
 *
 * @build HelloWorld JdwpConcurrentAttachTest
 * @run main/othervm JdwpConcurrentAttachTest
 */
public class JdwpConcurrentAttachTest extends Thread {

    public static void main(String[] args) throws Exception {
        failure = false;

        /*
         * Repeat 10 times to catch intermittent nature of bug
         */
        for(int j=0; j<10; j++) {
            /*
             * Create 10 concurrent threads all trying to start a listener
             */ 
            int numThreads=10;
            latch = new CountDownLatch(numThreads);
            InetAddress localAddresses[] = InetAddress.getAllByName("localhost");

            for(int i=0; i<numThreads; i++) {
                JdwpConcurrentAttachTest test = new JdwpConcurrentAttachTest(localAddresses[0].getHostAddress(),0);
                test.start();
            }

            latch.await(30,TimeUnit.SECONDS);

            if (failure) {
                throw new RuntimeException("Failed to accept connector connection", failureException);
            }
        }
    }

    static  boolean   failure=false;
    static  Exception failureException=null;
    static  CountDownLatch latch;

    private String threadListenAddress;
    private int    threadPort;

    public JdwpConcurrentAttachTest(String listenAddress, int port) {
        threadListenAddress = listenAddress;
        threadPort = port;
    }

    public void run() {
        try {
           attachTest(threadListenAddress, String.valueOf(threadPort)); 
        } catch(Exception e) {
           failure=true;
           failureException=e;
           e.printStackTrace();
        }

        latch.countDown();
    }

    private static void attachTest(String listenAddress, String connectPort)
            throws Exception {
        log("Starting listening at " + listenAddress);
        ListeningConnector connector = getListenConnector();
        Map<String, Connector.Argument> args = connector.defaultArguments();
        setConnectorArg(args, "localAddress", listenAddress);
        setConnectorArg(args, "port", connectPort);

        String actualAddress = connector.startListening(args);
        log("Listening address = "+actualAddress);

        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit((Callable<Exception>)() -> {
                try {
                    VirtualMachine vm = connector.accept(args);
                    log("ACCEPTED.");
                    vm.dispose();
                } catch(Exception e) {
                    failure = true;
                    failureException=e;
                    e.printStackTrace();
                }
                return null;
            });
            executor.shutdown();

            LingeredApp debuggee = LingeredApp.startApp(
                    Arrays.asList("-agentlib:jdwp=transport=dt_socket"
                                +",address="+ actualAddress
                                + ",server=n,suspend=n"));

            debuggee.stopApp();

            executor.awaitTermination(20, TimeUnit.SECONDS);
        } finally {
            connector.stopListening(args);
        }
    }

    private static String LISTEN_CONNECTOR = "com.sun.jdi.SocketListen";

    private static ListeningConnector getListenConnector() {
        return (ListeningConnector)getConnector(LISTEN_CONNECTOR);
    }

    private static Connector getConnector(String name) {
        List<Connector> connectors = Bootstrap.virtualMachineManager().allConnectors();
        for (Iterator<Connector> iter = connectors.iterator(); iter.hasNext(); ) {
            Connector connector = iter.next();
            if (connector.name().equalsIgnoreCase(name)) {
                return connector;
            }
        }
        throw new IllegalArgumentException("Connector " + name + " not found");
    }

    private static void setConnectorArg(Map<String, Connector.Argument> args, String name, String value) {
        Connector.Argument arg = args.get(name);
        if (arg == null) {
            throw new IllegalArgumentException("Argument " + name + " is not defined");
        }
        arg.setValue(value);
    }

    private static void log(Object o) {
        System.out.println(String.valueOf(o));
    }
}
