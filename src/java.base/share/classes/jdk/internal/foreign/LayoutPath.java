/*
 *  Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * This class provide support for constructing layout paths; that is, starting from a root path (see {@link #rootPath(MemoryLayout)}),
 * a path can be constructed by selecting layout elements using the selector methods provided by this class
 * (see {@link #sequenceElement()}, {@link #sequenceElement(long)}, {@link #sequenceElement(long, long)}, {@link #groupElement(String)}).
 * Once a path has been fully constructed, clients can ask for the offset associated with the layout element selected
 * by the path (see {@link #offset}), or obtain var handle to access the selected layout element
 * given an address pointing to a segment associated with the root layout (see {@link #dereferenceHandle()}).
 */
public class LayoutPath {

    private static final long[] EMPTY_STRIDES = new long[0];
    private static final long[] EMPTY_BOUNDS = new long[0];
    private static final MethodHandle[] EMPTY_DEREF_HANDLES = new MethodHandle[0];
    public static final MemoryLayout.PathElement[] EMPTY_PATH_ELEMENTS = new MemoryLayout.PathElement[0];

    private static final MethodHandle MH_ADD_SCALED_OFFSET;
    private static final MethodHandle MH_SLICE;
    private static final MethodHandle MH_SLICE_LAYOUT;
    private static final MethodHandle MH_CHECK_ENCL_LAYOUT;
    private static final MethodHandle MH_SEGMENT_RESIZE;
    private static final MethodHandle MH_ADD_EXACT;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_ADD_SCALED_OFFSET = lookup.findStatic(LayoutPath.class, "addScaledOffset",
                    MethodType.methodType(long.class, long.class, long.class, long.class, long.class));
            MH_SLICE = lookup.findVirtual(MemorySegment.class, "asSlice",
                    MethodType.methodType(MemorySegment.class, long.class, long.class));
            MH_SLICE_LAYOUT = lookup.findVirtual(MemorySegment.class, "asSlice",
                    MethodType.methodType(MemorySegment.class, long.class, MemoryLayout.class));
            MH_CHECK_ENCL_LAYOUT = lookup.findStatic(LayoutPath.class, "checkEnclosingLayout",
                    MethodType.methodType(void.class, MemorySegment.class, long.class, MemoryLayout.class));
            MH_SEGMENT_RESIZE = lookup.findStatic(LayoutPath.class, "resizeSegment",
                    MethodType.methodType(MemorySegment.class, MemorySegment.class));
            MH_ADD_EXACT = lookup.findStatic(Math.class, "addExact",
                    MethodType.methodType(long.class, long.class, long.class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private final MemoryLayout layout;
    private final long offset;
    private final LayoutPath enclosing;
    private final long[] strides;
    private final long[] bounds;
    private final MethodHandle[] derefAdapters;

    private LayoutPath(MemoryLayout layout, long offset, long[] strides, long[] bounds, MethodHandle[] derefAdapters, LayoutPath enclosing) {
        this.layout = layout;
        this.offset = offset;
        this.strides = strides;
        this.bounds = bounds;
        this.derefAdapters = derefAdapters;
        this.enclosing = enclosing;
    }

    // Layout path selector methods

    public LayoutPath sequenceElement() {
        SequenceLayout seq = requireSequenceLayout();
        MemoryLayout elem = seq.elementLayout();
        return LayoutPath.nestedPath(elem, offset, addStride(elem.byteSize()), addBound(seq.elementCount()), derefAdapters, this);
    }

    public LayoutPath sequenceElement(long start, long step) {
        SequenceLayout seq = requireSequenceLayout();
        checkSequenceBounds(seq, start);
        MemoryLayout elem = seq.elementLayout();
        long elemSize = elem.byteSize();
        long nelems = step > 0 ?
                seq.elementCount() - start :
                start + 1;
        long maxIndex = Math.ceilDiv(nelems, Math.abs(step));
        return LayoutPath.nestedPath(elem, offset + (start * elemSize),
                addStride(elemSize * step), addBound(maxIndex), derefAdapters, this);
    }

    public LayoutPath sequenceElement(long index) {
        SequenceLayout seq = requireSequenceLayout();
        checkSequenceBounds(seq, index);
        long elemSize = seq.elementLayout().byteSize();
        long elemOffset = elemSize * index;
        return LayoutPath.nestedPath(seq.elementLayout(), offset + elemOffset, strides, bounds, derefAdapters, this);
    }

    public LayoutPath groupElement(String name) {
        GroupLayout g = requireGroupLayout();
        long offset = 0;
        MemoryLayout elem = null;
        for (int i = 0; i < g.memberLayouts().size(); i++) {
            MemoryLayout l = g.memberLayouts().get(i);
            if (l.name().isPresent() &&
                l.name().get().equals(name)) {
                elem = l;
                break;
            } else if (g instanceof StructLayout) {
                offset += l.byteSize();
            }
        }
        if (elem == null) {
            throw badLayoutPath(
                    String.format("cannot resolve '%s' in layout %s", name, breadcrumbs()));
        }
        return LayoutPath.nestedPath(elem, this.offset + offset, strides, bounds, derefAdapters, this);
    }

    public LayoutPath groupElement(long index) {
        GroupLayout g = requireGroupLayout();
        long elemSize = g.memberLayouts().size();
        long offset = 0;
        MemoryLayout elem = null;
        for (int i = 0; i <= index; i++) {
            if (i == elemSize) {
                throw badLayoutPath(
                        String.format("cannot resolve element %d in layout: %s", index, breadcrumbs()));
            }
            elem = g.memberLayouts().get(i);
            if (g instanceof StructLayout && i < index) {
                offset += elem.byteSize();
            }
        }
        return LayoutPath.nestedPath(elem, this.offset + offset, strides, bounds, derefAdapters, this);
    }

    public LayoutPath derefElement() {
        if (!(layout instanceof AddressLayout addressLayout) ||
                addressLayout.targetLayout().isEmpty()) {
            throw badLayoutPath(
                    String.format("Cannot dereference layout: %s", breadcrumbs()));
        }
        MemoryLayout derefLayout = addressLayout.targetLayout().get();
        MethodHandle handle = dereferenceHandle(false).toMethodHandle(VarHandle.AccessMode.GET);
        handle = MethodHandles.filterReturnValue(handle, MH_SEGMENT_RESIZE);
        return derefPath(derefLayout, handle, this);
    }

    private static MemorySegment resizeSegment(MemorySegment segment) {
        // Avoid adapting for specific target layout. The check for the root layout
        // size and alignment will be inserted by LayoutPath::dereferenceHandle anyway.
        return Utils.longToAddress(segment.address(), Long.MAX_VALUE, 1);
    }

    // Layout path projections

    public long offset() {
        return offset;
    }

    public VarHandle dereferenceHandle() {
        return dereferenceHandle(true);
    }

    public VarHandle dereferenceHandle(boolean adapt) {
        if (!(layout instanceof ValueLayout valueLayout)) {
            throw new IllegalArgumentException(
                    String.format("Path does not select a value layout: %s", breadcrumbs()));
        }

        boolean constantOffset = strides.length == 0;
        // (MS, long, long) if variable offset, (MS, long) if constant offset
        VarHandle handle = Utils.makeRawSegmentViewVarHandle(rootLayout(), valueLayout, constantOffset, offset);
        if (!constantOffset) {
            MethodHandle offsetAdapter = offsetHandle();  // Adapter performs the bound checks
            offsetAdapter = MethodHandles.insertArguments(offsetAdapter, 0, 0L);
            handle = MethodHandles.collectCoordinates(handle, 2, offsetAdapter);    // (MS, long)
        }

        if (adapt) {
            if (derefAdapters.length > 0) {
                // plug up the base offset if we have at least 1 enclosing dereference
                handle = MethodHandles.insertCoordinates(handle, 1, 0);
            }
            for (int i = derefAdapters.length; i > 0; i--) {
                MethodHandle adapter = derefAdapters[i - 1];
                // the first/outermost adapter will have a base offset coordinate, the rest are constant 0
                if (i > 1) {
                    // plug in a constant 0 base offset for all but the outermost access in a deref chain
                    adapter = MethodHandles.insertArguments(adapter, 1, 0);
                }
                handle = MethodHandles.collectCoordinates(handle, 0, adapter);
            }
        }
        return handle;
    }

    @ForceInline
    private static long addScaledOffset(long base, long index, long stride, long bound) {
        Objects.checkIndex(index, bound);
        // note: the below can overflow, depending on 'base'. When constructing var handles
        // through the layout API, this is never the case, as the injected 'base' is always 0.
        return base + (stride * index);
    }

    public MethodHandle offsetHandle() {
        MethodHandle mh = MH_ADD_EXACT;
        for (int i = strides.length - 1; i >= 0; i--) {
            MethodHandle collector = MethodHandles.insertArguments(MH_ADD_SCALED_OFFSET, 2, strides[i], bounds[i]);
            // (J, J, ...) -> J to (J, J, J, ...) -> J
            // 1. the leading argument is the base offset (externally provided).
            // 2. index arguments are added. The last index correspond to the innermost layout.
            // 3. overflow can only occur at the outermost layer, due to the final addition with the base offset.
            // This is because the layout API ensures (by construction) that all offsets generated from layout paths
            // are always < Long.MAX_VALUE.
            mh = MethodHandles.collectArguments(mh, 1, collector);
        }
        return MethodHandles.insertArguments(mh, 1, offset);
    }

    private MemoryLayout rootLayout() {
        return enclosing != null ? enclosing.rootLayout() : this.layout;
    }

    public MethodHandle sliceHandle() {
        MethodHandle sliceHandle;
        if (enclosing != null) {
            // drop the alignment check for the accessed element, we check the root layout instead
            sliceHandle = MH_SLICE; // (MS, long, long) -> MS
            sliceHandle = MethodHandles.insertArguments(sliceHandle, 2, layout.byteSize()); // (MS, long) -> MS
        } else {
            sliceHandle = MH_SLICE_LAYOUT; // (MS, long, MemoryLayout) -> MS
            sliceHandle = MethodHandles.insertArguments(sliceHandle, 2, layout); // (MS, long) -> MS
        }
        sliceHandle = MethodHandles.collectArguments(sliceHandle, 1, offsetHandle()); // (MS, long, ...) -> MS

        if (enclosing != null) {
            // insert align check for the root layout on the initial MS + offset
            MethodType oldType = sliceHandle.type();
            MethodHandle alignCheck = MethodHandles.insertArguments(MH_CHECK_ENCL_LAYOUT, 2, rootLayout());
            sliceHandle = MethodHandles.collectArguments(sliceHandle, 0, alignCheck); // (MS, long, MS, long) -> MS
            int[] reorder = IntStream.concat(IntStream.of(0, 1), IntStream.range(0, oldType.parameterCount())).toArray();
            sliceHandle = MethodHandles.permuteArguments(sliceHandle, oldType, reorder); // (MS, long, ...) -> MS
        }

        return sliceHandle;
    }

    private static void checkEnclosingLayout(MemorySegment segment, long offset, MemoryLayout enclosing) {
        ((AbstractMemorySegmentImpl)segment).checkEnclosingLayout(offset, enclosing, true);
    }

    public MemoryLayout layout() {
        return layout;
    }

    // Layout path construction

    public static LayoutPath rootPath(MemoryLayout layout) {
        return new LayoutPath(layout, 0L, EMPTY_STRIDES, EMPTY_BOUNDS, EMPTY_DEREF_HANDLES, null);
    }

    private static LayoutPath nestedPath(MemoryLayout layout, long offset, long[] strides, long[] bounds, MethodHandle[] derefAdapters, LayoutPath encl) {
        return new LayoutPath(layout, offset, strides, bounds, derefAdapters, encl);
    }

    private static LayoutPath derefPath(MemoryLayout layout, MethodHandle handle, LayoutPath encl) {
        MethodHandle[] handles = Arrays.copyOf(encl.derefAdapters, encl.derefAdapters.length + 1);
        handles[encl.derefAdapters.length] = handle;
        return new LayoutPath(layout, 0L, EMPTY_STRIDES, EMPTY_BOUNDS, handles, null);
    }

    // Helper methods

    private SequenceLayout requireSequenceLayout() {
        return requireLayoutType(SequenceLayout.class, "sequence");
    }

    private GroupLayout requireGroupLayout() {
        return requireLayoutType(GroupLayout.class, "group");
    }

    private <T extends MemoryLayout> T requireLayoutType(Class<T> layoutClass, String name) {
        if (!layoutClass.isAssignableFrom(layout.getClass())) {
            throw badLayoutPath(
                    String.format("attempting to select a %s element from a non-%s layout: %s",
                            name, name, breadcrumbs()));
        }
        return layoutClass.cast(layout);
    }

    private void checkSequenceBounds(SequenceLayout seq, long index) {
        if (index >= seq.elementCount()) {
            throw badLayoutPath(String.format("sequence index out of bounds; index: %d, elementCount is %d for layout %s",
                    index, seq.elementCount(), breadcrumbs()));
        }
    }

    private static IllegalArgumentException badLayoutPath(String cause) {
        return new IllegalArgumentException("Bad layout path: " + cause);
    }

    private long[] addStride(long stride) {
        long[] newStrides = Arrays.copyOf(strides, strides.length + 1);
        newStrides[strides.length] = stride;
        return newStrides;
    }

    private long[] addBound(long maxIndex) {
        long[] newBounds = Arrays.copyOf(bounds, bounds.length + 1);
        newBounds[bounds.length] = maxIndex;
        return newBounds;
    }

    private String breadcrumbs() {
        return Stream.iterate(this, Objects::nonNull, lp -> lp.enclosing)
                .map(LayoutPath::layout)
                .map(Object::toString)
                .collect(joining(", selected from: "));
    }

    public record GroupElementByName(String name)
            implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        // Assert invariants
        public GroupElementByName {
            Objects.requireNonNull(name);
        }

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return layoutPath.groupElement(name);
        }

        @Override
        public String toString() {
            return "groupElement(\"" + name + "\")";
        }
    }

