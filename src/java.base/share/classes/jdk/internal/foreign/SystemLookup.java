/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2022, 2024 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.foreign;

import jdk.internal.loader.NativeLibraries;
import jdk.internal.loader.NativeLibrary;
import jdk.internal.loader.RawNativeLibraries;
import jdk.internal.util.OperatingSystem;
import jdk.internal.util.StaticProperty;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.ADDRESS;

public final class SystemLookup implements SymbolLookup {

    private SystemLookup() { }

    private static final SystemLookup INSTANCE = new SystemLookup();

    /* A fallback lookup, used when creation of system lookup fails. */
    private static final SymbolLookup FALLBACK_LOOKUP = name -> {
        Objects.requireNonNull(name);
        return Optional.empty();
    };

    /* The address of the inlined function list in the specified library. */
    private static final MemorySegment funcs = getInlinedFunctListAddr();

    /*
     * On POSIX systems, dlsym will allow us to lookup symbol in library dependencies; the same trick doesn't work
     * on Windows. For this reason, on Windows we do not generate any side-library, and load msvcrt.dll directly instead.
     */
    private static final SymbolLookup SYSTEM_LOOKUP = makeSystemLookup();

    private static SymbolLookup makeSystemLookup() {
        try {
            if (OperatingSystem.isWindows()) {
                return makeWindowsLookup();
            } else if (OperatingSystem.isAix() || OperatingSystem.isZOS()) {
                return makeDefaultLookup();
            } else {
                return libLookup(libs -> libs.load(jdkLibraryPath("syslookup")));
            }
        } catch (Throwable ex) {
            // This can happen in the event of a library loading failure - e.g. if one of the libraries the
            // system lookup depends on cannot be loaded for some reason. In such extreme cases, rather than
            // fail, return a dummy lookup.
            return FALLBACK_LOOKUP;
        }
    }

    /* Obtain the address of the inlined function list by loading the specified library. */
    private static MemorySegment getInlinedFunctListAddr() {
        SequenceLayout funcsLayout;

        if (OperatingSystem.isAix()) {
            funcsLayout = AixFuncSymbols.LAYOUT;
        } else if (OperatingSystem.isZOS()) {
            funcsLayout = ZosFuncSymbols.LAYOUT;
        } else {
            return null;
        }

        /* Directly load the specified library only once with the inlined libc functions
         * missing in the default library.
         */
        SymbolLookup funcsLibLookup = libLookup(libs -> libs.load(jdkLibraryPath("syslookup")));

        /* Perform a FFI downcall to obtain the address of the inlined function list. */
        MemorySegment funcs_addr = funcsLibLookup.findOrThrow("funcs_addr");
        MethodHandle handle = Linker.nativeLinker().downcallHandle(funcs_addr, FunctionDescriptor.of(ADDRESS));
        MemorySegment funcsAddr;
        try {
            funcsAddr = (MemorySegment)handle.invoke();
        } catch (Throwable e) {
            throw new InternalError(e);
        }

        return funcsAddr.reinterpret(funcsLayout.byteSize());
    }

    /* The method is to wrap up the SymbolLookup address generated by forward-porting
     * the default library loading solution introduced in Java 16, which remains valid
     * on AIX or z/OS.
     */
    private static SymbolLookup makeDefaultLookup() {
        NativeLibrary lib = NativeLibraries.defaultLibrary;
        return name -> {
            Objects.requireNonNull(name);
            MemorySegment funcAddr;
            var symbol = OperatingSystem.isAix() ? AixFuncSymbols.valueOfOrNull(name) : ZosFuncSymbols.valueOfOrNull(name);
            if (symbol == null) {
                try {
                    /* Look up the libc functions in the default library. */
                    funcAddr = MemorySegment.ofAddress(lib.lookup(name));
                } catch (NoSuchMethodException e) {
                    return Optional.empty();
                }
            } else {
                funcAddr = funcs.getAtIndex(ADDRESS, symbol.ordinal());
            }

            return Optional.of(MemorySegment.ofAddress(funcAddr.address()));
        };
    }

