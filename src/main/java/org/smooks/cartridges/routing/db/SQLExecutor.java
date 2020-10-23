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

import org.smooks.SmooksException;
import org.smooks.assertion.AssertArgument;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.container.ApplicationContext;
import org.smooks.container.ExecutionContext;
import org.smooks.db.AbstractDataSource;
import org.smooks.delivery.Fragment;
import org.smooks.delivery.annotation.VisitAfterIf;
import org.smooks.delivery.annotation.VisitBeforeIf;
import org.smooks.delivery.ordering.Consumer;
import org.smooks.delivery.ordering.Producer;
import org.smooks.delivery.sax.ng.AfterVisitor;
import org.smooks.delivery.sax.ng.BeforeVisitor;
import org.smooks.event.report.annotation.VisitAfterReport;
import org.smooks.event.report.annotation.VisitBeforeReport;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.repository.BeanId;
import org.smooks.util.CollectionsUtil;
import org.w3c.dom.Element;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * SQLExecutor Visitor.
 * <p/>
 * Supports extraction and persistence to a Database.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@SuppressWarnings("unchecked")
@VisitBeforeIf(condition = "executeBefore")
@VisitAfterIf(condition = "!executeBefore")
@VisitBeforeReport(summary = "Execute statement '${resource.parameters.statement}' on Datasource '${resource.parameters.datasource}'.", detailTemplate = "reporting/SQLExecutor.html")
@VisitAfterReport(summary = "Execute statement '${resource.parameters.statement}' on Datasource '${resource.parameters.datasource}'.", detailTemplate = "reporting/SQLExecutor.html")
public class SQLExecutor implements BeforeVisitor, AfterVisitor, Producer, Consumer {

    @Inject
    private String datasource;

    @Inject
    private String statement;
    private StatementExec statementExec;
    private String rsAppContextKey;

    @Inject
    private Optional<String> resultSetName;

    @Inject
    private ResultSetScope resultSetScope = ResultSetScope.EXECUTION;

    @Inject
    private Long resultSetTTL = 900000L;

    @Inject
    private Boolean executeBefore = false;

    @Inject
    private ApplicationContext appContext;

    private BeanId resultSetBeanId;

    public SQLExecutor setDatasource(AbstractDataSource datasource) {
        AssertArgument.isNotNull(datasource, "datasource");
        this.datasource = datasource.getName();
        return this;
    }

    public SQLExecutor setStatement(String statement) {
        AssertArgument.isNotNullAndNotEmpty(statement, "statement");
        this.statement = statement;
        return this;
    }

    public SQLExecutor setResultSetName(String resultSetName) {
        AssertArgument.isNotNullAndNotEmpty(resultSetName, "resultSetName");
        this.resultSetName = Optional.of(resultSetName);
        return this;
    }

    public String getResultSetName() {
        return resultSetName.orElse(null);
    }

    public SQLExecutor setResultSetScope(ResultSetScope resultSetScope) {
        AssertArgument.isNotNull(resultSetScope, "resultSetScope");
        this.resultSetScope = resultSetScope;
        return this;
    }

    public SQLExecutor setResultSetTTL(long resultSetTTL) {
        this.resultSetTTL = resultSetTTL;
        return this;
    }

    public SQLExecutor setExecuteBefore(boolean executeBefore) {
        this.executeBefore = executeBefore;
        return this;
    }

    public boolean getExecuteBefore() {
        return executeBefore;
    }

    @PostConstruct
    public void postConstruct() throws SmooksConfigurationException {
        statementExec = new StatementExec(statement);
        if (statementExec.getStatementType() == StatementType.QUERY && !resultSetName.isPresent()) {
            throw new SmooksConfigurationException("Sorry, query statements must be accompanied by a 'resultSetName' property, under whose value the query results are bound.");
        }

        if (resultSetName.isPresent()) {
            resultSetBeanId = appContext.getBeanIdStore().register(resultSetName.get());
        }
        rsAppContextKey = datasource + ":" + statement;
    }

