/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2022, 2022 All Rights Reserved
 * ===========================================================================
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class DateUtil {

    public static void main(String... args) {
        String date = "";
        String format = "";

        for (String arg : args) {
            if (arg.startsWith("--date=")) {
                date = arg.substring(7).trim();
            } else if (arg.startsWith("--format=")) {
                format = arg.substring(9).trim();
            } else {
                showUsageAndExit();
            }
        }

        LocalDateTime time = parseTime(date);

        if (format.isEmpty()) {
            System.out.println(time.toEpochSecond(ZoneOffset.UTC));
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format, Locale.ROOT);

            System.out.println(formatter.format(time));
        }
    }

    private static LocalDateTime parseTime(String text) {
        if (text.isEmpty()) {
            return LocalDateTime.now(ZoneOffset.UTC);
        }

        if (text.matches("\\d+")) {
            return LocalDateTime.ofEpochSecond(Long.parseLong(text), 0, ZoneOffset.UTC);
        }

        try {
            return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            // try next format
        }

        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            // try next format
        }

        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException e) {
            System.err.format("Cannot parse time: '%s'%n", text);
            System.exit(1);
            return null;
        }
    }

    private static void showUsageAndExit() {
        System.err.println("Usage: DateUtil [options]");
        System.err.println("  --date=<time>      time in epoch seconds, or in iso-8601 or yyyy-MM-dd format");
        System.err.println("  --format=<format>  output format");
        System.exit(1);
    }

}
