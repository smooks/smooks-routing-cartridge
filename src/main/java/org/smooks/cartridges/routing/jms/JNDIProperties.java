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

import org.smooks.api.SmooksConfigException;
import org.smooks.cartridges.routing.JNDIUtil;
import org.smooks.resource.URIResourceLocator;

import javax.naming.Context;
import java.io.IOException;
import java.util.Properties;


public class JNDIProperties {
    private String contextFactory;

    private String providerUrl;

    private String namingFactoryUrlPkgs;

    private Properties defaultProperties = JNDIUtil.getDefaultProperties();

    private String propertiesFile;

    private Properties properties;

    public String getContextFactory() {
        return contextFactory;
    }

    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public void setProviderUrl(String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public String getNamingFactoryUrlPkgs() {
        return namingFactoryUrlPkgs;
    }

    public void setNamingFactoryUrlPkgs(String namingFactoryUrl) {
        this.namingFactoryUrlPkgs = namingFactoryUrl;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public Properties toProperties() throws SmooksConfigException {
        if (properties == null) {
            properties = new Properties();
        }

        if (propertiesFile != null) {
            try {
                URIResourceLocator locator = new URIResourceLocator();
                properties.load(locator.getResource(propertiesFile));
            } catch (IOException e) {
                throw new SmooksConfigException("Failed to read JMS JNDI properties file '" + propertiesFile + "'.", e);
            }
        }

        if (contextFactory != null) {
            properties.setProperty(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
        }
        if (providerUrl != null) {
            properties.setProperty(Context.PROVIDER_URL, providerUrl);
        }
        if (namingFactoryUrlPkgs != null) {
            properties.setProperty(Context.URL_PKG_PREFIXES, namingFactoryUrlPkgs);
        }

        // We only use the default properties if none of the JNDI properties have been
        // configured.  Intentionally not merging configured properties with
        // default properties!!!
        if (!properties.isEmpty()) {
            return properties;
        } else {
            return defaultProperties;
        }
    }
}
