/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2026, 2026 All Rights Reserved
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
 *
 * ===========================================================================
 */

/*
 * @test
 * @summary Test FIPS mode API
 * @library /test/lib
 * @run junit TestFIPSMode
 */

import org.junit.jupiter.api.Test;
import java.security.Security;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestFIPSMode {

    private static void getFIPSMode() throws Exception {
        String s = Security.getFIPSmode();
        System.out.println("FIPS mode: " + s);
    }

    @Test
    public void FIPSModeEnabledProfile1() throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "-Dsemeru.fips=true",
                "-Dsemeru.customprofile=FIPSEnabled140-2.Version",
                "-Djava.security.properties=" + System.getProperty("test.src") + "/fipsmode-java.security",
                "TestFIPSMode"
        );
        outputAnalyzer.reportDiagnosticSummary();
        outputAnalyzer.shouldMatch("FIPS mode: 140-2");
        outputAnalyzer.shouldHaveExitValue(0);
    }

    @Test
    public void FIPSModeEnabledProfile2() throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "-Dsemeru.fips=true",
                "-Dsemeru.customprofile=FIPSEnabled140-3.Version",
                "-Djava.security.properties=" + System.getProperty("test.src") + "/fipsmode-java.security",
                "TestFIPSMode"
        );
        outputAnalyzer.reportDiagnosticSummary();
        outputAnalyzer.shouldMatch("FIPS mode: 140-3");
        outputAnalyzer.shouldHaveExitValue(0);
    }

    @Test
    public void FIPSDisabledMode() throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "-Dsemeru.customprofile=FIPSDisabled.Version",
                "-Djava.security.properties=" + System.getProperty("test.src") + "/fipsmode-java.security",
                "TestFIPSMode"
        );
        outputAnalyzer.reportDiagnosticSummary();
        outputAnalyzer.shouldMatch("FIPS mode: null");
        outputAnalyzer.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        Security.getProviders();
        getFIPSMode();
    }
}
