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
import org.smooks.SmooksException;
import org.smooks.assertion.AssertArgument;
import org.smooks.cartridges.routing.SmooksRoutingException;
import org.smooks.cartridges.routing.jms.message.creationstrategies.MessageCreationStrategy;
import org.smooks.cartridges.routing.jms.message.creationstrategies.StrategyFactory;
import org.smooks.cartridges.routing.jms.message.creationstrategies.TextMessageCreationStrategy;
import org.smooks.cdr.SmooksConfigurationException;
import org.smooks.container.ExecutionContext;
import org.smooks.delivery.annotation.VisitAfterIf;
import org.smooks.delivery.annotation.VisitBeforeIf;
import org.smooks.delivery.dom.DOMElementVisitor;
import org.smooks.delivery.ordering.Consumer;
import org.smooks.delivery.sax.SAXElement;
import org.smooks.delivery.sax.SAXVisitAfter;
import org.smooks.delivery.sax.SAXVisitBefore;
import org.smooks.util.FreeMarkerTemplate;
import org.smooks.util.FreeMarkerUtils;
import org.w3c.dom.Element;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * <p/>
 * Router is a Visitor for DOM or SAX elements. It sends the content
 * as a JMS Message object to the configured destination.
 * <p/>
 * The type of the JMS Message is determined by the "messageType" config param.
 * <p/>
 * Example configuration:
 * <pre>
 * &lt;resource-config selector="orderItems"&gt;
 *    &lt;resource&gt;org.smooks.routing.jms.JMSRouter&lt;/resource&gt;
 *    &lt;param name="beanId">beanId&lt;/param&gt;
 *    &lt;param name="destinationName"&gt;/queue/smooksRouterQueue&lt;/param&gt;
 * &lt;/resource-config&gt;
 *	....
 * Optional parameters:
 *    &lt;param name="executeBefore"&gt;true&lt;/param&gt;
 *    &lt;param name="jndiContextFactory"&gt;ConnectionFactory&lt;/param&gt;
 *    &lt;param name="jndiProviderUrl"&gt;jnp://localhost:1099&lt;/param&gt;
 *    &lt;param name="jndiNamingFactory"&gt;org.jboss.naming:java.naming.factory.url.pkgs=org.jnp.interfaces&lt;/param&gt;
 *    &lt;param name="connectionFactory"&gt;ConnectionFactory&lt;/param&gt;
 *    &lt;param name="deliveryMode"&gt;persistent&lt;/param&gt;
 *    &lt;param name="priority"&gt;10&lt;/param&gt;
 *    &lt;param name="timeToLive"&gt;100000&lt;/param&gt;
 *    &lt;param name="securityPrincipal"&gt;username&lt;/param&gt;
 *    &lt;param name="securityCredential"&gt;password&lt;/param&gt;
 *    &lt;param name="acknowledgeMode"&gt;AUTO_ACKNOWLEDGE&lt;/param&gt;
 *    &lt;param name="transacted"&gt;false&lt;/param&gt;
 *    &lt;param name="correlationIdPattern"&gt;orderitem-${order.orderId}-${order.orderItem.itemId}&lt;/param&gt;
 *    &lt;param name="messageType"&gt;ObjectMessage&lt;/param&gt;
 *    &lt;param name="highWaterMark"&gt;50&lt;/param&gt;
 *    &lt;param name="highWaterMarkTimeout"&gt;5000&lt;/param&gt;
 *    &lt;param name="highWaterMarkPollFrequency"&gt;500&lt;/param&gt;
 * </pre>
 * Description of configuration properties:
 * <ul>
 * <li><i>jndiContextFactory</i>: the JNDI ContextFactory to use.
 * <li><i>jndiProviderUrl</i>:  the JNDI Provider URL to use.
 * <li><i>jndiNamingFactory</i>: the JNDI NamingFactory to use.
 * <li><i>connectionFactory</i>: the ConnectionFactory to look up.
 * <li><i>deliveryMode</i>: the JMS DeliveryMode. 'persistent'(default) or 'non-persistent'.
 * <li><i>priority</i>: the JMS Priority to be used.
 * <li><i>timeToLive</i>: the JMS Time-To-Live to be used.
 * <li><i>securityPrincipal</i>: security principal use when creating the JMS connection.
 * <li><i>securityCredential</i>: the security credentials to use when creating the JMS connection.
 * <li><i>acknowledgeMode</i>: the acknowledge mode to use. One of 'AUTO_ACKNOWLEDGE'(default), 'CLIENT_ACKNOWLEDGE', 'DUPS_OK_ACKNOWLEDGE'.
 * <li><i>transacted</i>: determines if the session should be transacted. Defaults to 'false'.
 * <li><i>correlationIdPattern</i>: JMS Correlation pattern that will be used for the outgoing message. Supports templating.
 * <li><i>messageType</i>: type of JMS Message that should be sent. 'TextMessage'(default), 'ObjectMessage' or 'MapMessage'.
 * <li><i>highWaterMark</i>: max number of messages that can be sitting in the JMS Destination at any any time. Default is 200.
 * <li><i>highWaterMarkTimeout</i>: number of ms to wait for the system to process JMS Messages from the JMS destination
 * 		so that the number of JMS Messages drops below the highWaterMark. Default is 60000 ms.
 * <li><i>highWaterMarkPollFrequency</i>: number of ms to wait between checks on the High Water Mark, while
 *      waiting for it to drop. Default is 1000 ms.
 * </ul>
 *
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>
 * @since 1.0
 *
 */
