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