    public Set<?> getProducts() {
        if (statementExec.getStatementType() == StatementType.QUERY) {
            return CollectionsUtil.toSet(resultSetName);
        }

        return CollectionsUtil.toSet();
    }

    public boolean consumes(Object object) {
        return statement.contains(object.toString());
    }

    @Override
    public void visitBefore(Element element, ExecutionContext executionContext) throws SmooksException {
        executeSQL(executionContext, new Fragment(element));
    }

    @Override
    public void visitAfter(Element element, ExecutionContext executionContext) throws SmooksException {
        executeSQL(executionContext, new Fragment(element));
    }

    private void executeSQL(ExecutionContext executionContext, Fragment source) throws SmooksException {
        Connection connection = AbstractDataSource.getConnection(datasource, executionContext);
        BeanContext beanContext = executionContext.getBeanContext();

        Map<String, Object> beanMap = beanContext.getBeanMap();

        try {
            if (!statementExec.isJoin()) {
                if (statementExec.getStatementType() == StatementType.QUERY) {
                    if (resultSetScope == ResultSetScope.EXECUTION) {
                        beanContext.addBean(resultSetBeanId, statementExec.executeUnjoinedQuery(connection), source);
                    } else {
                        List<Map<String, Object>> resultMap;
                        // Cached in the application context...
                        ApplicationContext appContext = executionContext.getApplicationContext();
                        ResultSetContextObject rsContextObj = ResultSetContextObject.getInstance(rsAppContextKey, appContext);

                        if (rsContextObj.hasExpired()) {
                            synchronized (rsContextObj) {
                                if (rsContextObj.hasExpired()) {
                                    rsContextObj.resultSet = statementExec.executeUnjoinedQuery(connection);
                                    rsContextObj.expiresAt = System.currentTimeMillis() + resultSetTTL;
                                }
                            }
                        }
                        resultMap = rsContextObj.resultSet;
                        beanContext.addBean(resultSetBeanId, resultMap, source);
                    }
                } else {
                    statementExec.executeUnjoinedUpdate(connection);
                }
            } else {
                if (statementExec.getStatementType() == StatementType.QUERY) {
                    List<Map<String, Object>> resultMap = new ArrayList<Map<String, Object>>();
                    statementExec.executeJoinedQuery(connection, beanMap, resultMap);
                    beanContext.addBean(resultSetBeanId, resultMap, source);
                } else {
                    if (resultSetBeanId == null) {
                        statementExec.executeJoinedUpdate(connection, beanMap);
                    } else {
                        Object resultSetObj = beanContext.getBean(resultSetBeanId);
                        if (resultSetObj != null) {
                            try {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) resultSetObj;
                                statementExec.executeJoinedStatement(connection, resultSet);
                            } catch (ClassCastException e) {
                                throw new SmooksException("Cannot execute joined statement '" + statementExec.getStatement() + "' on ResultSet '" + resultSetName + "'.  Must be of type 'List<Map<String, Object>>'.  Is of type '" + resultSetObj.getClass().getName() + "'.");
                            }
                        } else {
                            throw new SmooksException("Cannot execute joined statement '" + statementExec.getStatement() + "' on ResultSet '" + resultSetName + "'.  ResultSet not found in ExecutionContext.");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new SmooksException("Error executing SQL Statement '" + statement + "'.", e);
        }
    }

    private static class ResultSetContextObject {
        private List<Map<String, Object>> resultSet;
        private long expiresAt = 0L;

        private boolean hasExpired() {
            return expiresAt <= System.currentTimeMillis();
        }

        private static ResultSetContextObject getInstance(String rsAppContextKey, ApplicationContext appContext) {
            ResultSetContextObject rsContextObj = (ResultSetContextObject) appContext.getRegistry().lookup(rsAppContextKey);

            if (rsContextObj == null) {
                synchronized (appContext) {
                    rsContextObj = (ResultSetContextObject) appContext.getRegistry().lookup(rsAppContextKey);
                    if (rsContextObj == null) {
                        rsContextObj = new ResultSetContextObject();
                        appContext.getRegistry().registerObject(rsAppContextKey, rsContextObj);
                    }
                }
            }

            return rsContextObj;
        }
    }
}