@VisitBeforeIf(condition = "executeBefore")
@VisitAfterIf(condition = "!executeBefore")
public class JMSRouter implements DOMElementVisitor, SAXVisitBefore, SAXVisitAfter, Consumer {
    /*
     *	Log instance
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JMSRouter.class);

    /*
     *	JNDI Properties holder
     */
    private final JNDIProperties jndiProperties = new JNDIProperties();

    /*
     *	JMS Properties holder
     */
    private final JMSProperties jmsProperties = new JMSProperties();

    /*
     * 	BeanId is a key that is used to look up a bean
     * 	in the execution context
     */
    @Inject
    private String beanId;

    @Inject
    private Optional<String> correlationIdPattern;
    private FreeMarkerTemplate correlationIdTemplate;

    @Inject
    private Integer highWaterMark = 200;
    @Inject
    private Long highWaterMarkTimeout = 60000L;

    @Inject
    private Long highWaterMarkPollFrequency = 1000L;

    @Inject
    private Boolean executeBefore = false;
    /*
     * 	Strategy for JMS Message object creation
     */
    private MessageCreationStrategy msgCreationStrategy = new TextMessageCreationStrategy();

    /*
     * 	JMS Destination
     */
    private Destination destination;

    /*
     * 	JMS Connection
     */
    private Connection connection;

    /*
     * 	JMS Message producer
     */
    private MessageProducer msgProducer;
    /*
     * 	JMS Session
     */
    private Session session;

    @PostConstruct
    public void initialize() throws SmooksConfigurationException, JMSException {
        Context context = null;
        boolean initialized = false;

        if (beanId == null) {
            throw new SmooksConfigurationException("Mandatory 'beanId' property not defined.");
        }
        if (jmsProperties.getDestinationName() == null) {
            throw new SmooksConfigurationException("Mandatory 'destinationName' property not defined.");
        }

        try {
            correlationIdPattern.ifPresent(s -> correlationIdTemplate = new FreeMarkerTemplate(s));

            Properties jndiContextProperties = jndiProperties.toProperties();

            if (jndiContextProperties.isEmpty()) {
                context = new InitialContext();
            } else {
                context = new InitialContext(jndiContextProperties);
            }
            destination = (Destination) context.lookup(jmsProperties.getDestinationName());
            msgProducer = createMessageProducer(destination, context);
            setMessageProducerProperties();

            initialized = true;
        } catch (NamingException e) {
            final String errorMsg = "NamingException while trying to lookup [" + jmsProperties.getDestinationName() + "]";
            LOGGER.error(errorMsg, e);
            throw new SmooksConfigurationException(errorMsg, e);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException e) {
                    LOGGER.debug("NamingException while trying to close initial Context");
                }
            }

