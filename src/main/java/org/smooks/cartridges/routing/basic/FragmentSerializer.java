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
import org.smooks.container.TypedKey;
import org.smooks.delivery.dom.serialize.DefaultDOMSerializerVisitor;
import org.smooks.delivery.fragment.Fragment;
import org.smooks.delivery.fragment.NodeFragment;
import org.smooks.delivery.ordering.Producer;
import org.smooks.delivery.sax.ng.AfterVisitor;
import org.smooks.delivery.sax.ng.BeforeVisitor;
import org.smooks.delivery.sax.ng.event.CharDataFragmentEvent;
import org.smooks.event.ExecutionEvent;
import org.smooks.event.ExecutionEventListener;
import org.smooks.event.types.EndFragmentEvent;
import org.smooks.event.types.StartFragmentEvent;
import org.smooks.javabean.context.BeanContext;
import org.smooks.javabean.lifecycle.BeanContextLifecycleEvent;
import org.smooks.javabean.lifecycle.BeanLifecycle;
import org.smooks.javabean.repository.BeanId;
import org.smooks.lifecycle.VisitLifecycleCleanable;
import org.smooks.namespace.NamespaceDeclarationStack;
import org.smooks.util.CollectionsUtil;
import org.smooks.xml.NamespaceManager;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;

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
public class FragmentSerializer implements BeforeVisitor, AfterVisitor, Producer, VisitLifecycleCleanable {

	private static final TypedKey<Map<String, FragmentSerializerVisitor>> FRAGMENT_SERIALIZER_TYPED_KEY = new TypedKey<>();
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

	@Override
	public void visitBefore(Element element, ExecutionContext executionContext) throws SmooksException {
    	Map<String, FragmentSerializerVisitor> fragmentSerializers = executionContext.get(FRAGMENT_SERIALIZER_TYPED_KEY);
    	
    	if(fragmentSerializers == null) {
    		fragmentSerializers = new HashMap<>();
        	executionContext.put(FRAGMENT_SERIALIZER_TYPED_KEY, fragmentSerializers);
    	}

		FragmentSerializerVisitor serializer = new FragmentSerializerVisitor(executionContext);
    	fragmentSerializers.put(bindTo, serializer);
    	
        if(!omitXMLDeclaration) {
        	serializer.fragmentWriter.write("<?xml version=\"1.0\"?>\n");
        }
    	
    	// Now add a dynamic visitor...
		executionContext.getContentDeliveryRuntime().addExecutionEventListener(serializer);

        notifyStartBean(new NodeFragment(element), executionContext);
    }

	@Override
	public void visitAfter(Element element, ExecutionContext executionContext) throws SmooksException {
    	Map<String, FragmentSerializerVisitor> fragmentSerializers = executionContext.get(FRAGMENT_SERIALIZER_TYPED_KEY);
		FragmentSerializerVisitor serializer = fragmentSerializers.get(bindTo);

    	try {
    		executionContext.getBeanContext().addBean(bindTo, serializer.fragmentWriter.toString().trim(), new NodeFragment(element));
    	} finally {
			executionContext.getContentDeliveryRuntime().removeExecutionEventListener(serializer);
    	}
    }

    private void notifyStartBean(NodeFragment source, ExecutionContext executionContext) {
        BeanContext beanContext = executionContext.getBeanContext();

        beanContext.notifyObservers(new BeanContextLifecycleEvent(executionContext,
                source, BeanLifecycle.START_FRAGMENT, beanContext.getBeanId(bindTo), ""));
    }

    @Override
    public void executeVisitLifecycleCleanup(Fragment fragment, ExecutionContext executionContext) {
        BeanContext beanContext = executionContext.getBeanContext();
        BeanId beanId = beanContext.getBeanId(bindTo);
        Object bean = beanContext.getBean(beanId);

        beanContext.notifyObservers(new BeanContextLifecycleEvent(executionContext, fragment, BeanLifecycle.END_FRAGMENT, beanId, bean));
        if(!retain) {
            executionContext.getBeanContext().removeBean(beanId, null);
        }
    }

