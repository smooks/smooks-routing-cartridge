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

import java.util.Map;
import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.Session;

import org.smooks.SmooksException;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.container.ExecutionContext;

/**
 *
 * @author <a href="mailto:maurice.zeijen@smies.com">maurice.zeijen@smies.com</a>
 *
 */
public class MapMessageCreationStrategy  implements MessageCreationStrategy {

	public Message createJMSMessage(String beanId, ExecutionContext context, Session jmsSession) throws SmooksException {
		final Object bean = context.getBeanContext().getBean( beanId );
        if(bean == null) {
            throw new SmooksException("Bean beandId '" + beanId + "' not available in the bean repository of this execution context.  Check the order in which your resources are being applied (in Smooks configuration).");
        }
        if(bean instanceof Map == false) {
        	throw new SmooksException("The bean unde beanId '" + beanId + "' with type " + bean.getClass().getName() + "'  can't be send with an JMS MapMessage because it doesn't implement a Map interface.");
        }

        return createMapMessage( (Map<?, ?>) bean, jmsSession );
	}

	private MapMessage createMapMessage( final Map<?, ?> map, final Session jmsSession ) throws SmooksException
	{
		try
		{
			MapMessage mapMessage = jmsSession.createMapMessage();

			mapToMapMessage(map, mapMessage);

			return mapMessage;
		}
		catch (JMSException e)
		{
			final String errorMsg = "JMSException while trying to create TextMessae";
			throw new SmooksConfigurationException( errorMsg, e );
		}
	}

	private void mapToMapMessage(final Map<?, ?> map, MapMessage mapMessage) throws JMSException {

		for(Entry<?, ?> entry : map.entrySet()) {

			String key = entry.getKey().toString();
			Object value = entry.getValue();

			if(value instanceof String) {

				mapMessage.setString(key, (String)value);

			} else if(value instanceof Integer) {

				mapMessage.setInt(key, (Integer)value);

			} else if(value instanceof Long) {

				mapMessage.setLong(key, (Long)value);

			} else if(value instanceof Double) {

				mapMessage.setDouble(key, (Double)value);

			} else if(value instanceof Float) {

				mapMessage.setFloat(key, (Float)value);

			} else if(value instanceof Boolean) {

				mapMessage.setBoolean(key, (Boolean)value);

			} else if(value instanceof Short) {

				mapMessage.setShort(key, (Short)value);

			} else if(value instanceof Byte) {

				mapMessage.setByte(key, (Byte)value);

			} else if(value instanceof Character) {

				mapMessage.setChar(key, (Character)value);

			} else if(value instanceof byte[]) {

				mapMessage.setBytes(key, (byte[])value);

			} else {

				mapMessage.setString(key, value.toString());

			}
		}

	}

}
