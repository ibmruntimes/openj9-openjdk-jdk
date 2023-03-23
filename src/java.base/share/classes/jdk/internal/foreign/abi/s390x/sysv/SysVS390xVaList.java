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

import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.misc.Unsafe;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static jdk.internal.foreign.PlatformLayouts.SysVS390x;
import static jdk.internal.foreign.abi.SharedUtils.SimpleVaArg;

/**
 * This class implements VaList specific to Linux/s390x based on "ELF Application Binary Interface
 * s390x Supplement"(Version 1.6, November 18, 2021) against the code of VaList on x64/sysv as the
 * template given the va_list's declaration on Linux/s390x is similar to Linux/x86_64 to some extent
 * even though the native implemenation of va_list is entirely different from each other.
 *
 * va_arg impl on Linux/s390x:
 *    typedef struct __va_list_tag {
 *        long __gpr;                   (offset)0      8 bytes (for r2-r6)
 *        long __fpr;                   (offset)8      8 bytes (for f0, f2, f4, and f6)
 *        void *__overflow_arg_area;    (offset)16     8 bytes
 *        void *__regSaveArea;        (offset)24     8 bytes
 *    } va_list[1];
 *
 * To be specific, the ABI document defines va_list to be equivalent to a structure with four
 * doubleword members (totally 32 bytes), in which:
 * 1) __gpr holds the starting number(0-5) of general argument registers(r2-r6) that have been used,
 *   which means 0 for r2, 1 for r3, 2 for r4, 3 for r5, and 4 for r6.
 * 2) __fpr holds the staring number(0-4) of floating-point argument registers(f0, f2, f4, and f6)
 *  that has been used, which means 0 for f0, 1 for f2, 2 for f4 and 3 for f6.
 * 3) __overflow_arg_area points to the first "overflow argument"(passed via the parameter area)
 *   after __regSaveArea at offset 160.
 * 4) __regSaveArea points to the start of a 160-byte memory region that contains the saved values
 *   of all argument registers, with the general registers(r2-r6) starting at offset 16 and the
 *   floating-point registers(f0, f2, f4, and f6) starting at offset 128.
 */
