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

package jdk.internal.foreign.abi.ppc64.aix;

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
import static jdk.internal.foreign.PlatformLayouts.AIX;

/**
 * This class implements VaList specific to AIX/ppc64 based on the publisized ABI document at
 * https://www.ibm.com/docs/en/ssw_aix_72/pdf/assembler_pdf.pdf against the code of VaList on
 * x64/windows as the template.
 *
 * va_arg impl on AIX/ppc64:
 * typedef void * va_list;
 *
 * Specifically, va_list is simply a pointer (similar to the va_list on x64/windows) to a buffer
 * with all supportted types of arugments, including struct (passed by value), pointer and
 * primitive types, which are aligned with 8 bytes.
 */
public non-sealed class AixPPC64VaList implements VaList {

	/* Every primitive/pointer occupies 8 bytes and structs are aligned
	 * with 8 bytes in the total size when stacking the va_list buffer.
	 */
	private static final long VA_LIST_SLOT_BYTES = 8;
	private static final VaList EMPTY = new SharedUtils.EmptyVaList(MemorySegment.NULL);

	private MemorySegment segment;
	private final SegmentScope session;

	private AixPPC64VaList(MemorySegment segment, SegmentScope session) {
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
	public MemorySegment nextVarg(ValueLayout.OfAddress layout) {
		return (MemorySegment)readArg(layout);
	}

	@Override
	public MemorySegment nextVarg(GroupLayout layout, SegmentAllocator allocator) {
		return (MemorySegment)readArg(layout, allocator);
	}

	private Object readArg(MemoryLayout argLayout) {
		return readArg(argLayout, SharedUtils.THROWING_ALLOCATOR);
	}

	private Object readArg(MemoryLayout argLayout, SegmentAllocator allocator) {
		Objects.requireNonNull(argLayout);
		Objects.requireNonNull(allocator);
		Object argument = null;

		TypeClass typeClass = TypeClass.classifyLayout(argLayout);
		long argByteSize = getAlignedArgSize(argLayout);
		checkNextArgument(argLayout, argByteSize);

		switch (typeClass) {
			case PRIMITIVE, POINTER -> {
				VarHandle argHandle = TypeClass.classifyVarHandle((ValueLayout)argLayout);
				argument = argHandle.get(segment);
			}
			case STRUCT -> {
				/* With the smaller size of the allocated struct segment and the corresponding layout,
				 * it ensures the struct value is copied correctly from the va_list segment to the
				 * returned struct argument.
				 */
				MemorySegment structSegment = allocator.allocate(argLayout);
				long structByteSize = getSmallerStructArgSize(structSegment, argLayout);
				argument = structSegment.copyFrom(segment.asSlice(0, structByteSize));
			}
			default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
		}

		/* Move to the next argument in the va_list buffer */
		segment = segment.asSlice(argByteSize);
		return argument;
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

	/* Check whether the argument to be skipped exceeds the existing memory size in the VaList */
	private void checkNextArgument(MemoryLayout argLayout, long argByteSize) {
		if (argByteSize > segment.byteSize()) {
			throw SharedUtils.newVaListNSEE(argLayout);
		}
	}

	private static long getSmallerStructArgSize(MemorySegment structSegment, MemoryLayout structArgLayout) {
		return Math.min(structSegment.byteSize(), structArgLayout.byteSize());
	}

	@Override
	public void skip(MemoryLayout... layouts) {
		Objects.requireNonNull(layouts);
		((MemorySessionImpl)session).checkValidState();

		for (MemoryLayout layout : layouts) {
			Objects.requireNonNull(layout);
			long argByteSize = getAlignedArgSize(layout);
			checkNextArgument(layout, argByteSize);
			/* Skip to the next argument in the va_list buffer */
			segment = segment.asSlice(argByteSize);
		}
	}

	public static VaList ofAddress(long addr, SegmentScope session) {
		MemorySegment segment = MemorySegment.ofAddress(addr, Long.MAX_VALUE, session);
		return new AixPPC64VaList(segment, session);
	}

	@Override
	public VaList copy() {
		((MemorySessionImpl)session).checkValidState();
		return new AixPPC64VaList(segment, session);
	}

	@Override
	public MemorySegment segment() {
		/* The returned segment cannot be accessed. */
		return segment.asSlice(0, 0);
	}

	@Override
	public String toString() {
		return "AixPPC64VaList{" + segment.asSlice(0, 0) + '}';
	}

	static Builder builder(SegmentScope session) {
		return new Builder(session);
	}

	public static non-sealed class Builder implements VaList.Builder {
		private final SegmentScope session;
		private final List<SimpleVaArg> stackArgs = new ArrayList<>();

		public Builder(SegmentScope session) {
			((MemorySessionImpl)session).checkValidState();
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
		public Builder addVarg(ValueLayout.OfAddress layout, MemorySegment value) {
			return setArg(layout, value);
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
			 * the va_list address), in which case we need to calculate the total byte size of the
			 * buffer to be allocated for va_list.
			 */
			long totalArgsSize = stackArgs.stream().reduce(0L,
					(accum, arg) -> accum + getAlignedArgSize(arg.layout), Long::sum);
			SegmentAllocator allocator = SegmentAllocator.nativeAllocator(session);
			MemorySegment segment = allocator.allocate(totalArgsSize);
			MemorySegment cursorSegment = segment;

			for (SimpleVaArg arg : stackArgs) {
				Object argValue = arg.value;
				MemoryLayout argLayout = arg.layout;
				long argByteSize = getAlignedArgSize(argLayout);
				TypeClass typeClass = TypeClass.classifyLayout(argLayout);

				switch (typeClass) {
					case PRIMITIVE, POINTER -> {
						VarHandle argHandle = TypeClass.classifyVarHandle((ValueLayout)argLayout);
						argHandle.set(cursorSegment, argValue);
					}
					case STRUCT -> {
						/* With the smaller size of the struct argument and the corresponding layout,
						 * it ensures the struct value is copied correctly from the struct argument
						 * to the va_list.
						 */
						MemorySegment structSegment = (MemorySegment)argValue;
						long structByteSize = getSmallerStructArgSize(structSegment, argLayout);
						cursorSegment.copyFrom(structSegment.asSlice(0, structByteSize));
					}
					default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
				}
				/* Move to the next argument by the aligned size of the current argument */
				cursorSegment = cursorSegment.asSlice(argByteSize);
			}
			return new AixPPC64VaList(segment, session);
		}
	}
}
