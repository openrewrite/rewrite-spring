package org.gradle.rewrite.spring.xml;

import org.openrewrite.RefactorModule;
import org.openrewrite.SourceVisitor;
import org.openrewrite.java.tree.J;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.beans.factory.xml.*;
import org.springframework.context.config.ContextNamespaceHandler;
import org.springframework.core.env.StandardEnvironment;
import org.w3c.dom.Element;
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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class MigrateSpringXmlConfigurationJava implements RefactorModule<J.CompilationUnit, J> {
    private final Path mainSourceSet;
    private final String configurationPackage;
    private final BeanDefinitionRegistry beanDefinitionRegistry = new SimpleBeanDefinitionRegistry();

    public MigrateSpringXmlConfigurationJava(Path mainSourceSet, String configurationPackage, List<Path> xmlConfigurations) {
        this.mainSourceSet = mainSourceSet;
        this.configurationPackage = configurationPackage;
        loadBeanDefinitions(xmlConfigurations, beanDefinitionRegistry);
    }

    static void loadBeanDefinitions(List<Path> xmlConfigurations, BeanDefinitionRegistry beanDefinitionRegistry) {
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beanDefinitionRegistry);
        reader.setEnvironment(new NoPropertyResolution());
        reader.setValidating(false);

        DefaultNamespaceHandlerResolver namespaceHandlerResolver = (DefaultNamespaceHandlerResolver) reader.getNamespaceHandlerResolver();
        try {
            Method getHandlerMappings = DefaultNamespaceHandlerResolver.class.getDeclaredMethod("getHandlerMappings");
            getHandlerMappings.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, Object> handlerMappings = (Map<String, Object>) getHandlerMappings
                    .invoke(namespaceHandlerResolver);
            handlerMappings.put("http://www.springframework.org/schema/context", new MigratingContextNamespaceHandler());
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

    /**
     * Unlike the default implementation, doesn't actually do component scanning, just preserves the options
     * on the element so we can build an annotation on it to apply to a generated configuration class
     */
    private static class MigratingContextNamespaceHandler extends ContextNamespaceHandler {
        public MigratingContextNamespaceHandler() {
            init();
        }

        @Override
        public void init() {
            super.init();
            registerBeanDefinitionParser("component-scan", new SetClassBeanDefinitionParser(
                    "org.springframework.context.annotation.ComponentScan"));
        }

        /**
         * See {@link org.springframework.context.annotation.ComponentScanBeanDefinitionParser} for the replaced implementation
         */
        private static class SetClassBeanDefinitionParser extends AbstractSimpleBeanDefinitionParser {
            private final String className;

            private SetClassBeanDefinitionParser(String className) {
                this.className = className;
            }

            @Override
            protected boolean shouldGenerateId() {
                return true;
            }

            @Override
            protected void postProcess(BeanDefinitionBuilder beanDefinition, Element element) {
                beanDefinition.getBeanDefinition().setBeanClassName(className);
                super.postProcess(beanDefinition, element);
            }
        }
    }

    private static class NoPropertyResolution extends StandardEnvironment {
        @Override
        public String resolvePlaceholders(String text) {
            return text;
        }

        @Override
        public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
            return text;
        }
    }

    @Override
    public List<SourceVisitor<J>> getVisitors() {
        return asList(
                new MakeComponentScannable(beanDefinitionRegistry),
                new AddConfigurationClass(beanDefinitionRegistry)
        );
    }

    @Override
    public List<J.CompilationUnit> getDeclaredOutputs() {
        return singletonList(J.CompilationUnit
                .buildEmptyClass(mainSourceSet, configurationPackage, "MyConfiguration")
                .withMetadata(Map.of("spring.beans.fileType", "ConfigurationClass")));
    }
}
