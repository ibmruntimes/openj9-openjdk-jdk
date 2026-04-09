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

/**
 * A provider created to test the restriction of legacy paths through
 * the RestrictedSecurity mode.
 */
package jdk.test.lib;

import java.security.Provider;

public class LegacyProvider extends Provider {
    public LegacyProvider() {
        super("LegacyProvider", "1", "Test provider");
        put("Signature.Test", "example.class.MyClass");
        put("Signature.Test2", "example.class.MyClass2");
        put("Signature.Test3", "example.class.MyClass3");

        putIfAbsent("Signature.Test4", "example.class.MyClass4");

        replace("Signature.Test2", "example.class.MyClass2New");
        replace("Signature.Test2", "example.class.MyClass2New", "example.class.MyClass2");

        replace("Signature.Test3", "example.class.MyClass3New");
        replace("Signature.Test3", "example.class.MyClass3", "example.class.MyClass3New");

        merge("Signature.Test5", "example.class.MyClass5", (a, b) -> b);
    }
}
