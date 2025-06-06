/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * (c) Copyright IBM Corp. 2025, 2025 All Rights Reserved
 * ===========================================================================
 */

import com.sun.beans.finder.MethodFinder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * @test
 * @bug 8028054
 * @summary Tests that cached methods have synchronized access
 * @author Sergey Malenkov
 * @modules java.desktop/com.sun.beans.finder
 * @compile -XDignore.symbol.file TestMethodFinder.java
 * @run main/timeout=300 TestMethodFinder
 */

public class TestMethodFinder {
    public static void main(String[] args) throws Exception {
        List<Class<?>> classes = Task.getClasses(4000);
        List<Method> methods = new ArrayList<>();
        for (Class<?> type : classes) {
            Collections.addAll(methods, type.getMethods());
        }
        Task.print("found " + methods.size() + " methods in " + classes.size() + " classes");

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(new Task<Method>(methods) {
                @Override
                protected void process(Method method) throws NoSuchMethodException {
                    MethodFinder.findMethod(method.getDeclaringClass(), method.getName(), method.getParameterTypes());
                }
            });
        }
        int alarm = 0;
        while (true) {
            int alive = 0;
            int working = 0;
            for (Task task : tasks) {
                if (task.isWorking()) {
                    working++;
                    alive++;
                } else if (task.isAlive()) {
                    alive++;
                }
            }
            if (alive == 0) {
                break;
            }
            Task.print(working + " out of " + alive + " threads are working");
            if ((working == 0) && (++alarm == 10)) {
                throw new RuntimeException("FAIL - DEADLOCK DETECTED");
            }
            Thread.sleep(1000);
        }
    }
}
