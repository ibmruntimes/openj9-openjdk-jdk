/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2022, 2023 All Rights Reserved
 * ===========================================================================
 */

/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "riscv64"
 * | os.arch == "ppc64" | os.arch == "ppc64le" | os.arch == "s390x"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestClassLoaderFindNative
 */

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.*;

// FYI this test is run on 64-bit platforms only for now,
// since the windows 32-bit linker fails and there
// is some fallback behaviour to use the 64-bit linker,
// where cygwin gets in the way and we accidentally pick up its
// link.exe
public class TestClassLoaderFindNative {
    static {
        System.loadLibrary("LookupTest");
    }

    @Test
    public void testSimpleLookup() {
        assertFalse(SymbolLookup.loaderLookup().find("f").isEmpty());
    }

    @Test
    public void testInvalidSymbolLookup() {
        assertTrue(SymbolLookup.loaderLookup().find("nonExistent").isEmpty());
    }

    @Test
    public void testVariableSymbolLookup() {
        MemorySegment segment = SymbolLookup.loaderLookup().find("c").get().reinterpret(JAVA_INT.byteSize());
        /* JAVA_INT applies to both Little-Endian and Big-Endian
         * platforms given the one-byte int value is stored at the
         * highest address(offset 3) of the int type in native on
         * the Big-Endian platforms.
         * See libLookupTest.c
         */
        assertEquals(segment.get(JAVA_INT, 0), 42);
    }
}
