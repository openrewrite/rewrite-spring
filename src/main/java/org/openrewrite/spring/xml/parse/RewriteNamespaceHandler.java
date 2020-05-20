/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.spring.xml.parse;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.w3c.dom.Element;

import java.util.Map;

public class RewriteNamespaceHandler extends NamespaceHandlerSupport {
    private final Map<String, RewriteBeanDefinition.Type> types;

    public RewriteNamespaceHandler(Map<String, RewriteBeanDefinition.Type> types) {
        this.types = types;
        init();
    }

    @Override
    public void init() {
        for (Map.Entry<String, RewriteBeanDefinition.Type> parserByElementName : types.entrySet()) {
            registerBeanDefinitionParser(parserByElementName.getKey(), new PropertyPreservingBeanDefinitionParser(parserByElementName.getValue()));
        }
    }

    private static class PropertyPreservingBeanDefinitionParser extends AbstractSimpleBeanDefinitionParser {
        private final RewriteBeanDefinition.Type type;

        private PropertyPreservingBeanDefinitionParser(RewriteBeanDefinition.Type type) {
            this.type = type;
        }

        @Override
        protected boolean shouldGenerateId() {
            return true;
        }

        @Override
        protected void postProcess(BeanDefinitionBuilder beanDefinition, Element element) {
            beanDefinition.getBeanDefinition().setBeanClassName(type.getNamespace() + ":" + type.name());
            beanDefinition.getBeanDefinition().getPropertyValues().addPropertyValue(RewriteBeanDefinition.TYPE_PROPERTY_KEY, type);
            super.postProcess(beanDefinition, element);
        }
    }
}