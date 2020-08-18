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
package org.smooks.cartridges.routing.basic;

import org.smooks.SmooksException;
import org.smooks.container.ExecutionContext;
import org.smooks.delivery.Fragment;
import org.smooks.delivery.VisitLifecycleCleanable;
import org.smooks.delivery.dom.DOMVisitAfter;
import org.smooks.delivery.dom.DOMVisitBefore;
import org.smooks.delivery.ordering.Producer;
import org.smooks.delivery.sax.*;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.lifecycle.BeanContextLifecycleEvent;
import org.smooks.javabean.lifecycle.BeanLifecycle;
import org.smooks.javabean.repository.BeanId;
import org.smooks.namespace.NamespaceDeclarationStack;
import org.smooks.util.CollectionsUtil;
import org.smooks.xml.NamespaceMappings;
import org.smooks.xml.XmlUtil;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Basic message fragment serializer.
 * 
 * @author <a href="mailto:tom.fennelly@jboss.com">tom.fennelly@jboss.com</a>
 */
public class FragmentSerializer implements SAXVisitBefore, SAXVisitAfter, DOMVisitBefore, DOMVisitAfter, Producer, VisitLifecycleCleanable {

    private String bindTo;
    private boolean omitXMLDeclaration;
	private boolean childContentOnly;
    private boolean retain;
    
    /**
     * Set the bind-to beanId for the serialized fragment.
	 * @param bindTo The bind-to beanId for the serialized fragment.
	 * @return this instance.
	 */
    @Inject
	public FragmentSerializer setBindTo(String bindTo) {
		this.bindTo = bindTo;
		return this;
	}
    
    /**
     * Omit the XML Declaration from the serialized fragments.
	 * @param omitXMLDeclaration True if the XML declaration is to be omitted, otherwise false.
	 * @return this instance.
	 */
    @Inject
	public FragmentSerializer setOmitXMLDeclaration(Optional<Boolean> omitXMLDeclaration) {
		this.omitXMLDeclaration = omitXMLDeclaration.orElse(false);
		return this;
	}

    /**
     * Set whether or not the child content only should be serialized.
     * <p/>
     * This variable is, by default, false.
     * 
	 * @param childContentOnly True if the child content only (exclude 
	 * the targeted element itself), otherwise false.
	 * @return this instance.
	 */
    @Inject
	public FragmentSerializer setChildContentOnly(Optional<Boolean> childContentOnly) {
		this.childContentOnly = childContentOnly.orElse(false);
		return this;
	}

    /**
     * Retain the fragment bean in the {@link BeanContext} after it's creating fragment
     * has been processed.
     *
	 * @param retain True if the fragment bean is to be retained in the {@link org.smooks.javabean.context.BeanContext},
     * otherwise false.
	 * @return this instance.
	 */
    @Inject
    public FragmentSerializer setRetain(Optional<Boolean> retain) {
        this.retain = retain.orElse(false);
        return this;
    }

    public Set<? extends Object> getProducts() {
		return CollectionsUtil.toSet(bindTo);
	}

	@SuppressWarnings("unchecked")
	public void visitBefore(SAXElement saxElement, ExecutionContext executionContext) throws SmooksException, IOException {
    	Map<String, SAXSerializer> fragmentSerializers = (Map<String, SAXSerializer>) executionContext.getAttribute(FragmentSerializer.class);
    	
    	if(fragmentSerializers == null) {
    		fragmentSerializers = new HashMap<String, SAXSerializer>();
        	executionContext.setAttribute(FragmentSerializer.class, fragmentSerializers);
    	}
    	
    	SAXSerializer serializer = new SAXSerializer();
    	fragmentSerializers.put(bindTo, serializer);
    	
        if(!omitXMLDeclaration) {
        	serializer.fragmentWriter.write("<?xml version=\"1.0\"?>\n");
        }
    	
    	// Now add a dynamic visitor...
        DynamicSAXElementVisitorList.addDynamicVisitor(serializer, executionContext);

        notifyStartBean(new Fragment(saxElement), executionContext);
    }

    @SuppressWarnings("unchecked")
	public void visitAfter(SAXElement saxElement, ExecutionContext executionContext) throws SmooksException, IOException {
    	Map<String, SAXSerializer> fragmentSerializers = (Map<String, SAXSerializer>) executionContext.getAttribute(FragmentSerializer.class);
    	SAXSerializer serializer = fragmentSerializers.get(bindTo);

    	try {
    		executionContext.getBeanContext().addBean(bindTo, serializer.fragmentWriter.toString().trim(), new Fragment(saxElement));
    	} finally {
            DynamicSAXElementVisitorList.removeDynamicVisitor(serializer, executionContext);
    	}
    }

    public void visitBefore(Element element, ExecutionContext executionContext) throws SmooksException {
        notifyStartBean(new Fragment(element), executionContext);
    }

