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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smooks.SmooksException;
import org.smooks.assertion.AssertArgument;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.container.ApplicationContext;
import org.smooks.container.ExecutionContext;
import org.smooks.delivery.annotation.VisitAfterIf;
import org.smooks.delivery.annotation.VisitBeforeIf;
import org.smooks.delivery.fragment.NodeFragment;
import org.smooks.delivery.ordering.Consumer;
import org.smooks.delivery.ordering.Producer;
import org.smooks.delivery.sax.ng.AfterVisitor;
import org.smooks.delivery.sax.ng.BeforeVisitor;
import org.smooks.expression.ExpressionEvaluator;
import org.smooks.expression.MVELExpressionEvaluator;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.context.BeanIdStore;
import org.smooks.javabean.repository.BeanId;
import org.smooks.util.CollectionsUtil;
import org.smooks.util.FreeMarkerTemplate;
import org.w3c.dom.Element;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
@VisitBeforeIf(condition = "executeBefore")
@VisitAfterIf(condition = "!executeBefore")
public class ResultSetRowSelector implements BeforeVisitor, AfterVisitor, Producer, Consumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResultSetRowSelector.class);

    @Inject
    private String resultSetName;

    @Inject
    @Named("where")
    private ExpressionEvaluator whereEvaluator;

    @Inject
    private Optional<FreeMarkerTemplate> failedSelectError;

    @Inject
    @Named("beanId")
    private String beanId;

    @Inject
    private Boolean executeBefore = true;

    private BeanId resultSetBeanId;

    private BeanId beanIdObj;

    @Inject
    private ApplicationContext appContext;

    public ResultSetRowSelector setResultSetName(String resultSetName) {
        AssertArgument.isNotNullAndNotEmpty(resultSetName, "resultSetName");
        this.resultSetName = resultSetName;
        return this;
    }

    public ResultSetRowSelector setSelector(SQLExecutor executor) {
        AssertArgument.isNotNull(executor, "executor");
        this.resultSetName = executor.getResultSetName();
        if(this.resultSetName == null) {
            throw new IllegalArgumentException("Invalid 'executor' argument.  Executor must specify a 'resultSetName' in order to be used by a ResultsetRowSelector.");
        }
        return this;
    }

    public ResultSetRowSelector setWhereClause(String whereClause) {
        AssertArgument.isNotNullAndNotEmpty(whereClause, "whereClause");
        this.whereEvaluator = new MVELExpressionEvaluator();
        this.whereEvaluator.setExpression(whereClause);
        return this;
    }

    public ResultSetRowSelector setWhereEvaluator(ExpressionEvaluator whereEvaluator) {
        AssertArgument.isNotNull(whereEvaluator, "whereEvaluator");
        this.whereEvaluator = whereEvaluator;
        return this;
    }

    public ResultSetRowSelector setFailedSelectError(String failedSelectError) {
        AssertArgument.isNotNullAndNotEmpty(failedSelectError, "failedSelectError");
        this.failedSelectError = Optional.of(new FreeMarkerTemplate(failedSelectError));
        return this;
    }

    public ResultSetRowSelector setBeanId(String beanId) {
        AssertArgument.isNotNullAndNotEmpty(beanId, "beanId");
        this.beanId = beanId;
        return this;
    }

    public ResultSetRowSelector setExecuteBefore(boolean executeBefore) {
        this.executeBefore = executeBefore;
        return this;
    }

    public boolean getExecuteBefore() {
        return executeBefore;
    }
    
    @PostConstruct
    public void postConstruct() throws SmooksConfigurationException {
    	BeanIdStore beanIdStore = appContext.getBeanIdStore();

    	beanIdObj = beanIdStore.register(beanId);
    	resultSetBeanId = beanIdStore.register(resultSetName);
    }

    public Set<? extends Object> getProducts() {
        return CollectionsUtil.toSet(beanId);
    }

    public boolean consumes(Object object) {
        if(object.equals(resultSetName)) {
            return true;
        } else if(whereEvaluator != null && whereEvaluator.getExpression().contains(object.toString())) {
            return true;
        } else {
            return failedSelectError != null && failedSelectError.isPresent() && failedSelectError.get().getTemplateText().contains(object.toString());
        }
    }

    @Override
    public void visitBefore(Element element, ExecutionContext executionContext) throws SmooksException {
        selectRow(executionContext, new NodeFragment(element));
    }

    @Override
    public void visitAfter(Element element, ExecutionContext executionContext) throws SmooksException {
        selectRow(executionContext, new NodeFragment(element));
    }
    
    private void selectRow(ExecutionContext executionContext, NodeFragment source) throws SmooksException {
    	BeanContext beanRepository = executionContext.getBeanContext();

    	Map<String, Object> beanMapClone = new HashMap<>(beanRepository.getBeanMap());

        // Lookup the new current value for the bean...
        try {
        	@SuppressWarnings("unchecked")
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) beanRepository.getBean(resultSetBeanId);

            if(resultSet == null) {
                throw new SmooksException("Resultset '" + resultSetName + "' not found in bean context.  Make sure an appropriate SQLExecutor resource config wraps this selector config.");
            }

            try {
            	Object selectedRow = null;

            	Iterator<Map<String, Object>> resultIter = resultSet.iterator();
                while (selectedRow == null && resultIter.hasNext()) {
                	Map<String, Object> row = resultIter.next();

                	beanMapClone.put("row", row);

                    if(whereEvaluator.eval(beanMapClone)) {
                    	selectedRow = row;
                    	beanRepository.addBean(beanIdObj, selectedRow, source);
                    }
                }

                if(selectedRow == null && failedSelectError.isPresent()) {
                    throw new DataSelectionException(failedSelectError.get().apply(beanRepository.getBeanMap()));
                }

                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Selected resultset where '" + whereEvaluator.getExpression() + "': [" + selectedRow + "].");
                }
            } catch(ClassCastException e) {
                throw new SmooksException("Bean '" + resultSetName + "' cannot be used as a Reference Data resultset.  The resultset List must contain entries of type Map<String, Object>.");
            }
        } catch(ClassCastException e) {
            throw new SmooksException("Bean '" + resultSetName + "' cannot be used as a Reference Data resultset.  A resultset must be of type List<Map<String, Object>>. '" + resultSetName + "' is of type '" + beanRepository.getBean(resultSetBeanId).getClass().getName() + "'.");
        }
    }
}
