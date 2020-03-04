package org.gradle.rewrite.spring.xml.parse;

import org.openrewrite.internal.lang.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;

import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public class RewriteBeanDefinitionRegistry extends SimpleBeanDefinitionRegistry {
    public final Map<String, RewriteBeanDefinition> getBeanDefinitions(@Nullable RewriteBeanDefinition.Type type) {
        return stream(getBeanDefinitionNames())
                .filter(name -> getBeanDefinition(name).isPropertyEqualTo(RewriteBeanDefinition.TYPE_PROPERTY_KEY, type))
                .collect(toMap(Function.identity(), this::getBeanDefinition));
    }

    @Override
    public RewriteBeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        return new RewriteBeanDefinition(super.getBeanDefinition(beanName));
    }
}