    private static SymbolLookup makeWindowsLookup() {
        String systemRoot = System.getenv("SystemRoot");
        Path system32 = Path.of(systemRoot, "System32");
        Path ucrtbase = system32.resolve("ucrtbase.dll");
        Path msvcrt = system32.resolve("msvcrt.dll");

        boolean useUCRT = Files.exists(ucrtbase);
        Path stdLib = useUCRT ? ucrtbase : msvcrt;
        SymbolLookup lookup = libLookup(libs -> libs.load(stdLib));

        if (useUCRT) {
            // use a fallback lookup to look up inline functions from fallback lib

            SymbolLookup fallbackLibLookup =
                    libLookup(libs -> libs.load(jdkLibraryPath("syslookup")));

            @SuppressWarnings("restricted")
            MemorySegment funcs = fallbackLibLookup.findOrThrow("funcs")
                    .reinterpret(WindowsFallbackSymbols.LAYOUT.byteSize());

            Function<String, Optional<MemorySegment>> fallbackLookup = name -> Optional.ofNullable(WindowsFallbackSymbols.valueOfOrNull(name))
                .map(symbol -> funcs.getAtIndex(ADDRESS, symbol.ordinal()));

            final SymbolLookup finalLookup = lookup;
            lookup = name -> {
                Objects.requireNonNull(name);
                if (Utils.containsNullChars(name)) return Optional.empty();
                return finalLookup.find(name).or(() -> fallbackLookup.apply(name));
            };
        }

        return lookup;
    }

    private static SymbolLookup libLookup(Function<RawNativeLibraries, NativeLibrary> loader) {
        NativeLibrary lib = loader.apply(RawNativeLibraries.newInstance(MethodHandles.lookup()));
        return name -> {
            Objects.requireNonNull(name);
            if (Utils.containsNullChars(name)) return Optional.empty();
            try {
                long addr = lib.lookup(name);
                return addr == 0 ?
                        Optional.empty() :
                        Optional.of(MemorySegment.ofAddress(addr));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        };
    }

    /*
     * Returns the path of the given library name from JDK
     */
    private static Path jdkLibraryPath(String name) {
        Path javahome = Path.of(StaticProperty.javaHome());
        String lib = OperatingSystem.isWindows() ? "bin" : "lib";
        String libname = System.mapLibraryName(name);
        return javahome.resolve(lib).resolve(libname);
    }


    public static SystemLookup getInstance() {
        return INSTANCE;
    }

    @Override
    public Optional<MemorySegment> find(String name) {
        return SYSTEM_LOOKUP.find(name);
    }

    // fallback symbols missing from ucrtbase.dll
    // this list has to be kept in sync with the table in the companion native library
    private enum WindowsFallbackSymbols {
        // stdio
        fprintf,
        fprintf_s,
        fscanf,
        fscanf_s,
        fwprintf,
        fwprintf_s,
        fwscanf,
        fwscanf_s,
        printf,
        printf_s,
        scanf,
        scanf_s,
        snprintf,
        sprintf,
        sprintf_s,
        sscanf,
        sscanf_s,
        swprintf,
        swprintf_s,
        swscanf,
        swscanf_s,
        vfprintf,
        vfprintf_s,
        vfscanf,
        vfscanf_s,
        vfwprintf,
        vfwprintf_s,
        vfwscanf,
        vfwscanf_s,
        vprintf,
        vprintf_s,
        vscanf,
        vscanf_s,
        vsnprintf,
        vsnprintf_s,
        vsprintf,
        vsprintf_s,
        vsscanf,
        vsscanf_s,
        vswprintf,
        vswprintf_s,
        vswscanf,
        vswscanf_s,
        vwprintf,
        vwprintf_s,
        vwscanf,
        vwscanf_s,
        wprintf,
        wprintf_s,
        wscanf,
        wscanf_s,

        // time
        gmtime;

        static WindowsFallbackSymbols valueOfOrNull(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        static final SequenceLayout LAYOUT = MemoryLayout.sequenceLayout(
                values().length, ADDRESS);
    }

    /* Inlined libc function symbols missing in the default library. */
    public enum AixFuncSymbols {
        bcopy, endfsent, getfsent, getfsfile, getfsspec, longjmp,
        memcpy, memmove, setfsent, setjmp, siglongjmp, strcat,
        strcpy, strncat, strncpy
        ;

        static AixFuncSymbols valueOfOrNull(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        static final SequenceLayout LAYOUT = MemoryLayout.sequenceLayout(
                values().length, ADDRESS);
    }
}
