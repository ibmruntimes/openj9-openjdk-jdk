/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

package xpath;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;

/*
 * @test
 * @bug 4992788
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm xpath.Bug4992788
 * @summary Test XPath.evaluate(expression,source,returnType) throws NPE if source is null.
 */
public class Bug4992788 {

    private static String expression = "/widgets/widget[@name='a']/@quantity";

    // test for XPath.evaluate(java.lang.String expression, InputSource source)
    // - default returnType is String
    // source is null , should throw NPE
    @Test
    public void testXPath23() throws Exception {
        try {
            createXPath().evaluate(expression, (InputSource) null);
            Assert.fail();
        } catch (NullPointerException e) {
            ; // as expected
        }
    }

    // test for XPath.evaluate(java.lang.String expression, InputSource source,
    // QName returnType)
    // source is null , should throw NPE
    @Test
    public void testXPath28() throws Exception {
        try {
            createXPath().evaluate(expression, (InputSource) null, XPathConstants.STRING);
            Assert.fail();
        } catch (NullPointerException e) {
            ; // as expected
        }
    }

    private XPath createXPath() throws XPathFactoryConfigurationException {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        Assert.assertNotNull(xpathFactory);
        XPath xpath = xpathFactory.newXPath();
        Assert.assertNotNull(xpath);
        return xpath;
    }
}
