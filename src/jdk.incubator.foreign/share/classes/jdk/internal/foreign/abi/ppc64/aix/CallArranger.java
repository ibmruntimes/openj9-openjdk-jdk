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

package jdk.internal.foreign.abi.ppc64.aix;

import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.NativeSymbol;
import jdk.internal.foreign.abi.ProgrammableInvoker;
import jdk.internal.foreign.abi.ProgrammableUpcallHandler;
import jdk.incubator.foreign.ResourceScope;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * For the AIX PPC64 C ABI specifically, this class uses the ProgrammableInvoker API
 * which is turned into a MethodHandle to invoke the native code in downcall.
 */
public class CallArranger {

	/* Replace ProgrammableInvoker in OpenJDK with the implementation of ProgrammableInvoker specific to OpenJ9 */
	public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc) {
		MethodHandle handle = ProgrammableInvoker.getBoundMethodHandle(mt, cDesc);
		return handle;
	}

	/* Replace ProgrammableUpcallHandler in OpenJDK with the implementation of ProgrammableUpcallHandler specific to OpenJ9 */
	public static NativeSymbol arrangeUpcall(MethodHandle target, MethodType mt, FunctionDescriptor cDesc, ResourceScope scope) {
		throw new InternalError("arrangeUpcall is not yet implemented"); //$NON-NLS-1$
	}
}
