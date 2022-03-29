/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * (c) Copyright IBM Corp. 2021, 2022 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.foreign.abi.ppc64.sysv;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.VaList;
import jdk.internal.foreign.abi.SharedUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * ABI implementation based on System V ABI PPC64LE
 *
 * Note: This file is copied from x86/windows with modification to accommodate the specifics
 * on Linux/ppc64le and might be updated accordingly in terms of VaList in the future.
 */
public final class SysVPPC64leLinker implements CLinker {
    private static SysVPPC64leLinker instance;

    static final long ADDRESS_SIZE = 64; // bits

    public static SysVPPC64leLinker getInstance() {
        if (instance == null) {
            instance = new SysVPPC64leLinker();
        }
        return instance;
    }

    @Override
    public final MethodHandle downcallHandle(FunctionDescriptor function) {
        Objects.requireNonNull(function);
        MethodType type = SharedUtils.inferMethodType(function, false);
        MethodHandle handle = CallArranger.arrangeDowncall(type, function);
        if (!type.returnType().equals(MemorySegment.class)) {
            // not returning segment, just insert a throwing allocator
            handle = MethodHandles.insertArguments(handle, 1, SharedUtils.THROWING_ALLOCATOR);
        }
        return SharedUtils.wrapDowncall(handle, function);
    }

    @Override
    public final NativeSymbol upcallStub(MethodHandle target, FunctionDescriptor function, ResourceScope scope) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(target);
        Objects.requireNonNull(function);
        SharedUtils.checkExceptions(target);
        MethodType type = SharedUtils.inferMethodType(function, true);
        if (!type.equals(target.type())) {
            throw new IllegalArgumentException("Wrong method handle type: " + target.type());
        }
        return CallArranger.arrangeUpcall(target, target.type(), function, scope);
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, ResourceScope scope) {
        SysVPPC64leVaList.Builder builder = SysVPPC64leVaList.builder(scope);
        actions.accept(builder);
        return builder.build();
    }

    public static VaList newVaListOfAddress(MemoryAddress ma, ResourceScope scope) {
        return SysVPPC64leVaList.ofAddress(ma, scope);
    }

    public static VaList emptyVaList() {
        return SysVPPC64leVaList.empty();
    }
}
