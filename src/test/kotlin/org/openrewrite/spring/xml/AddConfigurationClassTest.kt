package org.openrewrite.spring.xml

import org.openrewrite.spring.assertRefactored
import org.junit.jupiter.api.Test
import org.openrewrite.java.tree.J
import java.nio.file.Path

class AddConfigurationClassTest {
    private val mainSourceSet = Path.of("sourceSet")

    private val config = J.CompilationUnit
            .buildEmptyClass(mainSourceSet, "my.org", "MyConfiguration")
            .withMetadata(listOf("spring.beans.fileType" to "ConfigurationClass").toMap())

    @Test
    fun propertyPlaceholder() {
        val fixed = config.refactor().visit(AddConfigurationClass(beanDefinitions("""
            <context:property-placeholder location="${'$'}{projectConfigPrefix:file:///}${'$'}{projectConfigDir}/environment.properties" order="1" ignore-unresolvable="true" />
            <context:property-placeholder location="${'$'}{projectConfigPrefix:file:///}${'$'}{projectConfigDir}/sso.properties" order="3" />
            <context:property-placeholder location="classpath:project.properties" order="2" ignore-unresolvable="true" ignore-resource-not-found="true" />
        """), mainSourceSet)).fix().fixed

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
        """), mainSourceSet)).fix().fixed

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
        """), mainSourceSet)).fix().fixed

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

    @Test
    fun wireBeanNotInSourceSet() {
        val fixed = config.refactor().visit(AddConfigurationClass(beanDefinitions("""
            <bean id="dataSource" class="org.apache.tomcat.jdbc.pool.DataSource" destroy-method="close"/>  

            <bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
                <property name="dataSource" ref="dataSource"/>
            </bean>
        """), mainSourceSet)).fix().fixed

        assertRefactored(fixed, """
            package my.org;
            
            import org.apache.tomcat.jdbc.pool.DataSource;
            import org.springframework.jdbc.datasource.DataSourceTransactionManager;
            
            @Configuration
            public class MyConfiguration {
                @Bean(destroyMethod="close")
                public DataSource dataSource() {
                    return new DataSource();
                }
                
                @Bean
                public DataSourceTransactionManager transactionManager(DataSource dataSource) {
                    DataSourceTransactionManager dstm = new DataSourceTransactionManager();
                    dstm.setDataSource(dataSource);
                    return dstm;
                }
            }
        """)
    }
}
