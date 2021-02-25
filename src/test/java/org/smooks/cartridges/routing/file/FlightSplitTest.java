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
package org.smooks.cartridges.routing.file;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.smooks.Smooks;
import org.xml.sax.SAXException;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
public class FlightSplitTest {

    @Before
    protected void setUp() throws Exception {
        new File("target/flights/BA-1234.xml").delete();
        new File("target/flights/BA-5678.xml").delete();
    }

    @Test
    public void test() throws IOException, SAXException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("flight-split.xml"));

        try {
            smooks.filterSource(new StreamSource(getClass().getResourceAsStream("flight.xml")));
        } finally {
            smooks.close();
        }

        XMLUnit.setIgnoreWhitespace(true);

        FileReader fileReader = new FileReader(new File("target/flights/BA-1234.xml"));
        try {
            assertTrue(XMLUnit.compareXML(fileReader, new InputStreamReader(getClass().getResourceAsStream("BA-1234.xml"))).identical());
        } finally {
            fileReader.close();
        }

        fileReader = new FileReader(new File("target/flights/BA-5678.xml"));
        try {
            assertTrue(XMLUnit.compareXML(fileReader, new InputStreamReader(getClass().getResourceAsStream("BA-5678.xml"))).identical());
        } finally {
            fileReader.close();
        }
    }
}
