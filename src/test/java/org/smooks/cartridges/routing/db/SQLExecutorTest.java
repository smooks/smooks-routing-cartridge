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
package org.smooks.cartridges.routing.db;

import org.hsqldb.jdbcDriver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.smooks.Smooks;
import org.smooks.SmooksException;
import org.smooks.container.ExecutionContext;
import org.smooks.db.DirectDataSource;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.context.BeanIdStore;
import org.smooks.javabean.repository.BeanId;
import org.smooks.payload.StringSource;
import org.smooks.util.HsqlServer;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class SQLExecutorTest {

    private static HsqlServer hsqlServer;

    @BeforeClass
	public static void afterClass() throws Exception {
        hsqlServer = new HsqlServer(9992);
        hsqlServer.execScript(SQLExecutorTest.class.getResourceAsStream("test.script"));
    }

    @AfterClass
	public static void beforeClass() throws Exception {
        hsqlServer.stop();
    }

    @Test
    public void test_appContextTime() throws Exception {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-config.xml"));
        try {
            test_appContextTime(smooks);
        } finally {
            smooks.close();
        }
    }

    @Test
    public void test_appContextTimeExtendedConfig() throws Exception {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-extended-config.xml"));
    	test_appContextTime(smooks);
        try {
            test_appContextTime(smooks);
        } finally {
            smooks.close();
        }
    }

    @Test
    public void test_appContextTimeProgrammatic() throws Exception {
        Smooks smooks = new Smooks();

        try {
            // Now programmaticly configure...
            DirectDataSource datasource = new DirectDataSource()
                              .setDriver(jdbcDriver.class)
                              .setName("OrdersDS")
                              .setUrl("jdbc:hsqldb:hsql://localhost:9992/milyn-hsql-9992")
                              .setUsername("sa")
                              .setPassword("")
                              .setAutoCommit(true);
            SQLExecutor orderSelector = new SQLExecutor()
                              .setDatasource(datasource)
                              .setStatement("select * from ORDERS")
                              .setResultSetName("orders1")
                              .setExecuteBefore(true);

            smooks.addVisitor(datasource);
            smooks.addVisitor(orderSelector);

            smooks.addVisitor(new ResultsetRowSelector()
                              .setSelector(orderSelector)
                              .setBeanId("myOrder")
                              .setWhereClause("row.ORDERNUMBER == 2")
                              .setFailedSelectError("Order with ORDERNUMBER=2 not found in Database"));

            smooks.addVisitor(new SQLExecutor()
                              .setDatasource(datasource)
                              .setStatement("select * from ORDERS")
                              .setResultSetName("orders2")
                              .setResultSetScope(ResultSetScope.APPLICATION)
                              .setResultSetTTL(2000L)
                              .setExecuteBefore(true));

            test_appContextTime(smooks);
        } finally {
            smooks.close();
        }
    }

    @SuppressWarnings("unchecked")
	private void test_appContextTime(Smooks smooks) throws IOException, SAXException, InterruptedException {
        ExecutionContext execContext = smooks.createExecutionContext();
        BeanContext beanContext = execContext.getBeanContext();

        smooks.filterSource(execContext, new StringSource("<doc/>"), null);
        List orders11 = (List) beanContext.getBean("orders1");
        List orders12 = (List) beanContext.getBean("orders2");

        smooks.filterSource(execContext, new StringSource("<doc/>"), null);
        List orders21 = (List) beanContext.getBean("orders1");
        List orders22 = (List) beanContext.getBean("orders2");

        assertTrue(orders11 != orders21);
        assertTrue(orders12 == orders22); // order12 should come from the app context cache

        // timeout the cached resultset...
        Thread.sleep(2050);

        smooks.filterSource(execContext, new StringSource("<doc/>"), null);
        List orders31 = (List) beanContext.getBean("orders1");
        List orders32 = (List) beanContext.getBean("orders2");

        assertTrue(orders11 != orders31);
        assertTrue(orders12 != orders32); // order12 shouldn't come from the app context cache - timed out ala TTL

        smooks.filterSource(execContext, new StringSource("<doc/>"), null);
        List orders41 = (List) beanContext.getBean("orders1");
        List orders42 = (List) beanContext.getBean("orders2");

        assertTrue(orders31 != orders41);
        assertTrue(orders32 == orders42); // order42 should come from the app context cache
    }

    @Test
    @SuppressWarnings("unchecked")
	public void test_ResultsetRowSelector_01() throws IOException, SAXException, InterruptedException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-config.xml"));

        try {
            ExecutionContext execContext = smooks.createExecutionContext();
            BeanContext beanContext = execContext.getBeanContext();

            smooks.filterSource(execContext, new StringSource("<doc/>"), null);
            Map<String, Object> myOrder = (Map<String, Object>) beanContext.getBean("myOrder");

            assertEquals("{ORDERNUMBER=2, CUSTOMERNUMBER=2, PRODUCTCODE=456}", myOrder.toString());
        } finally {
            smooks.close();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
	public void test_ResultsetRowSelector_02() throws IOException, SAXException, InterruptedException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-config-failed-select-01.xml"));

        try {
            ExecutionContext execContext = smooks.createExecutionContext();
            BeanContext beanContext = execContext.getBeanContext();

            smooks.filterSource(execContext, new StringSource("<doc/>"), null);
            Map<String, Object> myOrder = (Map<String, Object>) beanContext.getBean("myOrder");

            assertEquals(null, myOrder);
        } finally {
            smooks.close();
        }
    }

    @Test
    public void test_ResultsetRowSelector_03() throws IOException, SAXException, InterruptedException {
        Smooks smooks = new Smooks(getClass().getResourceAsStream("smooks-config-failed-select-02.xml"));

        try {
            ExecutionContext execContext = smooks.createExecutionContext();
            BeanContext beanContext = execContext.getBeanContext();
            BeanIdStore beanIdStore =  execContext.getApplicationContext().getBeanIdStore();

            BeanId requiredOrderNumId = beanIdStore.register("requiredOrderNum");

            beanContext.addBean(requiredOrderNumId, 9999, null);
            try {
                smooks.filterSource(execContext, new StringSource("<doc/>"), null);
                fail("Expected DataSelectionException");
            } catch(SmooksException e) {
                assertEquals("Order with ORDERNUMBER=9999 not found in Database", e.getCause().getMessage());
            }
        } finally {
            smooks.close();
        }
    }
}
