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
package org.smooks.cartridges.routing.jms;

import com.mockrunner.mock.ejb.EJBMockObjectFactory;
import com.mockrunner.mock.jms.JMSMockObjectFactory;
import com.mockrunner.mock.jms.MockQueue;
import com.mockrunner.mock.jms.MockQueueConnectionFactory;
import org.mockejb.jndi.MockContextFactory;
import org.smooks.cartridges.routing.SmooksRoutingException;
import org.smooks.cartridges.routing.util.RouterTestHelper;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.cdr.ResourceConfig;
import org.smooks.container.MockApplicationContext;
import org.smooks.container.MockExecutionContext;
import org.smooks.injector.Scope;
import org.smooks.lifecycle.LifecycleManager;
import org.smooks.lifecycle.phase.PostConstructLifecyclePhase;
import org.smooks.registry.Registry;
import org.smooks.registry.lookup.LifecycleManagerLookup;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

import static org.testng.AssertJUnit.*;

/**
 * Unit test for the JMSRouter class
 *
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>
 *
 */
public class JMSRouterTest {
    private static Registry registry;
    private static LifecycleManager lifecycleManager;
    private String selector = "x";
    private String queueName = "queue/testQueue";

    private static MockQueue queue;
    private static MockQueueConnectionFactory connectionFactory;

    @BeforeClass
    public static void beforeClass() {
        registry = new MockApplicationContext().getRegistry();
        lifecycleManager = registry.lookup(new LifecycleManagerLookup());
    }

    @Test(groups = "integration", expectedExceptions = SmooksConfigurationException.class)
    public void configureWithMissingDestinationType() {
        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        JMSRouter jmsRouter = new JMSRouter();
        lifecycleManager.applyPhase(jmsRouter, new PostConstructLifecyclePhase(new Scope(registry, config, jmsRouter)));
    }

    @Test(groups = "integration")
    public void visitAfter_below_hwmark() throws ParserConfigurationException, JMSException, SAXException, IOException {
        queue.clear();
        final String beanId = "beanId";
        final TestBean bean = RouterTestHelper.createBean();

        final MockExecutionContext executionContext = RouterTestHelper.createExecutionContext(beanId, bean);

        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        config.setParameter("destinationName", queueName);
        config.setParameter("beanId", beanId);
        final JMSRouter router = new JMSRouter();
        lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

        router.visitAfter(null, executionContext);

        final Message message = queue.getMessage();
        assertTrue("Message in queue should have been of type TextMessage",
                message instanceof TextMessage);

        final TextMessage textMessage = (TextMessage) message;
        assertEquals("Content of bean was not the same as the content of the TextMessage",
                bean.toString(), textMessage.getText());
    }

    @Test(groups = "unit")
    public void visitAfter_above_hwmark_notimeout() throws ParserConfigurationException, JMSException, SAXException, IOException {
        final String beanId = "beanId";
        final TestBean bean = RouterTestHelper.createBean();

        final MockExecutionContext executionContext = RouterTestHelper.createExecutionContext(beanId, bean);

        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        config.setParameter("destinationName", queueName);
        config.setParameter("beanId", beanId);
        config.setParameter("highWaterMark", "3");
        config.setParameter("highWaterMarkPollFrequency", "200");
        final JMSRouter router = new JMSRouter();
        lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

        int numMessages = 10;
        ConsumeThread consumeThread = new ConsumeThread(queue, numMessages);
        consumeThread.start();

        // wait for the thread to start...
        while (!consumeThread.running) {
            JMSRouterTest.sleep(100);
        }

        // Fire the messages...
        for (int i = 0; i < numMessages; i++) {
            router.visitAfter((Element) null, executionContext);
        }

        // wait for the thread to finish...
        while (consumeThread.running) {
            JMSRouterTest.sleep(100);
        }

        assertEquals(numMessages, consumeThread.numMessagesProcessed);
    }

