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

import org.openrewrite.internal.lang.NonNull;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

import java.util.Optional;

public class RewriteBeanDefinition implements BeanDefinition {
    public static final String TYPE_PROPERTY_KEY = "__rewrite_type";

    private final BeanDefinition delegate;

    public RewriteBeanDefinition(BeanDefinition delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("unchecked")
    public final <T> Optional<T> getProperty(String property) {
        PropertyValue propertyValue = getPropertyValues().getPropertyValue(property);
        return propertyValue == null ? Optional.empty() : Optional.ofNullable((T) propertyValue.getValue());
    }

    public final Optional<String> getStringProperty(String property) {
        return this.getProperty(property);
    }

    public final Optional<Boolean> getBooleanProperty(String property) {
        return this.getProperty(property).map(value -> {
            if (value instanceof String)
                return Boolean.parseBoolean((String) value);
            return (Boolean) value;
        });
    }

    public final Optional<Integer> getIntegerProperty(String property) {
        return this.getProperty(property).map(value -> {
            if (value instanceof String)
                return Integer.parseInt((String) value);
            return (Integer) value;
        });
    }

    public final boolean isType(@Nullable Type type) {
        return isPropertyEqualTo(TYPE_PROPERTY_KEY, type);
    }

    public final boolean isPropertyEqualTo(String property, @org.openrewrite.internal.lang.Nullable Object value) {
        Object propertyValue = getProperty(property).orElse(null);
        return (value == null && propertyValue == null) || (value != null && value.equals(propertyValue));
    }

    public enum Type {
        ComponentScan("context"),
        PropertyPlaceholder("context");

        private final String namespace;

        Type(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }
    }

    @Override
    public void setParentName(String parentName) {
        delegate.setParentName(parentName);
    }

    @Override
    @Nullable
    public String getParentName() {
        return delegate.getParentName();
    }

    @Override
    public void setBeanClassName(String beanClassName) {
        delegate.setBeanClassName(beanClassName);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    @NonNull
    public String getBeanClassName() {
        return delegate.getBeanClassName();
    }

    @Override
    public void setScope(String scope) {
        delegate.setScope(scope);
    }

    @Override
    @Nullable
    public String getScope() {
        return delegate.getScope();
    }

    @Override
    public void setLazyInit(boolean lazyInit) {
        delegate.setLazyInit(lazyInit);
    }

    @Override
    public boolean isLazyInit() {
        return delegate.isLazyInit();
    }

    @Override
    public void setDependsOn(String... dependsOn) {
        delegate.setDependsOn(dependsOn);
    }

    @Override
    @Nullable
    public String[] getDependsOn() {
        return delegate.getDependsOn();
    }

    @Override
    public void setAutowireCandidate(boolean autowireCandidate) {
        delegate.setAutowireCandidate(autowireCandidate);
    }

    @Override
    public boolean isAutowireCandidate() {
        return delegate.isAutowireCandidate();
    }

    @Override
    public void setPrimary(boolean primary) {
        delegate.setPrimary(primary);
    }

    @Override
    public boolean isPrimary() {
        return delegate.isPrimary();
    }

    @Override
    public void setFactoryBeanName(String factoryBeanName) {
        delegate.setFactoryBeanName(factoryBeanName);
    }

    @Override
    @Nullable
    public String getFactoryBeanName() {
        return delegate.getFactoryBeanName();
    }

    @Override
    public void setFactoryMethodName(String factoryMethodName) {
        delegate.setFactoryMethodName(factoryMethodName);
    }

    @Override
    @Nullable
    public String getFactoryMethodName() {
        return delegate.getFactoryMethodName();
    }

    @Override
    public ConstructorArgumentValues getConstructorArgumentValues() {
        return delegate.getConstructorArgumentValues();
    }

    @Override
    public boolean hasConstructorArgumentValues() {
        return delegate.hasConstructorArgumentValues();
    }

    @Override
    public MutablePropertyValues getPropertyValues() {
        return delegate.getPropertyValues();
    }

    @Override
    public boolean hasPropertyValues() {
        return delegate.hasPropertyValues();
    }

    @Override
    public void setInitMethodName(String initMethodName) {
        delegate.setInitMethodName(initMethodName);
    }

    @Override
    @Nullable
    public String getInitMethodName() {
        return delegate.getInitMethodName();
    }

    @Override
    public void setDestroyMethodName(String destroyMethodName) {
        delegate.setDestroyMethodName(destroyMethodName);
    }

    @Override
    @Nullable
    public String getDestroyMethodName() {
        return delegate.getDestroyMethodName();
    }

    @Override
    public void setRole(int role) {
        delegate.setRole(role);
    }

    @Override
    public int getRole() {
        return delegate.getRole();
    }

    @Override
    public void setDescription(String description) {
        delegate.setDescription(description);
    }

    @Override
    @Nullable
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public ResolvableType getResolvableType() {
        return delegate.getResolvableType();
    }

    @Override
    public boolean isSingleton() {
        return delegate.isSingleton();
    }

    @Override
    public boolean isPrototype() {
        return delegate.isPrototype();
    }

    @Override
    public boolean isAbstract() {
        return delegate.isAbstract();
    }

    @Override
    @Nullable
    public String getResourceDescription() {
        return delegate.getResourceDescription();
    }

    @Override
    @Nullable
    public BeanDefinition getOriginatingBeanDefinition() {
        return delegate.getOriginatingBeanDefinition();
    }

    @Override
    public void setAttribute(String name, Object value) {
        delegate.setAttribute(name, value);
    }

    @Override
    @Nullable
    public Object getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    @Nullable
    public Object removeAttribute(String name) {
        return delegate.removeAttribute(name);
    }

    @Override
    public boolean hasAttribute(String name) {
        return delegate.hasAttribute(name);
    }

    @Override
    public String[] attributeNames() {
        return delegate.attributeNames();
    }

    @Override
    @Nullable
    public Object getSource() {
        return delegate.getSource();
    }
}