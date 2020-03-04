package org.gradle.rewrite.spring.xml.parse;

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