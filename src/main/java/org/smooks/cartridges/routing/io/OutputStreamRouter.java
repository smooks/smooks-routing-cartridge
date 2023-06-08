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

import org.smooks.api.ApplicationContext;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksConfigException;
import org.smooks.api.SmooksException;
import org.smooks.api.bean.context.BeanContext;
import org.smooks.api.bean.repository.BeanId;
import org.smooks.api.delivery.ordering.Consumer;
import org.smooks.api.resource.visitor.VisitAfterIf;
import org.smooks.api.resource.visitor.VisitBeforeIf;
import org.smooks.api.resource.visitor.sax.ng.AfterVisitor;
import org.smooks.api.resource.visitor.sax.ng.BeforeVisitor;
import org.smooks.cartridges.routing.file.FileOutputStreamResource;
import org.smooks.io.AbstractOutputStreamResource;
import org.smooks.io.ResourceOutputStream;
import org.w3c.dom.Element;

import jakarta.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * OutputStreamRouter is a fragment Visitor (DOM/SAX) that can be used to route
 * context beans ({@link BeanContext} beans) an OutputStream.
 * </p>
 * An OutputStreamRouter is used in combination with a concreate implementation of
 * {@link AbstractOutputStreamResource}, for example a {@link FileOutputStreamResource}.
 *
 *Example configuration:
 *<pre>
 *&lt;resource-config selector="orderItem"&gt;
 *    &lt;resource&gt;org.smooks.routing.io.OutputStreamRouter&lt;/resource&gt;
 *    &lt;param name="resourceName"&gt;refToResource&lt;/param&gt;
 *    &lt;param name="beanId"&gt;orderItem&lt;/param&gt;
 *&lt;/resource-config&gt;
 *</pre>
 *
 * Description of configuration properties:
 * <ul>
 * <li><code>beanId </code> is key used search the execution context for the content to be written the OutputStream
 * <li><code>resourceName </code> is a reference to a previously configured {@link AbstractOutputStreamResource}
 * <li><code>encoding </code> is the encoding used when writing a characters to file
 * </ul>
 *
 * @author <a href="mailto:daniel.bevenius@gmail.com">Daniel Bevenius</a>
 * @since 1.0
 */
@VisitAfterIf(	condition = "!parameters.containsKey('visitBefore') || parameters.visitBefore.value != 'true'")
@VisitBeforeIf(	condition = "!parameters.containsKey('visitAfter') || parameters.visitAfter.value != 'true'")
public class OutputStreamRouter implements BeforeVisitor, AfterVisitor, Consumer
{
	@Inject
	private String resourceName;

    /*
     *	Character encoding to be used when writing character output
     */
    @Inject
	private String encoding = "UTF-8";

	/*
	 * 	beanId is a key that is used to look up a bean in the execution context
	 */
    @Inject
	@Named("beanId")
    private String beanIdName;

    private BeanId beanId;

    @Inject
    private ApplicationContext applicationContext;

    @PostConstruct
    public void initialize() throws SmooksConfigException {
    	beanId = applicationContext.getBeanIdStore().getBeanId(beanIdName);
    }

    //	public

    public boolean consumes(Object object) {
        if(object.equals(resourceName)) {
            return true;
        } else if(object.toString().startsWith(beanIdName)) {
            // We use startsWith (Vs equals) so as to catch bean populations e.g. "address.street".
            return true;
        }

        return false;
    }
    

    @Override
	public void visitBefore(Element element, ExecutionContext executionContext) throws SmooksException {
		write(executionContext);
	}

	@Override
	public void visitAfter(Element element, ExecutionContext executionContext) throws SmooksException {
		write(executionContext);
	}

	public String getResourceName()
	{
		return resourceName;
	}

	//	private

	private void write( final ExecutionContext executionContext )
	{
		Object bean = executionContext.getBeanContext().getBean( beanId );
        if ( bean == null )
        {
        	throw new SmooksException( "A bean with id [" + beanId + "] was not found in the executionContext");
        }
        
        OutputStream out = new ResourceOutputStream(executionContext, resourceName);
		try
		{
			if ( bean instanceof String )
			{
        		out.write( ( (String)bean).getBytes(encoding ) );
			}
			else if ( bean instanceof byte[] )
			{
        		out.write( new String( (byte[]) bean, encoding ).getBytes() ) ;
			}
			else
			{
        		out = new ObjectOutputStream( out );
        		((ObjectOutputStream)out).writeObject( bean );
			}

			out.flush();

		}
		catch (IOException e)
		{
    		final String errorMsg = "IOException while trying to append to file";
    		throw new SmooksException( errorMsg, e );
		}
	}

}
