/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2019, 2024 All Rights Reserved
 * ===========================================================================
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * IBM designates this particular file as subject to the "Classpath" exception
 * as provided by IBM in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, see <http://www.gnu.org/licenses/>.
 * ===========================================================================
 */
package requires;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Platform;

public class OpenJ9PropsExt implements Callable<Map<String, String>> {

    @Override
    public Map<String, String> call() {
        Map<String, String> map = new HashMap<>();
        try {
            map.put("container.support", "true");
            map.put("jlink.packagedModules", Boolean.toString(packagedModules()));
            map.put("systemd.support", Boolean.toString(systemdSupport()));
            map.put("vm.bits", vmBits());
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
            map.put("vm.jvmti", "true");
            map.put("vm.musl", "false");
            map.put("vm.openj9", "true");
            map.put("vm.opt.final.ClassUnloading", "true");
            map.put("vm.opt.final.ZGenerational", "false");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return map;
    }

    /**
     * @return whether the current SDK includes packaged modules
     */
    private static boolean packagedModules() {
        try {
            return Path.of(System.getProperty("java.home"), "jmods").toFile().exists();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return whether systemd is available on the current platform
     */
    private static boolean systemdSupport() {
        if (Platform.isLinux()) {
            try {
                Process probe = new ProcessBuilder("which", "systemd-run").start();
                probe.waitFor(10, TimeUnit.SECONDS);
                if (probe.exitValue() == 0) {
                    return true;
                }
            } catch (Exception e) {
                // assume not supported
            }
        }
        return false;
    }

    /**
     * @return VM bitness, the value of the "sun.arch.data.model" property.
     */
    private static String vmBits() throws Exception {
        String dataModel = System.getProperty("sun.arch.data.model");
        if (dataModel != null) {
            return dataModel;
        } else {
            throw new Exception("Can't get 'sun.arch.data.model' property");
        }
    }
}
