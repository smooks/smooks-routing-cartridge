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
package org.smooks.cartridges.routing.io;

import org.smooks.cdr.SmooksResourceConfiguration;
import org.smooks.container.MockApplicationContext;
import org.smooks.injector.Scope;
import org.smooks.lifecycle.LifecycleManager;
import org.smooks.lifecycle.phase.PostConstructLifecyclePhase;
import org.smooks.registry.Registry;
import org.smooks.registry.lookup.LifecycleManagerLookup;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

/**
 * Unit test for {@link OutputStreamRouter}
 * 
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>
 *
 */
@Test ( groups = "unit" )
public class OutputStreamRouterTest
{
	private String resourceName = "testResource";
	private String beanId = "testBeanId";
	private OutputStreamRouter router = new OutputStreamRouter();
	private SmooksResourceConfiguration config;

	@Test
	public void configure() {
		Registry registry = new MockApplicationContext().getRegistry();
		LifecycleManager lifecycleManager = registry.lookup(new LifecycleManagerLookup());
		
		lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));
		
		assertEquals(resourceName, router.getResourceName());
	}
	
	@BeforeTest
	public void setup()
	{
		config = createConfig( resourceName, beanId );
	}
	
	//	private
	
	private SmooksResourceConfiguration createConfig( 
			final String resourceName,
			final String beanId)
	{
    	SmooksResourceConfiguration config = new SmooksResourceConfiguration( "x", OutputStreamRouter.class.getName() );
		config.setParameter( "resourceName", resourceName );
		config.setParameter( "beanId", beanId );
		return config;
	}

}
