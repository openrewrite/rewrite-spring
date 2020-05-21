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
package org.openrewrite.spring.xml;

import org.openrewrite.Refactor;
import org.openrewrite.RefactorModule;
import org.openrewrite.java.tree.J;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinition;
import org.openrewrite.spring.xml.parse.RewriteBeanDefinitionRegistry;
import org.openrewrite.spring.xml.parse.RewriteNamespaceHandler;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

public class MigrateSpringXmlConfigurationJava implements RefactorModule<J.CompilationUnit, J> {
    private final Path mainSourceSet;
    private final String configurationPackage;
    private final RewriteBeanDefinitionRegistry beanDefinitionRegistry = new RewriteBeanDefinitionRegistry();

    public MigrateSpringXmlConfigurationJava(Path mainSourceSet, String configurationPackage, List<Path> xmlConfigurations) {
        this.mainSourceSet = mainSourceSet;
        this.configurationPackage = configurationPackage;
        loadBeanDefinitions(xmlConfigurations, beanDefinitionRegistry);
    }

    static void loadBeanDefinitions(List<Path> xmlConfigurations, BeanDefinitionRegistry beanDefinitionRegistry) {
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beanDefinitionRegistry);
        reader.setValidating(false);

        DefaultNamespaceHandlerResolver namespaceHandlerResolver = (DefaultNamespaceHandlerResolver) reader.getNamespaceHandlerResolver();
        try {
            Method getHandlerMappings = DefaultNamespaceHandlerResolver.class.getDeclaredMethod("getHandlerMappings");
            getHandlerMappings.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, Object> handlerMappings = (Map<String, Object>) getHandlerMappings
                    .invoke(namespaceHandlerResolver);

            // Override default handler mappings to preserve the attributes in the original XML so we
            // can build annotations that, when interpreted by Spring, will do something similar as the original
            // handler mappings.
            handlerMappings.put("http://www.springframework.org/schema/context", new RewriteNamespaceHandler(Map.of(
                    "property-placeholder", RewriteBeanDefinition.Type.PropertyPlaceholder,
                    "component-scan", RewriteBeanDefinition.Type.ComponentScan)));

            System.out.println(handlerMappings);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        for (Path xmlConfiguration : xmlConfigurations) {
            try (InputStream configInput = Files.newInputStream(xmlConfiguration, StandardOpenOption.READ)) {
                reader.loadBeanDefinitions(new InputSource(configInput));
            } catch (IOException | BeanDefinitionStoreException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Refactor<J.CompilationUnit, J> apply(Refactor<J.CompilationUnit, J> refactor) {
        return refactor.visit(new MakeComponentScannable(beanDefinitionRegistry))
                .visit(new AddConfigurationClass(beanDefinitionRegistry, mainSourceSet));
    }

    @Override
    public List<J.CompilationUnit> getDeclaredOutputs() {
        return singletonList(J.CompilationUnit
                .buildEmptyClass(mainSourceSet, configurationPackage, "MyConfiguration")
                .withMetadata(Map.of(SpringMetadata.FILE_TYPE, "ConfigurationClass")));
    }
}
