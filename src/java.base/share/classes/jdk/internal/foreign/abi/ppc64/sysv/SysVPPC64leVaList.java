/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2022, 2022 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.foreign.abi.ppc64.sysv;

import java.lang.foreign.*;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Scoped;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import static jdk.internal.foreign.PlatformLayouts.SysVPPC64le;

/**
 * This file serves as a placeholder for VaList on Linux/ppc64le as the code
 * at Java level is not yet implemented for the moment. Futher analysis on
 * the struct is required to understand how the struct is laid out in memory
 * (e.g. the type & size of each field in va_list) and how the registers are
 * allocated for va_list.
 */
public non-sealed class SysVPPC64leVaList implements VaList, Scoped {
    static final Class<?> CARRIER = MemoryAddress.class;

    public static VaList empty() {
        throw new InternalError("empty() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public int nextVarg(ValueLayout.OfInt layout) {
        throw new InternalError("nextVarg() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public long nextVarg(ValueLayout.OfLong layout) {
        throw new InternalError("nextVarg() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public double nextVarg(ValueLayout.OfDouble layout) {
        throw new InternalError("nextVarg() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public MemoryAddress nextVarg(ValueLayout.OfAddress layout) {
        throw new InternalError("nextVarg() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
        throw new InternalError("nextVarg() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public void skip(MemoryLayout... layouts) {
        throw new InternalError("skip() is not yet implemented"); //$NON-NLS-1$
    }

    public static VaList ofAddress(MemoryAddress ma, MemorySession session) {
        throw new InternalError("ofAddress() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public MemorySession session() {
        throw new InternalError("session() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public MemorySessionImpl sessionImpl() {
        return MemorySessionImpl.toSessionImpl(session());
    }

    @Override
    public VaList copy() {
        throw new InternalError("copy() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public MemoryAddress address() {
        throw new InternalError("address() is not yet implemented"); //$NON-NLS-1$
    }

    @Override
    public String toString() {
        throw new InternalError("toString() is not yet implemented"); //$NON-NLS-1$
    }

    static SysVPPC64leVaList.Builder builder(MemorySession session) {
        return new SysVPPC64leVaList.Builder(session);
    }

    public static non-sealed class Builder implements VaList.Builder {

        public Builder(MemorySession session) {
            throw new InternalError("Builder() is not yet implemented"); //$NON-NLS-1$
        }

        @Override
        public Builder addVarg(ValueLayout.OfInt layout, int value) {
            throw new InternalError("addVarg() is not yet implemented"); //$NON-NLS-1$
        }

        @Override
        public Builder addVarg(ValueLayout.OfLong layout, long value) {
            throw new InternalError("addVarg() is not yet implemented"); //$NON-NLS-1$
        }

        @Override
        public Builder addVarg(ValueLayout.OfDouble layout, double value) {
            throw new InternalError("addVarg() is not yet implemented"); //$NON-NLS-1$
        }

        @Override
        public Builder addVarg(ValueLayout.OfAddress layout, Addressable value) {
            throw new InternalError("addVarg() is not yet implemented"); //$NON-NLS-1$
        }

        @Override
        public Builder addVarg(GroupLayout layout, MemorySegment value) {
            throw new InternalError("addVarg() is not yet implemented"); //$NON-NLS-1$
        }

        public VaList build() {
            throw new InternalError("build() is not yet implemented"); //$NON-NLS-1$
        }
    }
}
