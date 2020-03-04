package org.gradle.rewrite.spring.xml

import org.gradle.rewrite.spring.assertRefactored
import org.junit.jupiter.api.Test
import org.openrewrite.java.tree.J
import java.nio.file.Path

class AddConfigurationClassTest {
    private val config = J.CompilationUnit
            .buildEmptyClass(Path.of("sourceSet"), "my.org", "MyConfiguration")
            .withMetadata(listOf("spring.beans.fileType" to "ConfigurationClass").toMap())

    @Test
    fun propertyPlaceholder() {
        val fixed = config.refactor().visit(AddConfigurationClass(beanDefinitions("""
            <context:property-placeholder location="${'$'}{projectConfigPrefix:file:///}${'$'}{projectConfigDir}/environment.properties" order="1" ignore-unresolvable="true" />
            <context:property-placeholder location="${'$'}{projectConfigPrefix:file:///}${'$'}{projectConfigDir}/sso.properties" order="3" />
            <context:property-placeholder location="classpath:project.properties" order="2" ignore-unresolvable="true" ignore-resource-not-found="true" />
        """))).fix().fixed

        assertRefactored(fixed, """
            package my.org;
            
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
            import org.springframework.core.io.ClassPathResource;
            import org.springframework.core.io.FileSystemResource;
            import org.springframework.core.io.Resource;
            
            @Configuration
            public class MyConfiguration {
            
                @Bean
                public static PropertySourcesPlaceholderConfigurer properties(@Value("${'$'}{projectConfigPrefix:file:///}") String projectConfigPrefix, @Value("${'$'}{projectConfigDir}") String projectConfigDir) {
                    PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
                    Resource[] resources = new Resource[]{
                        new FileSystemResource(projectConfigPrefix + projectConfigDir + "/environment.properties"),
                        new ClassPathResource("project.properties"),
                        new FileSystemResource(projectConfigPrefix + projectConfigDir + "/sso.properties")
                    };
                    pspc.setLocations(resources);
                    pspc.setIgnoreResourceNotFound(true);
                    pspc.setIgnoreUnresolvablePlaceholders(true);
                    return pspc;
                }
            }
        """)
    }

    @Test
    fun componentScan() {
        val fixed = config.refactor().visit(AddConfigurationClass(beanDefinitions("""
            <context:component-scan base-package="nz.govt.project" />
        """))).fix().fixed

        assertRefactored(fixed, """
            package my.org;
            
            import org.springframework.context.annotation.ComponentScan;
            import org.springframework.context.annotation.Configuration;
            
            @Configuration
            @ComponentScan("nz.govt.project")
            public class MyConfiguration {
            }
        """)
    }

    @Test
    fun multipleComponentScan() {
        val fixed = config.refactor().visit(AddConfigurationClass(beanDefinitions("""
            <context:component-scan base-package="nz.govt.project" />
            <context:component-scan base-package="au.govt.project" />
        """))).fix().fixed

        assertRefactored(fixed, """
            package my.org;
            
            import org.springframework.context.annotation.ComponentScan;
            import org.springframework.context.annotation.Configuration;
            
            @Configuration
            @ComponentScan({
                "au.govt.project",
                "nz.govt.project"
            })
            public class MyConfiguration {
            }
        """)
    }
}
