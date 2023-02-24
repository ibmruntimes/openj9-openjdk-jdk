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
 * (c) Copyright IBM Corp. 2022, 2023 All Rights Reserved
 * ===========================================================================
 */

package jdk.internal.foreign.abi.s390x.sysv;

import java.lang.invoke.VarHandle;
import java.util.List;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import static java.lang.foreign.ValueLayout.*;

/**
 * This class enumerates three argument types for Linux/s390x against the implementation
 * of TypeClass on Linux/ppc64le.
 */
enum TypeClass {
	INTEGER, /* Intended for all integral primitive types */
	FLOAT,   /* Intended for float and double */
	POINTER,
	STRUCT,
	STRUCT_ONE_FLOAT; /* Intended for a struct with only one float or double element */

	static boolean isFloatingType(MemoryLayout layout) {
		boolean isFPR = false;

		if ((layout instanceof ValueLayout) && (classifyValueType((ValueLayout)layout) == FLOAT)
			|| (layout instanceof GroupLayout) && isStructWithOneFloat((GroupLayout)layout)
		) {
			isFPR = true;
		}

		return isFPR;
	}

	private static boolean isStructWithOneFloat(GroupLayout structLayout) {
		List<MemoryLayout> elemLayoutList = structLayout.memberLayouts();
		boolean hasOneFloat = false;

		if (elemLayoutList.size() == 1) {
			MemoryLayout elemLayout = elemLayoutList.get(0);
			if ((elemLayout instanceof ValueLayout)
				&& (classifyValueType((ValueLayout)elemLayout) == FLOAT)
			) {
				hasOneFloat = true;
			}
		}

		return hasOneFloat;
	}

	static VarHandle classifyVarHandle(MemoryLayout layout) {
		TypeClass typeClass = classifyLayout(layout);
		Class<?> carrier = ((typeClass == STRUCT) || (typeClass == STRUCT_ONE_FLOAT)) ?
								MemorySegment.class : ((ValueLayout)layout).carrier();
		VarHandle argHandle = null;

		/* According to the API Spec, all non-long integral types are promoted
		 * to long (8 bytes) while a float is promoted to double.
		 */
		if ((carrier == boolean.class)
			|| (carrier == byte.class)
			|| (carrier == char.class)
			|| (carrier == short.class)
			|| (carrier == int.class)
			|| (carrier == long.class)
		) {
			argHandle = JAVA_LONG.varHandle();
		} else if ((carrier == float.class)
			|| (carrier == double.class)
		) {
			argHandle = JAVA_DOUBLE.varHandle();
		/* VarHandle stores the address of struct which is greater than 8 bytes in size as per the ABI document */
		} else if (carrier == MemorySegment.class) {
			argHandle = ADDRESS.varHandle();
		} else {
			throw new IllegalStateException("Unspported carrier: " + carrier.getName());
		}

		return argHandle;
	}

	static TypeClass classifyLayout(MemoryLayout layout) {
		TypeClass layoutType = null;

		if (layout instanceof ValueLayout) {
			layoutType = classifyValueType((ValueLayout)layout);
		} else if (layout instanceof GroupLayout) {
			if (isStructWithOneFloat((GroupLayout)layout)) {
				layoutType = STRUCT_ONE_FLOAT;
			} else {
				layoutType = STRUCT;
			}
		} else {
			throw new IllegalArgumentException("Unsupported layout: " + layout);
		}

		return layoutType;
	}

	private static TypeClass classifyValueType(ValueLayout layout) {
		TypeClass layoutType = null;
		Class<?> carrier = layout.carrier();

		if ((carrier == boolean.class)
			|| (carrier == byte.class)
			|| (carrier == char.class)
			|| (carrier == short.class)
			|| (carrier == int.class)
			|| (carrier == long.class)
		) {
			layoutType = INTEGER;
		} else if ((carrier == float.class)
			|| (carrier == double.class)
		) {
			layoutType = FLOAT;
		} else if (carrier == MemorySegment.class) {
			layoutType = POINTER;
		} else {
			throw new IllegalStateException("Unspported carrier: " + carrier.getName());
		}

		return layoutType;
	}
}