public non-sealed class SysVS390xVaList implements VaList {
	private static final Unsafe unsafe = Unsafe.getUnsafe();

	private static final GroupLayout LAYOUT_VA_LIST = MemoryLayout.structLayout(
		SysVS390x.C_LONG.withName("__gpr"),
		SysVS390x.C_LONG.withName("__fpr"),
		SysVS390x.C_POINTER.withName("__overflow_arg_area"),
		SysVS390x.C_POINTER.withName("__regSaveArea")
	).withName("__va_list_tag");

	private static final MemoryLayout GP_REG = MemoryLayout.paddingLayout(64).withBitAlignment(64);
	private static final MemoryLayout FP_REG = MemoryLayout.paddingLayout(64).withBitAlignment(64);

	/* The unused area is 16 bytes in size. */
	private static final MemoryLayout UNUSED_AREA = MemoryLayout.paddingLayout(16 * 8)
											.withBitAlignment(64).withName("UnusedArea");

	/* The other register save area is 72 bytes in size. */
	private static final MemoryLayout OTHER_REG_SAVE_AREA = MemoryLayout.paddingLayout(72 * 8)
													.withBitAlignment(64).withName("OtherRegSaveArea");


	/* The Parameter Area consists of a 160-byte register save area and the overflow area from offset 160,
	 * in which the layout of __regSaveArea is illustated as follows:
	 *  +----------------------------+
	 *  |       Parameter slot N     |
	 *  |----------------------------|
	 *  |            ... ...         |
	 *  |----------------------------|
	 *  |       Parameter slot 2     |
	 *  |----------------------------|
	 *  |       Parameter slot 1     | Overflow arguments
	 *  |----------------------------| Offset 160
	 *  |        f0, f2, 4, f6       | Floating-point register save area
	 *  |----------------------------| Offset 128
	 *  |  Other register save area  |
	 *  |----------------------------| Offset 56
	 *  |      r2, r3, r4, r5, r6    | General register save area
	 *  |----------------------------| Offset 16
	 *  |   Unused/Back chain slot   |
	 *  +----------------------------+ Offset 0
	 *  |<-----------8 bytes ------->|
	 */
	private static final GroupLayout LAYOUT_REG_SAVE_AREA = MemoryLayout.structLayout(
			UNUSED_AREA,           /* 16 bytes */
			GP_REG.withName("r2"), /* #0 */
			GP_REG.withName("r3"), /* #1 */
			GP_REG.withName("r4"), /* #3 */
			GP_REG.withName("r5"), /* #4 */
			GP_REG.withName("r6"), /* #5 */
			OTHER_REG_SAVE_AREA,   /* 72 bytes */
			FP_REG.withName("f0"), /* #0 */
			FP_REG.withName("f2"), /* #1 */
			FP_REG.withName("f4"), /* #2 */
			FP_REG.withName("f6")  /* #3 */
		);

	/* The starting offset of the general register save area. */
	private static final long GPR_OFFSET = LAYOUT_REG_SAVE_AREA.byteOffset(groupElement("r2"));
	/* The starting offset of the floating-point register save area. */
	private static final long FPR_OFFSET = LAYOUT_REG_SAVE_AREA.byteOffset(groupElement("f0"));

	private static final long MAX_GPR_NUM = 5; /* 5 8-byte general registers (r2-r6) being used */
	private static final long MAX_FPR_NUM = 4; /* 4 8-byte floating-point registers(f0, f2, f4, f6) being used */

	private static final VarHandle VH_GPR_NO = LAYOUT_VA_LIST.varHandle(groupElement("__gpr"));
	private static final VarHandle VH_FPR_NO = LAYOUT_VA_LIST.varHandle(groupElement("__fpr"));
	private static final VarHandle VH_OVERFLOW_ARG_AREA = LAYOUT_VA_LIST.varHandle(groupElement("__overflow_arg_area"));
	private static final VarHandle VH_REG_SAVE_AREA = LAYOUT_VA_LIST.varHandle(groupElement("__regSaveArea"));

	/* Every argument slot occpuies 8 bytes as each stack frame
	 * is aligned on an 8-byte boundary as per the ABI document.
	 */
	private static final long VA_LIST_SLOT_BYTES = 8;

	private static final long STRUCT_ARG_SIZE_1_BYTE = 1;
	private static final long STRUCT_ARG_SIZE_2_BYTES = 2;
	private static final long STRUCT_ARG_SIZE_4_BYTES = 4;
	private static final long STRUCT_ARG_SIZE_8_BYTES = 8;

	private static final VaList EMPTY = new SharedUtils.EmptyVaList(emptyListAddress());

	private final MemorySegment segment;
	private final MemorySegment regSaveAreaOfVaList;
	private final MemorySegment overflowArgAreaOfVaList;
	private MemorySegment gpRegSaveArea;
	private MemorySegment fpRegSaveArea;
	private MemorySegment overflowAreaCursor;

	private SysVS390xVaList(MemorySegment segment, MemorySegment gpRegSaveArea, MemorySegment fpRegSaveArea, MemorySegment overflowArgArea) {
		this.segment = segment;
		this.gpRegSaveArea = gpRegSaveArea;
		this.fpRegSaveArea = fpRegSaveArea;
		this.overflowAreaCursor = overflowArgArea;
		this.regSaveAreaOfVaList = MemorySegment.ofAddress(((MemorySegment)VH_REG_SAVE_AREA.get(segment)).address(),
															LAYOUT_REG_SAVE_AREA.byteSize(), segment.scope());
		this.overflowArgAreaOfVaList = MemorySegment.ofAddress(((MemorySegment)VH_OVERFLOW_ARG_AREA.get(segment)).address(),
																Long.MAX_VALUE, segment.scope());
	}

	private static MemorySegment emptyListAddress() {
		MemorySegment vaListSegment = MemorySegment.allocateNative(LAYOUT_VA_LIST, SegmentScope.global());
		VH_GPR_NO.set(vaListSegment, 0);
		VH_FPR_NO.set(vaListSegment, 0);
		VH_OVERFLOW_ARG_AREA.set(vaListSegment, MemorySegment.NULL);
		VH_REG_SAVE_AREA.set(vaListSegment, MemorySegment.NULL);
		return vaListSegment.asSlice(0, 0);
	}

	public static VaList empty() {
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
		return (MemorySegment)readArg(layout, allocator, true);
	}

	@Override
	public void skip(MemoryLayout... layouts) {
		Objects.requireNonNull(layouts);
		((MemorySessionImpl)segment.scope()).checkValidState();
		for (MemoryLayout layout : layouts) {
			readArg(layout, SharedUtils.THROWING_ALLOCATOR, false);
		}
	}

	private Object readArg(MemoryLayout layout) {
		return readArg(layout, SharedUtils.THROWING_ALLOCATOR, true);
	}

	private Object readArg(MemoryLayout layout, SegmentAllocator allocator, boolean isRead) {
		Objects.requireNonNull(layout);
		Objects.requireNonNull(allocator);
		TypeClass typeClass = TypeClass.classifyLayout(layout);
		long nextGprNo = currentGprNo() + 1;
		long nextFprNo = currentFprNo() + 1;
		Object argument = null;

		if (isRegOverflow(TypeClass.isFloatingType(layout), nextGprNo, nextFprNo)) {
			checkOverFlowAreaElement(layout);
			if (isRead) {
				argument = getArgFromMemoryArea(layout, overflowAreaCursor, allocator, true);
			}
			/* Move to the next argument by 8 bytes in the overflow area. */
			overflowAreaCursor = overflowAreaCursor.asSlice(VA_LIST_SLOT_BYTES);
		} else {
			checkRegSaveAreaElement(layout);
			switch (typeClass) {
				case INTEGER, POINTER, STRUCT -> {
					if (isRead) {
						argument = getArgFromMemoryArea(layout, gpRegSaveArea, allocator, false);
					}
					/* Move to the next argument in the general register area. */
					moveToNextArgOfGprArea(nextGprNo);
				}
				case FLOAT, STRUCT_ONE_FLOAT -> {
					if (isRead) {
						argument = getArgFromMemoryArea(layout, fpRegSaveArea, allocator, false);
					}
					/* Move to the next argument in the floating-point register area. */
					moveToNextArgOfFprArea(nextFprNo);
				}
				default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
			}
		}

		return argument;
	}

	/* Check whether the next argument should be stored in the overflow argument area or not.
	 *
	 * Note:
	 * 1)primitives, pointers or structs are stored in the overflow area when
	 *   the general register save area is full.
	 * 2)floats/doubles are also stored in the the overflow area when the
	 *   floating-point register save area is full.
	 */
	private static boolean isRegOverflow(boolean isFPR, long usedGprNum, long usedFprNum) {
		return ((!isFPR && (usedGprNum > MAX_GPR_NUM))
				|| (isFPR && (usedFprNum > MAX_FPR_NUM)));
	}

	private void checkOverFlowAreaElement(MemoryLayout layout) {
		if (overflowAreaCursor.byteSize() < VA_LIST_SLOT_BYTES) {
			throw SharedUtils.newVaListNSEE(layout);
		}
	}

	private void checkRegSaveAreaElement(MemoryLayout layout) {
		boolean isFPR = TypeClass.isFloatingType(layout);
		if ((!isFPR && (gpRegSaveArea.byteSize() < VA_LIST_SLOT_BYTES))
			|| (isFPR && (fpRegSaveArea.byteSize() < VA_LIST_SLOT_BYTES))
		) {
			throw SharedUtils.newVaListNSEE(layout);
		}
	}

	/* Obtain the argument value from the specified memory area of VaList. */
	private Object getArgFromMemoryArea(MemoryLayout layout, MemorySegment argAreaSegment, SegmentAllocator allocator, boolean isOverflowArea) {
		TypeClass typeClass = TypeClass.classifyLayout(layout);
		VarHandle argHandle = TypeClass.classifyVarHandle(layout);
		Object argument = null;

		switch (typeClass) {
			case STRUCT_ONE_FLOAT -> {
				long struArgSize = layout.byteSize();
				long rightShiftBytes = isOverflowArea ? (VA_LIST_SLOT_BYTES - struArgSize) : 0;
				MemorySegment struArgSegment = allocator.allocate(VA_LIST_SLOT_BYTES);
				argument = struArgSegment.copyFrom(argAreaSegment.asSlice(rightShiftBytes, struArgSize));
			}
			case STRUCT -> {
				/* There are two cases in handling struct arguments in the general register save
				 * area or the overflow argument area:
				 * 1)construct the struct with its address stored in a 8-byte stack slot of the
				 *   memory area when the struct size is 3, 5, 6, 7 bytes or greater than 8 bytes.
				 * 2)obtain all elements of the struct by copying them to the specified location
				 *   when the struct's size is 1, 2, 4, 8 bytes.
				 */
				GroupLayout struLayout = (GroupLayout)layout;
				if (isStruAddrRequired(struLayout)) {
					long struArgSize = getAlignedStructSize(struLayout);
					MemorySegment struAddr = (MemorySegment)argHandle.get(argAreaSegment);
					MemorySegment struArgSegment = MemorySegment.ofAddress(struAddr.address(), struArgSize, argAreaSegment.scope());
					argument = allocator.allocate(struArgSize).copyFrom(struArgSegment);
				} else {
					long struArgSize = struLayout.byteSize();
					MemorySegment struArgSegment = allocator.allocate(VA_LIST_SLOT_BYTES);
					argument = struArgSegment.copyFrom(argAreaSegment.asSlice(VA_LIST_SLOT_BYTES - struArgSize, struArgSize));
				}
			}
			case INTEGER, POINTER, FLOAT -> {
				/* A primitive/pointer is stored in a 8-byte stack slot of the memory area. */
				argument = argHandle.get(argAreaSegment);
			}
			default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
		}

		return argument;
	}

	/* Check whether to store or obtain the struct's address in the memory area,
	 * depending upon the size of struct.
	 *
	 * Note:
	 * According to the ABI document, the struct's address is required only when
	 * the struct's size is 3, 5, 6, 7 bytes or greater than 8 bytes; otherwise,
	 * the elements of struct are copied to the memory area.
	 */
	private static boolean isStruAddrRequired(GroupLayout structLayout) {
		long struArgSize = structLayout.byteSize();
		boolean required = false;

		if ((struArgSize != STRUCT_ARG_SIZE_1_BYTE)
			&& (struArgSize != STRUCT_ARG_SIZE_2_BYTES)
			&& (struArgSize != STRUCT_ARG_SIZE_4_BYTES)
			&& (struArgSize != STRUCT_ARG_SIZE_8_BYTES)
		) {
			required = true;
		}

		return required;
	}

	/* Only a struct with its address stored in the slot needs to be aligned by 8 bytes to
	 * ensure the elements of structs are correctly copied to the specified memory area.
	 */
	private static long getAlignedStructSize(GroupLayout structLayout) {
		long struArgSize = structLayout.byteSize();

		if ((struArgSize % VA_LIST_SLOT_BYTES) != 0) {
			struArgSize = (struArgSize / VA_LIST_SLOT_BYTES) * VA_LIST_SLOT_BYTES + VA_LIST_SLOT_BYTES;
		}

		return struArgSize;
	}

	private long currentGprNo() {
		return (long)VH_GPR_NO.get(segment);
	}

	private long currentFprNo() {
		return (long)VH_FPR_NO.get(segment);
	}

	private void moveToNextArgOfGprArea(long nextGprNo) {
		VH_GPR_NO.set(segment, nextGprNo);
		/* Move to the next argument by 8 bytes in the general register area. */
		gpRegSaveArea = gpRegSaveArea.asSlice(VA_LIST_SLOT_BYTES);
	}

	private void moveToNextArgOfFprArea(long nextFprNo) {
		VH_FPR_NO.set(segment, nextFprNo);
		/* Move to the next argument by 8 bytes in the floating-point register area. */
		fpRegSaveArea = fpRegSaveArea.asSlice(VA_LIST_SLOT_BYTES);
	}

	public static VaList ofAddress(long address, SegmentScope session) {
		MemorySegment segment = MemorySegment.ofAddress(address, LAYOUT_VA_LIST.byteSize(), session);
		MemorySegment regSaveAreaOfVaList = MemorySegment.ofAddress(((MemorySegment)VH_REG_SAVE_AREA.get(segment)).address(),
															LAYOUT_REG_SAVE_AREA.byteSize(), session);
		MemorySegment overflowArgAreaOfVaList = MemorySegment.ofAddress(((MemorySegment)VH_OVERFLOW_ARG_AREA.get(segment)).address(),
																Long.MAX_VALUE, session);

		long initGprNo = (long)VH_GPR_NO.get(segment);
		long initFprNo = (long)VH_FPR_NO.get(segment);
		/* The GPR and FPR memory area starts at the offset which is calculated
		 * with the initial GPR/FPR number specified in va_list.
		 */
		MemorySegment gpRegSaveArea = regSaveAreaOfVaList.asSlice(GPR_OFFSET + initGprNo * VA_LIST_SLOT_BYTES,
															(MAX_GPR_NUM  - initGprNo) * VA_LIST_SLOT_BYTES);
		MemorySegment fpRegSaveArea = regSaveAreaOfVaList.asSlice(FPR_OFFSET + initFprNo * VA_LIST_SLOT_BYTES,
															(MAX_FPR_NUM - initFprNo) * VA_LIST_SLOT_BYTES);

		return new SysVS390xVaList(segment, gpRegSaveArea, fpRegSaveArea, overflowArgAreaOfVaList);
	}

	@Override
	public VaList copy() {
		MemorySegment copySegment = MemorySegment.allocateNative(LAYOUT_VA_LIST, segment.scope()).copyFrom(segment);
		return new SysVS390xVaList(copySegment, gpRegSaveArea, fpRegSaveArea, overflowAreaCursor);
	}

	@Override
	public MemorySegment segment() {
		/* The returned segment cannot be accessed. */
		return segment.asSlice(0, 0);
	}

	@Override
	public String toString() {
		return "SysVS390xVaList{"
				+ "__gpr=" + currentGprNo()
				+ ", __fpr=" + currentFprNo()
				+ ", __overflow_arg_area=" + overflowArgAreaOfVaList
				+ ", __regSaveArea=" + regSaveAreaOfVaList
				+ '}';
	}

	static SysVS390xVaList.Builder builder(SegmentScope session) {
		return new SysVS390xVaList.Builder(session);
	}

	public static non-sealed class Builder implements VaList.Builder {
		private final SegmentScope session;
		private final List<SimpleVaArg> gprArgs = new ArrayList<>();
		private final List<SimpleVaArg> fprArgs = new ArrayList<>();
		private final List<SimpleVaArg> overflowArgs = new ArrayList<>();

		public Builder(SegmentScope session) {
			((MemorySessionImpl)session).checkValidState();
			this.session = session;
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

		private Builder setArg(MemoryLayout layout, Object value) {
			Objects.requireNonNull(layout);
			Objects.requireNonNull(value);

			if (isRegOverflow(TypeClass.isFloatingType(layout), gprArgs.size() + 1, fprArgs.size() + 1)) {
				overflowArgs.add(new SimpleVaArg(layout, value));
			} else {
				TypeClass typeClass = TypeClass.classifyLayout(layout);
				switch (typeClass) {
					case INTEGER, POINTER, STRUCT -> {
						gprArgs.add(new SimpleVaArg(layout, value));
					}
					case FLOAT, STRUCT_ONE_FLOAT -> {
						fprArgs.add(new SimpleVaArg(layout, value));
					}
					default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
				}
			}

			return this;
		}

		private boolean isEmpty() {
			return gprArgs.isEmpty() && fprArgs.isEmpty() && overflowArgs.isEmpty();
		}

		private void storeArgToMemoryArea(List<SimpleVaArg> vaListArgs, MemorySegment argAreaSegment, boolean isOverflowArea) {
			MemorySegment argAreaCursor = argAreaSegment;

			for (SimpleVaArg arg : vaListArgs) {
				MemoryLayout layout = arg.layout;
				Object argValue = arg.value;
				TypeClass typeClass = TypeClass.classifyLayout(layout);
				VarHandle argHandle = TypeClass.classifyVarHandle(layout);

				switch (typeClass) {
					case STRUCT_ONE_FLOAT, STRUCT -> {
						MemorySegment struArgValue = (MemorySegment)argValue;
						/* Use the layout size for the requested struct when the size of the allocated segment
						 * for struct is greater than the layout size.
						 */
						long struArgSize = (struArgValue.byteSize() > layout.byteSize()) ?
													layout.byteSize() : struArgValue.byteSize();

						if (typeClass == TypeClass.STRUCT_ONE_FLOAT) {
							/* Extend the float value right aligned into a 8-byte stack slot in the overflow area;
							 * otherwise, it is left aligned to a 8-byte slot in the floating-point register area.
							 */
							long rightShiftBytes = isOverflowArea ? (VA_LIST_SLOT_BYTES - struArgSize) : 0;
							argAreaCursor.asSlice(rightShiftBytes, struArgSize).copyFrom(struArgValue.asSlice(0, struArgSize));
						} else {
							/* There are two cases in handling struct arguments in the general register area
							 * or the overflow argument area when the general register area is full:
							 * 1)store the struct's address when the struct size is greater than 8 bytes
							 *   or 3, 5, 6, 7 bytes if less than 8 bytes.
							 * 2)store all elements of struct by extending it to 8 bytes with the padding
							 *   on the left when the struct's size is 1, 2, 4, 8 bytes.
							 */
							if (isStruAddrRequired((GroupLayout)layout)) {
								argHandle.set(argAreaCursor, struArgValue.asSlice(0, 0));
							} else {
								argAreaCursor.asSlice(VA_LIST_SLOT_BYTES - struArgSize, struArgSize).copyFrom(struArgValue);
							}
						}
					}
					case INTEGER, POINTER, FLOAT -> {
						argHandle.set(argAreaCursor, argValue);
					}
					default -> throw new IllegalStateException("Unsupported TypeClass: " + typeClass);
				}

				/* Move to the next argument by 8 bytes. */
				argAreaCursor = argAreaCursor.asSlice(VA_LIST_SLOT_BYTES);
			}
		}

		public VaList build() {
			if (isEmpty()) {
				return EMPTY;
			}

			long regSaveAreaSize = LAYOUT_REG_SAVE_AREA.byteSize();
			long overflowAreaSize = overflowArgs.size() * VA_LIST_SLOT_BYTES;
			SegmentAllocator allocator = SegmentAllocator.nativeAllocator(session);
			MemorySegment vaListSegment = allocator.allocate(LAYOUT_VA_LIST);
			MemorySegment vaArgArea = allocator.allocate(regSaveAreaSize + overflowAreaSize);

			MemorySegment regSaveArea = vaArgArea.asSlice(0, regSaveAreaSize);
			MemorySegment gpRegSaveArea = regSaveArea.asSlice(GPR_OFFSET, gprArgs.size() * VA_LIST_SLOT_BYTES);
			MemorySegment fpRegSaveArea = regSaveArea.asSlice(FPR_OFFSET, fprArgs.size() * VA_LIST_SLOT_BYTES);
			/* The overflow area is located at offset 160 after the register save area. */
			MemorySegment overflowArgArea = vaArgArea.asSlice(regSaveAreaSize, overflowAreaSize);

			storeArgToMemoryArea(gprArgs, gpRegSaveArea, false);
			storeArgToMemoryArea(fprArgs, fpRegSaveArea, false);
			storeArgToMemoryArea(overflowArgs, overflowArgArea, true);

			/* Set va_list with all required information so as to ensure va_list is correctly accessed in native. */
			VH_GPR_NO.set(vaListSegment, 0);
			VH_FPR_NO.set(vaListSegment, 0);
			VH_OVERFLOW_ARG_AREA.set(vaListSegment, overflowArgArea.asSlice(0, 0));
			VH_REG_SAVE_AREA.set(vaListSegment, regSaveArea.asSlice(0, 0));

			return new SysVS390xVaList(vaListSegment, gpRegSaveArea, fpRegSaveArea, overflowArgArea);
		}
	}
}
