/*-
 * ========================LICENSE_START=================================
 * smooks-routing-cartridge
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 * 
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 * 
 * ======================================================================
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * ======================================================================
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.cartridges.routing.basic;

import org.junit.Test;
import static org.junit.Assert.*;

import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.smooks.FilterSettings;
import org.smooks.Smooks;
import org.smooks.SmooksException;
import org.smooks.container.ExecutionContext;
import org.smooks.delivery.dom.DOMVisitAfter;
import org.smooks.delivery.sax.SAXElement;
import org.smooks.delivery.sax.SAXVisitAfter;
import org.smooks.payload.JavaResult;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
public class FragmentSerializerTest {

    @Test
    public void test_children_only_SAX() throws IOException, SAXException {
    	test_children_only(FilterSettings.DEFAULT_SAX);
    }

    @Test
    public void test_children_only_DOM() throws IOException, SAXException {
    	test_children_only(FilterSettings.DEFAULT_DOM);
    }

    private void test_children_only(FilterSettings filterSettings) throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-config-01.xml"));
        StreamSource source = new StreamSource(getClass().getResourceAsStream("input-message-01.xml"));
        JavaResult result = new JavaResult();

        smooks.setFilterSettings(filterSettings);
        smooks.filterSource(source, result);

        XMLUnit.setIgnoreWhitespace( true );
        InputStream stream = getClass().getResourceAsStream("children-only.xml");
        Object bean = result.getBean("soapBody");
        XMLAssert.assertXMLEqual(new InputStreamReader(stream), new StringReader(bean.toString().trim()));
    }

    @Test
    public void test_all_SAX() throws IOException, SAXException {
    	test_all(FilterSettings.DEFAULT_SAX);
    }

    @Test
    public void test_all_DOM() throws IOException, SAXException {
    	test_all(FilterSettings.DEFAULT_DOM);
    }

    private void test_all(FilterSettings filterSettings) throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-config-02.xml"));
        StreamSource source = new StreamSource(getClass().getResourceAsStream("input-message-01.xml"));
        JavaResult result = new JavaResult();

        smooks.setFilterSettings(filterSettings);
        smooks.filterSource(source, result);

        XMLUnit.setIgnoreWhitespace( true );
        XMLAssert.assertXMLEqual(new InputStreamReader(getClass().getResourceAsStream("all.xml")), new StringReader(result.getBean("soapBody").toString().trim()));
    }

    @Test    
    public void test_multi_fragments_SAX() throws IOException, SAXException {
    	test_multi_fragments(FilterSettings.DEFAULT_SAX);
    }

    @Test
    public void test_multi_fragments_DOM() throws IOException, SAXException {
    	test_multi_fragments(FilterSettings.DEFAULT_DOM);
    }

    private void test_multi_fragments(FilterSettings filterSettings) throws IOException, SAXException {
        Smooks smooks = new Smooks();

        smooks.addVisitor(new FragmentSerializer().setBindTo("orderItem"), "order-items/order-item");
        MockRouter router = new MockRouter().setBoundTo("orderItem");
        smooks.addVisitor(router, "order-items/order-item");

        smooks.setFilterSettings(filterSettings);
        smooks.filterSource(new StreamSource(getClass().getResourceAsStream("input-message-02.xml")));
        assertEquals(2, router.routedObjects.size());

//        System.out.println(router.routedObjects.get(0));

        XMLUnit.setIgnoreWhitespace( true );
        XMLAssert.assertXMLEqual(new InputStreamReader(getClass().getResourceAsStream("frag1.xml")), new StringReader((String) router.routedObjects.get(0)));
        XMLAssert.assertXMLEqual(new InputStreamReader(getClass().getResourceAsStream("frag2.xml")), new StringReader((String) router.routedObjects.get(1)));
    }
    
    private class MockRouter implements SAXVisitAfter, DOMVisitAfter {

        private String boundTo;
        private List<Object> routedObjects = new ArrayList<Object>();
    	
		public void visitAfter(SAXElement element, ExecutionContext executionContext) throws SmooksException, IOException {
			routedObjects.add(executionContext.getBeanContext().getBean(boundTo));
		}

		public void visitAfter(Element element,	ExecutionContext executionContext) throws SmooksException {
			routedObjects.add(executionContext.getBeanContext().getBean(boundTo));
		}
		
		public MockRouter setBoundTo(String boundTo) {
			this.boundTo = boundTo;
			return this;
		}
    }
}
