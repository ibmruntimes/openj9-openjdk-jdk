/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2022, 2023 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.foreign.abi.s390x.sysv;

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.VaList;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

/**
 * ABI implementation based on System V ABI s390x
 *
 * Note: This file is copied from x64/sysv with modification to accommodate the specifics
 * on Linux/s390x and might be updated accordingly in terms of VaList in the future.
 */
public final class SysVS390xLinker extends AbstractLinker {

    public static SysVS390xLinker getInstance() {
        final class Holder {
            private static final SysVS390xLinker INSTANCE = new SysVS390xLinker();
        }

        return Holder.INSTANCE;
    }

    private SysVS390xLinker() {
        /* Ensure there is only one instance */
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options) {
        return CallArranger.arrangeDowncall(inferredMethodType, function, options);
    }

    @Override
    protected UpcallStubFactory arrangeUpcall(MethodType mt, FunctionDescriptor cDesc) {
        return CallArranger.arrangeUpcall(mt, cDesc);
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, SegmentScope scope) {
        SysVS390xVaList.Builder builder = SysVS390xVaList.builder(scope);
        actions.accept(builder);
        return builder.build();
    }

    public static VaList newVaListOfAddress(long address, SegmentScope scope) {
        return SysVS390xVaList.ofAddress(address, scope);
    }

    public static VaList emptyVaList() {
        return SysVS390xVaList.empty();
    }
}