    public record GroupElementByIndex(long index)
            implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        // Assert invariants
        public GroupElementByIndex {
            if (index < 0) {
                throw new IllegalArgumentException("Index < 0");
            }
        }

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return layoutPath.groupElement(index);
        }

        @Override
        public String toString() {
            return "groupElement(" + index + ")";
        }

    }

    public record SequenceElementByIndex(long index)
            implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        // Assert invariants
        public SequenceElementByIndex {
            if (index < 0) {
                throw new IllegalArgumentException("Index < 0");
            }
        }

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return layoutPath.sequenceElement(index);
        }

        @Override
        public String toString() {
            return "sequenceElement(" + index + ")";
        }

    }

    public record SequenceElementByRange(long start, long step)
            implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        // Assert invariants
        public SequenceElementByRange {
            if (start < 0) {
                throw new IllegalArgumentException("Start index must be positive: " + start);
            }
            if (step == 0) {
                throw new IllegalArgumentException("Step must be != 0: " + step);
            }
        }

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return layoutPath.sequenceElement(start, step);
        }

        @Override
        public String toString() {
            return "sequenceElement(" + start + ", " + step + ")";
        }

    }

    public record SequenceElement()
            implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        private static final SequenceElement INSTANCE = new SequenceElement();

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return layoutPath.sequenceElement();
        }

        @Override
        public String toString() {
            return "sequenceElement()";
        }

        public static MemoryLayout.PathElement instance() {
            return INSTANCE;
        }

    }

    public record DereferenceElement()
            implements MemoryLayout.PathElement, UnaryOperator<LayoutPath> {

        private static final DereferenceElement INSTANCE = new DereferenceElement();

        @Override
        public LayoutPath apply(LayoutPath layoutPath) {
            return layoutPath.derefElement();
        }

        // Overriding here will ensure DereferenceElement will have a hash code
        // that is different from the hash code of SequenceElement.
        @Override
        public int hashCode() {
            return 31;
        }

        @Override
        public String toString() {
            return "dereferenceElement()";
        }

        public static MemoryLayout.PathElement instance() {
            return INSTANCE;
        }

    }

}
