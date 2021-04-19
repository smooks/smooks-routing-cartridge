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

import org.junit.Before;
import org.junit.Test;
import org.smooks.api.Registry;
import org.smooks.api.lifecycle.LifecycleManager;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.engine.injector.Scope;
import org.smooks.engine.lifecycle.PostConstructLifecyclePhase;
import org.smooks.engine.lookup.LifecycleManagerLookup;
import org.smooks.engine.resource.config.DefaultResourceConfig;
import org.smooks.tck.MockApplicationContext;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for {@link OutputStreamRouter}
 * 
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>
 *
 */
public class OutputStreamRouterTest {
	private String resourceName = "testResource";
	private String beanId = "testBeanId";
	private OutputStreamRouter router = new OutputStreamRouter();
	private ResourceConfig config;

	@Test
	public void configure() {
		Registry registry = new MockApplicationContext().getRegistry();
		LifecycleManager lifecycleManager = registry.lookup(new LifecycleManagerLookup());

		lifecycleManager.applyPhase(router, new PostConstructLifecyclePhase(new Scope(registry, config, router)));

		assertEquals(resourceName, router.getResourceName());
	}

	@Before
	public void setup() {
		config = createConfig(resourceName, beanId);
	}


	private ResourceConfig createConfig(final String resourceName, final String beanId) {
		ResourceConfig config = new DefaultResourceConfig("x", OutputStreamRouter.class.getName());
		config.setParameter("resourceName", resourceName);
		config.setParameter("beanId", beanId);
		return config;
	}
}