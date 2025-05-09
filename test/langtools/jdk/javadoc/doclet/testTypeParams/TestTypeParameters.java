/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug      4927167 4974929 6381729 7010344 8025633 8081854 8182765 8187288 8261976 8313931
 * @summary  When the type parameters are more than 10 characters in length,
 *           make sure there is a line break between type params and return type
 *           in member summary. Also, test for type parameter links in package-summary and
 *           class-use pages. The class/annotation pages should check for type
 *           parameter links in the class/annotation signature section when -linksource is set.
 *           Verify that generic type parameters on constructors are documented.
 * @library  ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    javadoc.tester.*
 * @run main TestTypeParameters
 */

import javadoc.tester.JavadocTester;

public class TestTypeParameters extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestTypeParameters();
        tester.runTests();
    }

    @Test
    public void test1() {
        javadoc("-d", "out-1",
                "-use",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/C.html", true,
                """
                    <div class="col-first odd-row-color method-summary-table method-summary-tab\
                    le-tab2 method-summary-table-tab4"><code>&lt;W extends java.lang.String, \
                    V extends java.util.List&gt;<br>java.lang.Object</code></div>""",
                "<code>&lt;T&gt;&nbsp;java.lang.Object</code>");

        checkOutput("pkg/package-summary.html", true,
                """
                    C</a>&lt;E extends <a href="Parent.html" title="class in pkg">Parent</a>&gt;""");

        checkOutput("pkg/class-use/Foo4.html", true,
                """
                    <a href="../ClassUseTest3.html" class="type-name-link" title="class in pkg">Clas\
                    sUseTest3</a>&lt;T extends <a href="../ParamTest2.html" title="class in pkg">Par\
                    amTest2</a>&lt;java.util.List&lt;? extends <a href="../Foo4.html" title="class i\
                    n pkg">Foo4</a>&gt;&gt;&gt;""");

        // Nested type parameters
        checkOutput("pkg/C.html", true,
                """
                    <section class="detail" id="formatDetails(java.util.Collection,java.util.Collection)">
                    <h3>formatDetails</h3>""");
    }

    @Test
    public void test2() {
        javadoc("-d", "out-2",
                "-linksource",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/ClassUseTest3.html", true,
                """
                    public class </span><span class="element-name"><a href="../src-html/pkg/ClassUse\
                    Test3.html#line-28">ClassUseTest3</a>&lt;T extends <a href="ParamTest2.html" tit\
                    le="class in pkg">ParamTest2</a>&lt;java.util.List&lt;? extends <a href="Foo4.ht\
                    ml" title="class in pkg">Foo4</a>&gt;&gt;&gt;""");
    }

    @Test
    public void test3() {
        javadoc("-d", "out-3",
                "-Xdoclint:none",
                "--no-platform-links",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/CtorTypeParam.html", true,
                """
                    <div class="col-first even-row-color"><code>&nbsp;&lt;T extends java.lang.Runnable&gt;<br></code></div>
                    <div class="col-constructor-name even-row-color"><code>\
                    <a href="#%3Cinit%3E()" class="member-name-link">CtorTypeParam</a>()</code></div>
                    <div class="col-last even-row-color">
                    <div class="block">Generic constructor.</div>""",
                """
                    <div class="member-signature"><span class="modifiers">public</span>\
                    &nbsp;<span class="type-parameters">&lt;T extends java.lang.Runnable&gt;</span>\
                    &nbsp;<span class="element-name">CtorTypeParam</span>()</div>""",
                """
                    <a href="#%3Cinit%3E()-type-param-T"><code>T</code></a>""",
                """
                    <dt>Type Parameters:</dt>
                    <dd><span id="&lt;init&gt;()-type-param-T"><code>T</code> - the type parameter</span></dd>""",
                """
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="tag-list">
                    <li><a href="#%3Cinit%3E()-type-param-T">link to type parameter</a></li>
                    </ul>""");
    }
}
