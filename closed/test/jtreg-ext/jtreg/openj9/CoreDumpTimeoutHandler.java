/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2020, 2022 All Rights Reserved
 * ===========================================================================
 */

package jtreg.openj9;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import com.sun.javatest.regtest.TimeoutHandler;

/**
 * This is the OpenJ9 core dump timeout handler. It runs jcmd on the process that has
 * timed out to request a system and java dump, as well as running jstack as the default
 * timeout handler does.
 */
public class CoreDumpTimeoutHandler extends TimeoutHandler {

    public CoreDumpTimeoutHandler(PrintWriter log, File outputDir, File testJdk) {
        super(log, outputDir, testJdk);
    }

    @Override
    protected void runActions(Process proc, long pid) throws InterruptedException {
        runJcmd(pid);
        runJstack(pid);
    }

    /**
     * Run jstack on the specified pid.
     * @param pid Process Id
     */
    private void runJstack(long pid) throws InterruptedException {
        try {
            log.println("Running jstack on process " + pid);

            File jstack = findJstack();
            if (jstack == null) {
                log.println("Warning: Could not find jstack in: " + testJdk.getAbsolutePath());
                log.println("Will not dump jstack output.");
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(jstack.getAbsolutePath(), pid + "");
            pb.redirectErrorStream(true);

            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.println(line);
                }
                p.waitFor();
            }
        } catch (IOException ex) {
            ex.printStackTrace(log);
        }
    }

    private File findJstack() {
        File jstack = new File(new File(testJdk, "bin"), "jstack");
        if (!jstack.exists()) {
            jstack = new File(new File(testJdk, "bin"), "jstack.exe");
            if (!jstack.exists()) {
                return null;
            }
        }
        return jstack;
    }

    /**
     * Run the specified jcmd command and log the output.
     * @param jcmdPath the absolute path of the jcmd executable
     * @param pid the pid, as a String, of the process to run jcmd on
     * @param command the jcmd command to run
     */
    private void runJcmdCommand(String jcmdPath, String pid, String command) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(jcmdPath, pid, command);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.println(line);
            }
            p.waitFor();
        }
    }

    /**
     * Run jcmd on the specified pid.
     * @param pid Process Id
     */
    private void runJcmd(long pid) throws InterruptedException {
        try {
            log.println("Running jcmd on process " + pid);

            File jcmd = findJcmd();
            if (jcmd == null) {
                log.println("Warning: Could not find jcmd in: " + testJdk.getAbsolutePath());
                log.println("Will not run jcmd.");
                return;
            }
            String jcmdPath = jcmd.getAbsolutePath();
            String pidString = Long.toString(pid);
            runJcmdCommand(jcmdPath, pidString, "Dump.system");
            runJcmdCommand(jcmdPath, pidString, "Dump.java");
        } catch (IOException ex) {
            ex.printStackTrace(log);
        }
    }

    private File findJcmd() {
        File jcmd = new File(new File(testJdk, "bin"), "jcmd");
        if (!jcmd.exists()) {
            jcmd = new File(new File(testJdk, "bin"), "jcmd.exe");
            if (!jcmd.exists()) {
                return null;
            }
        }
        return jcmd;
    }
}