    public void visitAfter(Element element, ExecutionContext executionContext) throws SmooksException {
		String serializedFragment;

        if(childContentOnly) {
        	serializedFragment = XmlUtil.serialize(element.getChildNodes(), false);
        } else {
        	serializedFragment = XmlUtil.serialize(element, true);
        }

        if(!omitXMLDeclaration) {
        	serializedFragment = "<?xml version=\"1.0\"?>\n" + serializedFragment;
        }

        executionContext.getBeanContext().addBean(bindTo, serializedFragment, new Fragment(element));
	}

    private void notifyStartBean(Fragment source, ExecutionContext executionContext) {
        BeanContext beanContext = executionContext.getBeanContext();

        beanContext.notifyObservers(new BeanContextLifecycleEvent(executionContext,
                source, BeanLifecycle.START_FRAGMENT, beanContext.getBeanId(bindTo), ""));
    }

    public void executeVisitLifecycleCleanup(Fragment fragment, ExecutionContext executionContext) {
        BeanContext beanContext = executionContext.getBeanContext();
        BeanId beanId = beanContext.getBeanId(bindTo);
        Object bean = beanContext.getBean(beanId);

        beanContext.notifyObservers(new BeanContextLifecycleEvent(executionContext, fragment, BeanLifecycle.END_FRAGMENT, beanId, bean));
        if(!retain) {
            executionContext.getBeanContext().removeBean(beanId, null);
        }
    }

    private class SAXSerializer implements SAXElementVisitor {
		
    	int depth = 0;
    	StringWriter fragmentWriter = new StringWriter();

		public void visitBefore(SAXElement element, ExecutionContext executionContext) throws SmooksException, IOException {
            if (depth == 0) {
                addRootNamespaces(element, executionContext);
            }

	        if(childContentOnly) {
	        	// Print child content only, so only print the start if the depth is greater
	        	// than 1...
	        	if(depth > 0) {
	        		SAXElementWriterUtil.writeStartElement(element, fragmentWriter, true);
	        	}
	        } else {
	        	// Printing all of the element, so just print the start element...
	        	SAXElementWriterUtil.writeStartElement(element, fragmentWriter, true);
	        }
	        depth++;
		}

		public void onChildElement(SAXElement element, SAXElement childElement, ExecutionContext executionContext) throws SmooksException, IOException {
	    	// The child element will look after itself.
	    }
	    
		public void onChildText(SAXElement element, SAXText text, ExecutionContext executionContext) throws SmooksException, IOException {
	    	SAXElementWriterUtil.writeText(text, fragmentWriter);
	    }

		public void visitAfter(SAXElement element, ExecutionContext executionContext) throws SmooksException, IOException {
	        depth--;
	        if(childContentOnly) {
	        	// Print child content only, so only print the empty element if the depth is greater
	        	// than 1...
	        	if(depth > 0) {
	        		SAXElementWriterUtil.writeEndElement(element, fragmentWriter);
	        	}
	        } else {
	        	// Printing all of the elements, so just print the end of the element...
	        	SAXElementWriterUtil.writeEndElement(element, fragmentWriter);
	        }
		}		

        private void addRootNamespaces(SAXElement element, ExecutionContext executionContext) {
            NamespaceDeclarationStack nsDeclStack = NamespaceMappings.getNamespaceDeclarationStack(executionContext);
            Map<String, String> rootNamespaces = nsDeclStack.getActiveNamespaces();

            if (!rootNamespaces.isEmpty()) {
                Set<Map.Entry<String,String>> namespaces = rootNamespaces.entrySet();
                for (Map.Entry<String,String> namespace : namespaces) {
                    addNamespace(namespace.getKey(), namespace.getValue(), element);
                }
            }
        }

		private void addNamespace(String prefix, String namespaceURI, SAXElement element) {
            if (prefix == null || namespaceURI == null) {
                // No namespace.  Ignore...
                return;
            } else  if(prefix.equals(XMLConstants.DEFAULT_NS_PREFIX) && namespaceURI.equals(XMLConstants.NULL_NS_URI)) {
                // No namespace.  Ignore...
                return;
			} else {
				String prefixNS = element.getAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix);
				if(prefixNS != null && prefixNS.length() != 0) {
					// Already declared (on the element)...
					return;
				}
			}
			
			Attributes attributes = element.getAttributes();
	        AttributesImpl attributesCopy = new AttributesImpl();
	        attributesCopy.setAttributes(attributes);
	        
	        if(prefix.length() > 0) {
	        	attributesCopy.addAttribute(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix, "xmlns:" + prefix, null, namespaceURI);
	        } else {
	        	attributesCopy.addAttribute(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix, "xmlns", null, namespaceURI);
	        }
	        element.setAttributes(attributesCopy);
		}
	}
}