    @Test(groups = "unit")
    public void visitAfter_above_hwmark_timeout() throws ParserConfigurationException, JMSException, SAXException, IOException {
        final String beanId = "beanId";
        final TestBean bean = RouterTestHelper.createBean();

        final MockExecutionContext executionContext = RouterTestHelper.createExecutionContext(beanId, bean);

        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        config.setParameter("destinationName", queueName);
        config.setParameter("beanId", beanId);
        config.setParameter("highWaterMark", "3");
        config.setParameter("highWaterMarkTimeout", "3000");
        config.setParameter("highWaterMarkPollFrequency", "200");
        final JMSRouter router = new JMSRouter();
        lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

        router.visitAfter(null, executionContext);
        router.visitAfter(null, executionContext);
        router.visitAfter(null,
                executionContext);

        try {
            router.visitAfter(null, executionContext);
            fail("Expected SmooksRoutingException");
        } catch (SmooksRoutingException e) {
            assertEquals("Failed to route JMS message to Queue destination 'testQueue'. Timed out (3000 ms) waiting for queue length to drop below High Water Mark (3).  Consider increasing 'highWaterMark' and/or 'highWaterMarkTimeout' param values.", e.getMessage());
        }
    }

    @Test(groups = "unit")
    public void setJndiContextFactory() {
        final String contextFactory = MockContextFactory.class.getName();
        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        setManadatoryProperties(config);
        config.setParameter("jndiContextFactory", contextFactory);
        final JMSRouter router = new JMSRouter();
        lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

        assertEquals("ContextFactory did not match the one set on the Router",
                contextFactory, router.getJndiContextFactory());
    }

    @Test(groups = "unit")
    public void setJndiProviderUrl() {
        final String providerUrl = "jnp://localhost:1099";
        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        setManadatoryProperties(config);
        config.setParameter("jndiProviderUrl", providerUrl);
        final JMSRouter router = new JMSRouter();
        lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

        assertEquals("ProviderURL did not match the one set on the Router",
                providerUrl, router.getJndiProviderUrl());
    }

    @Test(groups = "unit")
    public void setJndiNamingFactoryUrl() {
        final String namingFactoryUrlPkgs = "org.jboss.naming:org.jnp.interfaces";

        ResourceConfig config = new ResourceConfig(selector, JMSRouter.class.getName());
        setManadatoryProperties(config);
        config.setParameter("jndiNamingFactoryUrl", namingFactoryUrlPkgs);
        final JMSRouter router = new JMSRouter();
        lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

        assertEquals("NamingFactoryUrlPkg did not match the one set on the Router",
                namingFactoryUrlPkgs, router.getJndiNamingFactoryUrl());
    }

    @BeforeClass(groups = {"unit", "integration"})
    public static void setUpInitialContext() throws Exception {
        final EJBMockObjectFactory mockObjectFactory = new EJBMockObjectFactory();
        final Context context = mockObjectFactory.getContext();
        final JMSMockObjectFactory jmsObjectFactory = new JMSMockObjectFactory();

        connectionFactory = jmsObjectFactory.getMockQueueConnectionFactory();
        context.bind("ConnectionFactory", connectionFactory);
        queue = jmsObjectFactory.getDestinationManager().createQueue("testQueue");
        context.bind("queue/testQueue", queue);
        MockContextFactory.setAsInitial();
    }

    private void setManadatoryProperties(final ResourceConfig config) {
        config.setParameter("destinationName", queueName);
        config.setParameter("beanId", "bla");
    }

    static class ConsumeThread extends Thread {

        private volatile boolean running = false;
        private int numMessagesProcessed;
        private int numMessagesToProcesses;
        private MockQueue queue;

        ConsumeThread(MockQueue queue, int numMessagesToProcesses) {
            this.queue = queue;
            this.numMessagesToProcesses = numMessagesToProcesses;
        }

        public void run() {
            running = true;

            while (numMessagesProcessed < numMessagesToProcesses) {
                JMSRouterTest.sleep(100);
                if (!queue.isEmpty()) {
                    queue.getMessage();
                    numMessagesProcessed++;
                }
            }

            running = false;
        }
    }

    private static void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
        }
    }
}