            if (!initialized) {
                releaseJMSResources();
            }
        }
    }

    @PreDestroy
    public void uninitialize() throws JMSException {
        releaseJMSResources();
    }

    public boolean consumes(Object object) {
        if (object.toString().startsWith(beanId)) {
            // We use startsWith (Vs equals) so as to catch bean populations e.g. "address.street".
            return true;
        }

        return false;
    }

    public void setBeanId(String beanId) {
        AssertArgument.isNotNullAndNotEmpty(beanId, "beanId");
        this.beanId = beanId;
    }

    public void setCorrelationIdPattern(String correlationIdPattern) {
        this.correlationIdPattern = Optional.ofNullable(correlationIdPattern);
    }

    public void setHighWaterMark(int highWaterMark) {
        this.highWaterMark = highWaterMark;
    }

    public void setHighWaterMarkTimeout(long highWaterMarkTimeout) {
        this.highWaterMarkTimeout = highWaterMarkTimeout;
    }

    public void setHighWaterMarkPollFrequency(long highWaterMarkPollFrequency) {
        this.highWaterMarkPollFrequency = highWaterMarkPollFrequency;
    }

    @Inject
    public void setJndiContextFactory(final Optional<String> contextFactory) {
        jndiProperties.setContextFactory(contextFactory.orElse(null));
    }

    @Inject
    public void setJndiProperties(final Optional<String> propertiesFile) {
        jndiProperties.setPropertiesFile(propertiesFile.orElse(null));
    }

    public void setJndiProperties(final Properties properties) {
        jndiProperties.setProperties(properties);
    }

    @Inject
    public void setJndiProviderUrl(final Optional<String> providerUrl) {
        jndiProperties.setProviderUrl(providerUrl.orElse(null));
    }


    @Inject
    public void setJndiNamingFactoryUrl(final Optional<String> pkgUrl) {
        jndiProperties.setNamingFactoryUrlPkgs(pkgUrl.orElse(null));
    }

    @Inject
    public void setDestinationName(final String destinationName) {
        AssertArgument.isNotNullAndNotEmpty(destinationName, "destinationName");
        jmsProperties.setDestinationName(destinationName);
    }

    @Inject
    public void setDeliveryMode(final Optional<DeliveryMode> deliveryMode) {
        jmsProperties.setDeliveryMode(deliveryMode.orElse(DeliveryMode.PERSISTENT).toString());
    }

    @Inject
    public void setTimeToLive(final Optional<Long> timeToLive) {
        jmsProperties.setTimeToLive(timeToLive.orElse(0L));
    }

    @Inject
    public void setSecurityPrincipal(final Optional<String> securityPrincipal) {
        jmsProperties.setSecurityPrincipal(securityPrincipal.orElse(null));
    }

    @Inject
    public void setSecurityCredential(final Optional<String> securityCredential) {
        jmsProperties.setSecurityCredential(securityCredential.orElse(null));
    }

    @Inject
    public void setTransacted(final Optional<Boolean> transacted) {
        jmsProperties.setTransacted(transacted.orElse(false));
    }

    @Inject
    public void setConnectionFactoryName(final Optional<String> connectionFactoryName) {
        jmsProperties.setConnectionFactoryName(connectionFactoryName.orElse("ConnectionFactory"));
    }

    @Inject
    public void setPriority(final Optional<Integer> priority) {
        jmsProperties.setPriority(priority.orElse(0));
    }

    @Inject
    public void setAcknowledgeMode(final Optional<AckMode> jmsAcknowledgeMode) {
        jmsProperties.setAcknowledgeMode(jmsAcknowledgeMode.orElse(AckMode.DUPS_OK_ACKNOWLEDGE).toString());
    }

    @Inject
    public void setMessageType(final Optional<StrategyFactory.StrategyFactoryEnum> messageType) {
        msgCreationStrategy = StrategyFactory.getInstance().createStrategy(messageType.orElse(StrategyFactory.StrategyFactoryEnum.TEXT_MESSAGE).toString());
        jmsProperties.setMessageType(messageType.orElse(StrategyFactory.StrategyFactoryEnum.TEXT_MESSAGE).toString());
    }

    public Boolean getExecuteBefore() {
        return executeBefore;
    }

    //	Vistor methods

    public void visitAfter(final Element element, final ExecutionContext execContext) throws SmooksException {
        visit(execContext);
    }

    public void visitBefore(final Element element, final ExecutionContext execContext) throws SmooksException {
        visit(execContext);
    }

    public void visitAfter(final SAXElement element, final ExecutionContext execContext) throws SmooksException, IOException {
        visit(execContext);
    }

    public void visitBefore(final SAXElement element, final ExecutionContext execContext) throws SmooksException, IOException {
        visit(execContext);
    }

    private void visit(final ExecutionContext execContext) throws SmooksException {
        Message message = msgCreationStrategy.createJMSMessage(beanId, execContext, session);

        if (correlationIdTemplate != null) {
            setCorrelationID(execContext, message);
        }

        sendMessage(message);
    }

    //	Lifecycle

    protected MessageProducer createMessageProducer(final Destination destination, final Context context) throws JMSException {
        try {
            final ConnectionFactory connFactory = (ConnectionFactory) context.lookup(jmsProperties.getConnectionFactoryName());

            connection = (jmsProperties.getSecurityPrincipal() == null && jmsProperties.getSecurityCredential() == null) ?
                    connFactory.createConnection() :
                    connFactory.createConnection(jmsProperties.getSecurityPrincipal(), jmsProperties.getSecurityCredential());

            session = connection.createSession(jmsProperties.isTransacted(),
                    AcknowledgeModeEnum.getAckMode(jmsProperties.getAcknowledgeMode().toUpperCase()).getAcknowledgeModeInt());

            msgProducer = session.createProducer(destination);
            connection.start();
            LOGGER.info("JMS Connection started");
        } catch (JMSException e) {
            final String errorMsg = "JMSException while trying to create MessageProducer for Queue [" + jmsProperties.getDestinationName() + "]";
            releaseJMSResources();
            throw new SmooksConfigurationException(errorMsg, e);
        } catch (NamingException e) {
            final String errorMsg = "NamingException while trying to lookup ConnectionFactory [" + jmsProperties.getConnectionFactoryName() + "]";
            releaseJMSResources();
            throw new SmooksConfigurationException(errorMsg, e);
        }

        return msgProducer;
    }

    /**
     * Sets the following MessageProducer properties:
     * <lu>
     * <li>TimeToLive
     * <li>Priority
     * <li>DeliveryMode
     * </lu>
     * <p>
     * Subclasses may override this behaviour.
     */
    protected void setMessageProducerProperties() throws SmooksConfigurationException {
        try {
            msgProducer.setTimeToLive(jmsProperties.getTimeToLive());
            msgProducer.setPriority(jmsProperties.getPriority());

            final int deliveryModeInt = "non-persistent".equals(jmsProperties.getDeliveryMode()) ? javax.jms.DeliveryMode.NON_PERSISTENT : javax.jms.DeliveryMode.PERSISTENT;
            msgProducer.setDeliveryMode(deliveryModeInt);
        } catch (JMSException e) {
            final String errorMsg = "JMSException while trying to set JMS Header Fields";
            throw new SmooksConfigurationException(errorMsg, e);
        }
    }

    protected void sendMessage(final Message message) throws SmooksRoutingException {
        try {
            waitWhileAboveHighWaterMark();
        } catch (JMSException e) {
            throw new SmooksRoutingException("Exception while attempting to check JMS Queue High Water Mark.", e);
        }

        try {
            msgProducer.send(message);
        } catch (JMSException e) {
            final String errorMsg = "JMSException while sending Message.";
            throw new SmooksRoutingException(errorMsg, e);
        }
    }

    private void waitWhileAboveHighWaterMark() throws JMSException, SmooksRoutingException {
        if (highWaterMark == -1) {
            return;
        }

        if (session instanceof QueueSession) {
            QueueSession queueSession = (QueueSession) session;
            QueueBrowser queueBrowser = queueSession.createBrowser((Queue) destination);

            try {
                int length = getQueueLength(queueBrowser);
                long start = System.currentTimeMillis();

                if (LOGGER.isDebugEnabled() && length >= highWaterMark) {
                    LOGGER.debug("Length of JMS destination Queue '" + jmsProperties.getDestinationName() + "' has reached " + length + ".  High Water Mark is " + highWaterMark + ".  Waiting for Queue length to drop.");
                }

                while (length >= highWaterMark && (System.currentTimeMillis() < start + highWaterMarkTimeout)) {
                    try {
                        Thread.sleep(highWaterMarkPollFrequency);
                    } catch (InterruptedException e) {
                        LOGGER.error("Interrupted", e);
                        return;
                    }
                    length = getQueueLength(queueBrowser);
                }

                // Check did the queue length drop below the HWM...
                if (length >= highWaterMark) {
                    throw new SmooksRoutingException("Failed to route JMS message to Queue destination '" + ((Queue) destination).getQueueName() + "'. Timed out (" + highWaterMarkTimeout + " ms) waiting for queue length to drop below High Water Mark (" + highWaterMark + ").  Consider increasing 'highWaterMark' and/or 'highWaterMarkTimeout' param values.");
                }
            } finally {
                queueBrowser.close();
            }
        }
    }

    private int getQueueLength(QueueBrowser queueBrowser) throws JMSException {
        int length = 0;
        Enumeration queueEnum = queueBrowser.getEnumeration();
        while (queueEnum.hasMoreElements()) {
            length++;
            queueEnum.nextElement();
        }
        return length;
    }

    protected void close(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                final String errorMsg = "JMSException while trying to close connection";
                LOGGER.debug(errorMsg, e);
            }
        }
    }

    protected void close(final Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                final String errorMsg = "JMSException while trying to close session";
                LOGGER.debug(errorMsg, e);
            }
        }
    }

    public Destination getDestination() {
        return destination;
    }

    public String getJndiContextFactory() {
        return jndiProperties.getContextFactory();
    }

    public String getJndiProviderUrl() {
        return jndiProperties.getProviderUrl();
    }

    public String getJndiNamingFactoryUrl() {
        return jndiProperties.getNamingFactoryUrlPkgs();
    }

    public String getDestinationName() {
        return jmsProperties.getDestinationName();
    }

    private void setCorrelationID(ExecutionContext execContext, Message message) {
        Map<String, Object> beanMap = FreeMarkerUtils.getMergedModel(execContext);
        String correlationId = correlationIdTemplate.apply(beanMap);

        try {
            message.setJMSCorrelationID(correlationId);
        } catch (JMSException e) {
            throw new SmooksException("Failed to set CorrelationID '" + correlationId + "' on message.", e);
        }
    }

    public String getDeliveryMode() {
        return jmsProperties.getDeliveryMode();
    }

    public long getTimeToLive() {
        return jmsProperties.getTimeToLive();
    }

    public String getSecurityPrincipal() {
        return jmsProperties.getSecurityPrincipal();
    }

    public String getSecurityCredential() {
        return jmsProperties.getSecurityCredential();
    }

    public boolean isTransacted() {
        return jmsProperties.isTransacted();
    }

    public String getConnectionFactoryName() {
        return jmsProperties.getConnectionFactoryName();
    }

    public int getPriority() {
        return jmsProperties.getPriority();
    }

    public String getAcknowledgeMode() {
        return jmsProperties.getAcknowledgeMode();
    }

    public void setMsgCreationStrategy(final MessageCreationStrategy msgCreationStrategy) {
        this.msgCreationStrategy = msgCreationStrategy;
    }

    private void releaseJMSResources() throws JMSException {
        if (connection != null) {
            try {
                try {
                    connection.stop();
                } finally {
                    try {
                        closeProducer();
                    } finally {
                        closeSession();
                    }
                }
            } catch (JMSException e) {
                LOGGER.debug("JMSException while trying to stop JMS Connection.", e);
            } finally {
                connection.close();
                connection = null;
            }
        }
    }

    private void closeProducer() {
        if (msgProducer != null) {
            try {
                msgProducer.close();
            } catch (JMSException e) {
                LOGGER.debug("JMSException while trying to close JMS Message Producer.", e);
            } finally {
                msgProducer = null;
            }
        }
    }

    private void closeSession() {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                LOGGER.debug("JMSException while trying to close JMS Session.", e);
            } finally {
                session = null;
            }
        }
    }
}