	private class FragmentSerializerVisitor implements ExecutionEventListener {

		private final ExecutionContext executionContext;
		private final StringWriter fragmentWriter = new StringWriter();
		private final DefaultDOMSerializerVisitor serializerVisitor;
		private int depth = 0;
    	
		public FragmentSerializerVisitor(ExecutionContext executionContext) {
			this.executionContext = executionContext;
			serializerVisitor = new DefaultDOMSerializerVisitor();
			serializerVisitor.postConstruct();
		}
		
		public void visitBefore(Element element, ExecutionContext executionContext) throws SmooksException {
			Element copyElement = (Element) element.cloneNode(false);
			if (depth == 0) {
                addRootNamespaces(copyElement, executionContext);
            }

	        if(childContentOnly) {
	        	// Print child content only, so only print the start if the depth is greater
	        	// than 1...
	        	if(depth > 0) {
					try {
						serializerVisitor.writeStartElement(copyElement, fragmentWriter, null);
					} catch (IOException e) {
						throw new SmooksException(e.getMessage(), e);
					}
	        	}
	        } else {
				try {
					// Printing all of the element, so just print the start element...
					serializerVisitor.writeStartElement(copyElement, fragmentWriter, null);
				} catch (IOException e) {
					throw new SmooksException(e.getMessage(), e);
				}
	        }
	        depth++;
		}
		
		public void visitChildText(CharacterData characterData, ExecutionContext executionContext) throws SmooksException {
			try {
				serializerVisitor.writeCharacterData(characterData, fragmentWriter, executionContext);
			} catch (IOException e) {
				throw new SmooksException(e.getMessage(), e);
			}
		}

		public void visitAfter(Element element, ExecutionContext executionContext) throws SmooksException {
	        depth--;
	        if(childContentOnly) {
	        	// Print child content only, so only print the empty element if the depth is greater
	        	// than 1...
	        	if(depth > 0) {
					try {
						serializerVisitor.writeEndElement(element, fragmentWriter, null);
					} catch (IOException e) {
						throw new SmooksException(e.getMessage(), e);
					}
				}
	        } else {
	        	// Printing all of the elements, so just print the end of the element...
				try {
					serializerVisitor.writeEndElement(element, fragmentWriter, null);
				} catch (IOException e) {
					throw new SmooksException(e.getMessage(), e);
				}
	        }
		}		

        private void addRootNamespaces(Element element, ExecutionContext executionContext) {
            NamespaceDeclarationStack nsDeclStack = executionContext.get(NamespaceManager.NAMESPACE_DECLARATION_STACK_TYPED_KEY);
            Map<String, String> rootNamespaces = nsDeclStack.getActiveNamespaces();

            if (!rootNamespaces.isEmpty()) {
                Set<Map.Entry<String,String>> namespaces = rootNamespaces.entrySet();
                for (Map.Entry<String,String> namespace : namespaces) {
                    addNamespace(namespace.getKey(), namespace.getValue(), element);
                }
            }
        }

		private void addNamespace(String prefix, String namespaceURI, Element element) {
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

			if(prefix.length() > 0) {
				element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:" + prefix, namespaceURI);
			} else {
				element.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", namespaceURI);
			}
		}

		@Override
		public void onEvent(ExecutionEvent executionEvent) {
			if (executionEvent instanceof StartFragmentEvent) {
				StartFragmentEvent startFragmentEvent = (StartFragmentEvent) executionEvent;
				visitBefore((Element) startFragmentEvent.getFragment().unwrap(), executionContext);
			} else if (executionEvent instanceof CharDataFragmentEvent) {
				CharDataFragmentEvent charDataFragmentEvent = (CharDataFragmentEvent) executionEvent;
				visitChildText((CharacterData) charDataFragmentEvent.getFragment().unwrap(), executionContext);
			} else if (executionEvent instanceof EndFragmentEvent) {
				EndFragmentEvent endFragmentEvent = (EndFragmentEvent) executionEvent;
				visitAfter((Element) endFragmentEvent.getFragment().unwrap(), executionContext);
			}
		}
	}
}
