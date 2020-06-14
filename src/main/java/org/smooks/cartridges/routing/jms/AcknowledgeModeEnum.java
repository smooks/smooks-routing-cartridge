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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Session;

/**
 * Enum to type-safe JMS Client Acknowledgement mode string
 * mappings to JMS Session's integers.
 *
 */
public enum AcknowledgeModeEnum
{
	CLIENT_ACKNOWLEDGE(Session.CLIENT_ACKNOWLEDGE),
	AUTO_ACKNOWLEDGE(Session.AUTO_ACKNOWLEDGE),
	DUPS_OK_ACKNOWLEDGE(Session.DUPS_OK_ACKNOWLEDGE);

	private static final Logger LOGGER = LoggerFactory.getLogger( AcknowledgeModeEnum.class ); // NOPMD by danbev on 8/03/08 09:20

	private int jmsAckModeInt;

	AcknowledgeModeEnum(final int jmsAckModeInt)
	{
		this.jmsAckModeInt = jmsAckModeInt;
	}

	public int getAcknowledgeModeInt()
	{
		return jmsAckModeInt;
	}

	static public AcknowledgeModeEnum getAckMode(final String ackMode)
	{
		if(ackMode != null)
		{
			try
			{
				return  AcknowledgeModeEnum.valueOf(ackMode); // NOPMD by danbev on 8/03/08 09:20
			}
			catch (IllegalArgumentException e)
			{
				LOGGER.debug("' " + ackMode + "' is invalid : " + ". Will use default '" + AcknowledgeModeEnum.AUTO_ACKNOWLEDGE);
			}
		}
		return AcknowledgeModeEnum.AUTO_ACKNOWLEDGE;
	}
}
