/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2019, 2025 All Rights Reserved
 * ===========================================================================
 */
package requires;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jdk.internal.misc.PreviewFeatures;
import jdk.test.lib.Platform;

public class OpenJ9PropsExt implements Callable<Map<String, String>> {
    // value known to jtreg as an indicator of error state
    private static final String ERROR_STATE = "__ERROR__";

    private static class SafeMap {
        private final Map<String, String> map = new HashMap<>();

        public void put(String key, String value) {
            map.put(key, value);
        }

        public void put(String key, Supplier<String> s) {
            String value;
            try {
                value = s.get();
            } catch (Throwable t) {
                System.err.println("failed to get value for " + key);
                t.printStackTrace(System.err);
                value = ERROR_STATE + t;
            }
            map.put(key, value);
        }
    }

    @Override
    public Map<String, String> call() {
        SafeMap map = new SafeMap();
        map.put("container.support", "true");
        map.put("java.enablePreview", this::isPreviewEnabled);
        map.put("jdk.static", "false");
        map.put("jlink.packagedModules", this::packagedModules);
        map.put("systemd.support", this::systemdSupport);
        map.put("vm.asan", "false");
        map.put("vm.bits", this::vmBits);
        map.put("vm.cds", "false");
        map.put("vm.cds.write.archived.java.heap", "false");
        map.put("vm.compiler2.enabled", "false");
        map.put("vm.continuations", "true");
        map.put("vm.debug", "false");
        map.put("vm.flagless", "true");
        map.put("vm.gc.G1", "false");
        map.put("vm.gc.Parallel", "false");
        map.put("vm.gc.Serial", "false");
        map.put("vm.gc.Shenandoah", "false");
        map.put("vm.gc.Z", "false");
        map.put("vm.gc.ZGenerational", "false");
        map.put("vm.gc.ZSinglegen", "false");
        map.put("vm.graal.enabled", "false");
        map.put("vm.hasJFR", "false");
        map.put("vm.jvmci", "false");
        map.put("vm.jvmti", "true");
        map.put("vm.musl", "false");
        map.put("vm.openj9", "true");
        map.put("vm.opt.final.ClassUnloading", "true");
        map.put("vm.opt.final.ZGenerational", "false");
        return map.map;
    }

    /**
     * Print a stack trace before returning error state.
     * Used by the various helper functions which parse information from
     * VM properties in the case where they don't find an expected property
     * or a property doesn't conform to an expected format.
     *
     * @return {@link #ERROR_STATE}
     */
    private static String errorWithMessage(String message) {
        new Exception(message).printStackTrace();
        return ERROR_STATE + message;
    }

    protected String isPreviewEnabled() {
        return "" + PreviewFeatures.isEnabled();
    }

    /**
     * @return whether the current SDK includes packaged modules
     */
    protected String packagedModules() {
        return "" + Path.of(System.getProperty("java.home"), "jmods").toFile().exists();
    }

    /**
     * @return whether systemd is available on the current platform
     */
    protected String systemdSupport() {
        boolean result = false;
        if (Platform.isLinux()) {
            try {
                Process probe = new ProcessBuilder("which", "systemd-run").start();
                probe.waitFor(10, TimeUnit.SECONDS);
                if (probe.exitValue() == 0) {
                    result = true;
                }
            } catch (Exception e) {
                // assume not supported
            }
        }
        return "" + result;
    }

    /**
     * @return VM bitness, the value of the "sun.arch.data.model" property.
     */
    protected String vmBits() {
        String dataModel = System.getProperty("sun.arch.data.model");
        if (dataModel != null) {
            return dataModel;
        } else {
            return errorWithMessage("Can't get 'sun.arch.data.model' property");
        }
    }
}
