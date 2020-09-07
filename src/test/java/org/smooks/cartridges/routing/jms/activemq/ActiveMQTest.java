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
package org.smooks.cartridges.routing.jms.activemq;

import org.smooks.FilterSettings;
import org.smooks.Smooks;
import org.smooks.cartridges.javabean.Bean;
import org.smooks.cartridges.routing.jms.JMSRouter;
import org.smooks.cartridges.routing.jms.TestJMSMessageListener;
import org.smooks.cartridges.templating.BindTo;
import org.smooks.cartridges.templating.TemplatingConfiguration;
import org.smooks.cartridges.templating.freemarker.FreeMarkerTemplateProcessor;
import org.smooks.payload.StringSource;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import javax.jms.JMSException;
import java.io.IOException;
import java.util.HashMap;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
@Test ( groups = "unit" )
public class ActiveMQTest {

    private static ActiveMQProvider mqProvider;
    private static TestJMSMessageListener listener;

    @BeforeClass
    public static void startActiveMQ() throws Exception {
        mqProvider = new ActiveMQProvider();
        mqProvider.addQueue("objectAQueue");
        mqProvider.start();
        listener = new TestJMSMessageListener();
        mqProvider.addQueueListener("objectAQueue", listener);
    }

    @AfterClass
    public static void stopActiveMQ() throws Exception {
        mqProvider.stop();
    }

    @Test
    public void test_xml_config_dom() throws IOException, SAXException, JMSException, InterruptedException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("config-01.xml"));
        test(smooks, FilterSettings.DEFAULT_DOM);
    }

    @Test
    public void test_xml_config_sax() throws IOException, SAXException, JMSException, InterruptedException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("config-01.xml"));
        test(smooks, FilterSettings.DEFAULT_SAX);
    }

    @Test
    public void test_xml_programmatic_dom() throws JMSException, InterruptedException {
        Smooks smooks = new Smooks();
        configure(smooks);
        test(smooks, FilterSettings.DEFAULT_DOM);
    }

    @Test
    public void test_xml_programmatic_sax() throws JMSException, InterruptedException {
        Smooks smooks = new Smooks();
        configure(smooks);
        test(smooks, FilterSettings.DEFAULT_SAX);
    }

    private void configure(Smooks smooks) {
        // Create a HashMap, name it "object" and then bind the <a> data into it, keyed as "a"...
        smooks.addVisitors(new Bean(HashMap.class, "object").bindTo("a", "a"));

        // On every <a> fragment, apply a simple template and bind the templating result to
        // beanId "orderItem_xml" ...
        smooks.addVisitor(new FreeMarkerTemplateProcessor(new TemplatingConfiguration("${object.a}").setUsage(BindTo.beanId("orderItem_xml"))), "a");

        JMSRouter jmsRouter = new JMSRouter();
        jmsRouter.setDestinationName("objectAQueue");
        jmsRouter.setBeanId("orderItem_xml");
        jmsRouter.setCorrelationIdPattern("${object.a}");
        jmsRouter.setJndiProperties(java.util.Optional.of("/org/smooks/cartridges/routing/jms/activemq/activemq.1.jndi.properties"));
        smooks.addVisitor(jmsRouter, "a");
    }

    private void test(Smooks smooks, FilterSettings filterSettings) throws JMSException, InterruptedException {
        try {
            smooks.setFilterSettings(filterSettings);
            listener.getMessages().clear();
            smooks.filterSource(new StringSource("<root><a>1</a><a>2</a><a>3</a></root>"));

            // wait to make sure all messages get delivered...
            Thread.sleep(500);

            assertEquals(3, listener.getMessages().size());
        } finally {
            smooks.close();
        }
    }
}
