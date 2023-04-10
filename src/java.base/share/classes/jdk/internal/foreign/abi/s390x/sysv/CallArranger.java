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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

/*
 * ===========================================================================
 * (c) Copyright IBM Corp. 2022, 2023 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.foreign.abi.s390x.sysv;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import jdk.internal.foreign.abi.AbstractLinker.UpcallStubFactory;
import jdk.internal.foreign.abi.DowncallLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.UpcallLinker;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;

/**
 * S390x CallArranger specialized for SysV C ABI
 */
public class CallArranger {

	/* Replace DowncallLinker in OpenJDK with the implementation of DowncallLinker specific to OpenJ9 */
	public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc, LinkerOptions options) {
		return DowncallLinker.getBoundMethodHandle(mt, cDesc, options);
	}

	/* Replace UpcallLinker in OpenJDK with the implementation of UpcallLinker specific to OpenJ9 */
	public static UpcallStubFactory arrangeUpcall(MethodType mt, FunctionDescriptor cDesc) {
		return UpcallLinker.makeFactory(mt, cDesc);
	}
}
