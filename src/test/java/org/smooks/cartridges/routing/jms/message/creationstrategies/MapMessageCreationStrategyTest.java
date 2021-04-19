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
package org.smooks.cartridges.routing.jms.message.creationstrategies;

import com.mockrunner.mock.jms.JMSMockObjectFactory;
import com.mockrunner.mock.jms.MockConnectionFactory;
import org.junit.BeforeClass;
import org.junit.Test;
import org.smooks.cartridges.routing.util.RouterTestHelper;
import org.smooks.tck.MockExecutionContext;
import org.xml.sax.SAXException;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.Session;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 *
 */
public class MapMessageCreationStrategyTest
{
	private final MapMessageCreationStrategy strategy = new MapMessageCreationStrategy();

	private static Session jmsSession;

	@Test
	public void createJMSMessage() throws ParserConfigurationException, JMSException, SAXException, IOException
	{
		final String beanId = "123";
		final Map<String, Object> bean = new HashMap<String, Object>();
		bean.put("string", "Test");
		bean.put("int", 10);
		bean.put("long", 1000l);
		bean.put("double", 1000.01d);
		bean.put("float", 2000.01f);
		bean.put("short", (short)8);
		bean.put("boolean", false);
		bean.put("byte", (byte)1);
		bean.put("char", 'c');
		bean.put("chars", "Test".getBytes());
		bean.put("object", new Object() {
				@Override
				public String toString() {
					return "someObject";
				}
			});

        MockExecutionContext executionContext = RouterTestHelper.createExecutionContext( beanId, bean );

        Message message = strategy.createJMSMessage( beanId, executionContext, jmsSession ) ;

        assertTrue ( message instanceof MapMessage );

        MapMessage mapMessage = (MapMessage) message;

        assertEquals(bean.get("string"), mapMessage.getString("string"));
        assertEquals(bean.get("int"), mapMessage.getInt("int"));
        assertEquals(bean.get("long"), mapMessage.getLong("long"));
        assertEquals(bean.get("double"), mapMessage.getDouble("double"));
        assertEquals(bean.get("float"), mapMessage.getFloat("float"));
        assertEquals(bean.get("short"), mapMessage.getShort("short"));
        assertEquals(bean.get("boolean"), mapMessage.getBoolean("boolean"));
        assertEquals(bean.get("byte"), mapMessage.getByte("byte"));
        assertEquals(bean.get("char"), mapMessage.getChar("char"));
        assertEquals(new String((byte[])bean.get("chars")), new String(mapMessage.getBytes("chars")));
        assertEquals(bean.get("object").toString(), mapMessage.getString("object"));

	}

	@BeforeClass
	public static void setup() throws JMSException
	{
		JMSMockObjectFactory jmsObjectFactory = new JMSMockObjectFactory();
		MockConnectionFactory connectionFactory = jmsObjectFactory.getMockConnectionFactory();
		jmsSession = connectionFactory.createQueueConnection().createQueueSession( false, Session.AUTO_ACKNOWLEDGE );
	}

}
