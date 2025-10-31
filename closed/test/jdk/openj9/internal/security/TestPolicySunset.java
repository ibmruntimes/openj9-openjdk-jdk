/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2025, 2025 All Rights Reserved
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
 * @summary Test Restricted Security Mode Policy Sunset
 * @library /test/lib
 * @run junit TestPolicySunset
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Provider;
import java.security.Security;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPolicySunset {

    private static Path updateExpireSoonSunsetFile(String baseFile) {
        try {
            LocalDate soonDate = LocalDate.now(ZoneOffset.UTC).plusMonths(1);
            String newDate = soonDate.format(DateTimeFormatter.ISO_DATE);

            String content = Files.readString(Paths.get(baseFile), StandardCharsets.UTF_8);
            String pattern = "(?m)^(RestrictedSecurity\\.Test-Profile-PolicySunset-ExpireSoon\\.desc\\.sunsetDate)\\s*=.*$";
            String updated = content.replaceAll(pattern, "$1 = " + newDate);

            Path tmp = Files.createTempFile("sunset-java.security.expireSoon.", ".tmp");
            Files.writeString(tmp, updated, StandardCharsets.UTF_8);
            return tmp;
        } catch (IOException e) {
            throw new RuntimeException("Failed to update sunset date for ExpireSoon profile", e);
        }
    }

    private static Stream<Arguments> patternMatches_expectedExitValue0() {
        String propertyFile = System.getProperty("test.src") + "/sunset-java.security";
        String updatedPropertyFile = updateExpireSoonSunsetFile(propertyFile).toString();

        return Stream.of(
                // 1 - expired; supress=false; ignore=true
                Arguments.of("Test-Profile-PolicySunset-Expired",
                        propertyFile,
                        "false", "true",
                        "WARNING: java will start with the requested restricted security profile but uncertified cryptography may be active"),
                // 2 - expired; supress=true; ignore=true, no warning
                Arguments.of("Test-Profile-PolicySunset-Expired",
                        propertyFile,
                        "true", "true",
                        ""),
                // 3 - expire soon (<=6 months); supress=false
                Arguments.of("Test-Profile-PolicySunset-ExpireSoon",
                        updatedPropertyFile,
                        "false", "false",
                        "The restricted security profile RestrictedSecurity.Test-Profile-PolicySunset-ExpireSoon is about to expire"),
                // 4 - expire soon (<=6 months); supress=true, no warning
                Arguments.of("Test-Profile-PolicySunset-ExpireSoon",
                        updatedPropertyFile,
                        "true", "false",
                        ""),
                // 5 - not expire (>6 months); no warning
                Arguments.of("Test-Profile-PolicySunset-NotExpire",
                        propertyFile,
                        "false", "false",
                        ""));
    }

    private static Stream<Arguments> patternMatches_expectedExitValue1() {
        String propertyFile = System.getProperty("test.src") + "/sunset-java.security";

        return Stream.of(
                // 1 - expired; supress=false; ignore=false
                Arguments.of("Test-Profile-PolicySunset-Expired",
                        propertyFile,
                        "false", "false",
                        "Use the -Dsemeru.restrictedsecurity.ignoresunsetexpiration to allow java to start while possibly using uncertified cryptography"),
                // 2 - expired; supress=true; ignore=false, no warning
                Arguments.of("Test-Profile-PolicySunset-Expired",
                        propertyFile,
                        "true", "false",
                        ""));
    }

    @ParameterizedTest
    @MethodSource("patternMatches_expectedExitValue0")
    public void shouldContain_expectedExitValue0(String customprofile, String securityPropertyFile, String suppresssunsetwarning, String ignoresunsetexpiration, String expected) throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "-Dsemeru.fips=true",
                "-Dsemeru.customprofile=" + customprofile,
                "-Djava.security.properties=" + securityPropertyFile,
                "-Dsemeru.restrictedsecurity.suppresssunsetwarning=" + suppresssunsetwarning,
                "-Dsemeru.restrictedsecurity.ignoresunsetexpiration=" + ignoresunsetexpiration,
                "TestPolicySunset"
        );
        outputAnalyzer.reportDiagnosticSummary();
        outputAnalyzer.shouldHaveExitValue(0).shouldMatch(expected);
    }

    @ParameterizedTest
    @MethodSource("patternMatches_expectedExitValue1")
    public void shouldContain_expectedExitValue1(String customprofile, String securityPropertyFile, String suppresssunsetwarning, String ignoresunsetexpiration, String expected) throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "-Dsemeru.fips=true",
                "-Dsemeru.customprofile=" + customprofile,
                "-Djava.security.properties=" + securityPropertyFile,
                "-Dsemeru.restrictedsecurity.suppresssunsetwarning=" + suppresssunsetwarning,
                "-Dsemeru.restrictedsecurity.ignoresunsetexpiration=" + ignoresunsetexpiration,
                "TestPolicySunset"
        );
        outputAnalyzer.reportDiagnosticSummary();
        outputAnalyzer.shouldHaveExitValue(1).shouldMatch(expected);
    }

    public static void main(String[] args) {
        // Something to trigger "properties" debug output.
        try {
            for (Provider provider : Security.getProviders()) {
                System.out.println("Provider Name: " + provider.getName());
                System.out.println("Provider Version: " + provider.getVersionStr());
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
