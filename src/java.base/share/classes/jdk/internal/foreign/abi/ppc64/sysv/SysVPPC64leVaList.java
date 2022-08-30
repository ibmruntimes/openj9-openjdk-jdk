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
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.internal.foreign.abi.ppc64.TypeClass;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Scoped;
import static jdk.internal.foreign.PlatformLayouts.SysVPPC64le;

/**
 * This class implements VaList specific to Linux/ppc64le based on "64-Bit ELF V2 ABI
 * Specification: Power Architecture"(Revision 1.5) against the code of VaList on
 * x64/windows as the template.
 *
 * va_arg impl on Linux/ppc64le:
 * typedef void * va_list;
 *
 * Specifically, va_list is simply a pointer (similar to the va_list on x64/windows) to a buffer
 * with all supportted types of arugments, including struct (passed by value), pointer and 
 * primitive types, which are aligned with 8 bytes.
 */
public non-sealed class SysVPPC64leVaList implements VaList, Scoped {
	public static final Class<?> CARRIER = MemoryAddress.class;

	/* Every primitive/pointer occupies 8 bytes and structs are aligned
	 * with 8 bytes in the total size when stacking the va_list buffer. 
	 */
	private static final long VA_LIST_SLOT_BYTES = 8;
	private static final VaList EMPTY = new SharedUtils.EmptyVaList(MemoryAddress.NULL);

	private MemorySegment segment;
	private final MemorySession session;

	private SysVPPC64leVaList(MemorySegment segment, MemorySession session) {
		this.segment = segment;
		this.session = session;
	}

	public static final VaList empty() {
		return EMPTY;
	}

	@Override
	public int nextVarg(ValueLayout.OfInt layout) {
		return Math.toIntExact((long)readArg(layout));
	}

	@Override
	public long nextVarg(ValueLayout.OfLong layout) {
		return (long)readArg(layout);
	}

	@Override
	public double nextVarg(ValueLayout.OfDouble layout) {
		return (double)readArg(layout);
	}

	@Override
	public MemoryAddress nextVarg(ValueLayout.OfAddress layout) {
		return (MemoryAddress)readArg(layout);
	}

	@Override
	public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
		Objects.requireNonNull(allocator);
		return (MemorySegment)readArg(layout, allocator);
	}

	private static long getAlignedArgSize(MemoryLayout argLayout) {
		long argLayoutSize = VA_LIST_SLOT_BYTES; // Always aligned with 8 bytes for primitives/pointer by default

		/* As with primitives, a struct should aligned with 8 bytes */
		if (argLayout instanceof GroupLayout) {
			argLayoutSize = argLayout.byteSize();
			if ((argLayoutSize % VA_LIST_SLOT_BYTES) > 0) {
				argLayoutSize = (argLayoutSize / VA_LIST_SLOT_BYTES) * VA_LIST_SLOT_BYTES + VA_LIST_SLOT_BYTES;
			}
		}

		return argLayoutSize;
	}

	private Object readArg(MemoryLayout argLayout) {
		return readArg(argLayout, SharedUtils.THROWING_ALLOCATOR);
	}

	private Object readArg(MemoryLayout argLayout, SegmentAllocator allocator) {
		Objects.requireNonNull(argLayout);
		long argByteSize = VA_LIST_SLOT_BYTES; // Always aligned with 8 bytes for primitives/pointer by default
		Object argument = null;

		TypeClass typeClass = TypeClass.classifyLayout(argLayout);
		switch (typeClass) {
			case BASE, POINTER -> {
				VarHandle argHandle = TypeClass.classifyVarHandle((ValueLayout)argLayout);
				argument = argHandle.get(segment);
			}
			case STRUCT -> {
				argByteSize = getAlignedArgSize(argLayout);
				/* Copy the struct argument with the aligned size from the va_list buffer to allocated memory */
				argument = allocator.allocate(argByteSize).copyFrom(segment.asSlice(0, argByteSize));
			}
			default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
		}

		/* Move to the next argument in the va_list buffer */
		segment = segment.asSlice(argByteSize);
		return argument;
	}

	@Override
	public void skip(MemoryLayout... layouts) {
		Objects.requireNonNull(layouts);
		sessionImpl().checkValidState();
		Stream.of(layouts).forEach(Objects::requireNonNull);

		/* All primitves/pointer (aligned with 8 bytes) are directly stored
		 * in the va_list buffer and all elements of stuct are totally copied
		 * to the va_list buffer (instead of storing the va_list address), in
		 * which case we need to calculate the total byte size to be skipped
		 * in the va_list buffer.
		 */
		long totalArgsSize = Stream.of(layouts).reduce(0L,
				(accum, layout) -> accum + getAlignedArgSize(layout), Long::sum);
		segment = segment.asSlice(totalArgsSize);
	}

	public static VaList ofAddress(MemoryAddress addr, MemorySession session) {
		MemorySegment segment = MemorySegment.ofAddress(addr, Long.MAX_VALUE, session);
		return new SysVPPC64leVaList(segment, session);
	}

	@Override
	public MemorySession session() {
		return session;
	}

	@Override
	public VaList copy() {
		sessionImpl().checkValidState();
		return new SysVPPC64leVaList(segment, session);
	}

	@Override
	public MemoryAddress address() {
		return segment.address();
	}

	@Override
	public String toString() {
		return "SysVPPC64leVaList{" + segment.address() + '}';
	}

	static Builder builder(MemorySession session) {
		return new Builder(session);
	}

	public static non-sealed class Builder implements VaList.Builder {
		private final MemorySession session;
		private final List<SimpleVaArg> stackArgs = new ArrayList<>();

		public Builder(MemorySession session) {
			MemorySessionImpl.toSessionImpl(session).checkValidState();
			this.session = session;
		}

		private Builder setArg(MemoryLayout layout, Object value) {
			Objects.requireNonNull(layout);
			Objects.requireNonNull(value);
			stackArgs.add(new SimpleVaArg(layout, value));
			return this;
		}

		@Override
		public Builder addVarg(ValueLayout.OfInt layout, int value) {
			return setArg(layout, value);
		}

		@Override
		public Builder addVarg(ValueLayout.OfLong layout, long value) {
			return setArg(layout, value);
		}

		@Override
		public Builder addVarg(ValueLayout.OfDouble layout, double value) {
			return setArg(layout, value);
		}

		@Override
		public Builder addVarg(ValueLayout.OfAddress layout, Addressable value) {
			return setArg(layout, value.address());
		}

		@Override
		public Builder addVarg(GroupLayout layout, MemorySegment value) {
			return setArg(layout, value);
		}

		public VaList build() {
			if (stackArgs.isEmpty()) {
				return EMPTY;
			}

			/* All primitves/pointer (aligned with 8 bytes) are directly stored in the va_list buffer
			 * and all elements of stuct are totally copied to the va_list buffer (instead of storing
			 * the va_list address), in which  case we need to calculate the total byte size of the
			 * buffer to be allocated for va_list.
			 */
			long totalArgsSize = stackArgs.stream().reduce(0L,
					(accum, arg) -> accum + getAlignedArgSize(arg.layout), Long::sum);
			SegmentAllocator allocator = SegmentAllocator.newNativeArena(session);
			MemorySegment segment = allocator.allocate(totalArgsSize);
			MemorySegment cursorSegment = segment;

			for (SimpleVaArg arg : stackArgs) {
				MemoryLayout argLayout = arg.layout;
				TypeClass typeClass = TypeClass.classifyLayout(argLayout);

				switch (typeClass) {
					case BASE, POINTER -> {
						VarHandle argHandle = TypeClass.classifyVarHandle((ValueLayout)argLayout);
						argHandle.set(cursorSegment, arg.value);
						/* Move to the location for the next argument by 8 bytes */
						cursorSegment = cursorSegment.asSlice(VA_LIST_SLOT_BYTES);
					}
					case STRUCT -> {
						cursorSegment.copyFrom((MemorySegment)(arg.value));
						/* Move to the location for the next argument by the aligned struct size */
						cursorSegment = cursorSegment.asSlice(getAlignedArgSize(arg.layout));
					}
					default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
				}
			}
			return new SysVPPC64leVaList(segment, session);
		}
	}
}
