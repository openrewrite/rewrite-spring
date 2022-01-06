/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class InitilizationOrderingTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-beans", "spring-context", "spring-boot", "mysql-connector-java")
            .build()

    override val recipe: Recipe
        get() = InitializationOrdering()


    @Test
    fun beanDeclarationForBeanDependingOnDataSourceInConfiguration() = assertChanged(
        dependsOn = arrayOf(
        """
            import javax.sql.DataSource;
            public class AnotherComponent {
                public void method(DataSource ds) {}
            }
        """),
        before = """
            import javax.sql.DataSource;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            @Configuration
            public class BeanConfig {
                @Bean
                public AnotherComponent anotherComponent(DataSource ds) {
                    return new AnotherComponent();
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            public class BeanConfig {
                @Bean
                @DependsOnDatabaseInitialization
                public AnotherComponent anotherComponent(DataSource ds) {
                    return new AnotherComponent();
                }
            }
        """
    )

    @Test
    fun beanDeclarationForBeanDependingOnSubclassOfDataSourceInConfiguration() = assertChanged(
        dependsOn = arrayOf(
            """
            import com.mysql.cj.jdbc.MysqlDataSource;
            public class AnotherComponent {
                public void method(MysqlDataSource ds) {}
            }
        """),
        before = """
            import com.mysql.cj.jdbc.MysqlDataSource;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            @Configuration
            public class BeanConfig {
                @Bean
                public AnotherComponent anotherComponent(MysqlDataSource ds) {
                    return new AnotherComponent();
                }
            }
        """,
        after = """
            import com.mysql.cj.jdbc.MysqlDataSource;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            public class BeanConfig {
                @Bean
                @DependsOnDatabaseInitialization
                public AnotherComponent anotherComponent(MysqlDataSource ds) {
                    return new AnotherComponent();
                }
            }
        """
    )

    @Test
    fun beanDeclarationForBeanDependingOnDataSourceInSpringBootConfiguration() = assertChanged(
        dependsOn = arrayOf(
            """
            import javax.sql.DataSource;
            public class AnotherComponent {
                public void method(DataSource ds) {}
            }
        """),
        before = """
            import javax.sql.DataSource;
            import org.springframework.boot.SpringBootConfiguration;
            import org.springframework.context.annotation.Bean;
            @SpringBootConfiguration
            public class BeanConfig {
                @Bean
                public AnotherComponent anotherComponent(DataSource ds) {
                    return new AnotherComponent();
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            import org.springframework.boot.SpringBootConfiguration;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootConfiguration
            public class BeanConfig {
                @Bean
                @DependsOnDatabaseInitialization
                public AnotherComponent anotherComponent(DataSource ds) {
                    return new AnotherComponent();
                }
            }
        """
    )

    @Test
    fun beanDeclarationForBeanDependingOnDataSourceAndOthersInSpringBootConfiguration() = assertChanged(
        dependsOn = arrayOf(
            """
            public class SomeComponent {
                public void method() {}
            }
            """,
            """
            import javax.sql.DataSource;
            public class AnotherComponent {
                public void method(DataSource ds) {}
            }
        """),
        before = """
            import javax.sql.DataSource;
            import org.springframework.boot.SpringBootConfiguration;
            import org.springframework.context.annotation.Bean;
            @SpringBootConfiguration
            public class BeanConfig {
                @Bean
                public AnotherComponent anotherComponent(DataSource ds) {
                    return new AnotherComponent();
                }
                @Bean
                public SomeComponent someComponent() {
                    return new SomeComponent();
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            import org.springframework.boot.SpringBootConfiguration;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Bean;
            
            @SpringBootConfiguration
            public class BeanConfig {
                @Bean
                @DependsOnDatabaseInitialization
                public AnotherComponent anotherComponent(DataSource ds) {
                    return new AnotherComponent();
                }
                @Bean
                public SomeComponent someComponent() {
                    return new SomeComponent();
                }
            }
        """
    )

    @Test
    fun annotateComponent() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.stereotype.Component;
            @Component
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.stereotype.Component;
            
            @Component
            @DependsOnDatabaseInitialization
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
        """
    )

    @Test
    fun annotateOnlyComponentDependingOnDatSource() = assertChanged(
        dependsOn = arrayOf("""
            import org.springframework.stereotype.Component;
            @Component
            public class AnotherComponent {
                public void method() {
                }
            }
        """),
        before = """
            import javax.sql.DataSource;
            import org.springframework.stereotype.Component;
            @Component
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.stereotype.Component;
            
            @Component
            @DependsOnDatabaseInitialization
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
        """
    )

    @Test
    fun annotateOnlyComponent() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.stereotype.Component;
            @Component
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
            
            class Foo {}
        """,
        after = """
            import javax.sql.DataSource;
            
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.stereotype.Component;
            
            @Component
            @DependsOnDatabaseInitialization
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
            
            class Foo {}
        """
    )

    @Test
    fun annotateOnlyComponentDependingOnDataSource() = assertChanged(
        dependsOn = arrayOf("""
            import org.springframework.stereotype.Component;
            @Component
            public class AnotherComponent {
                public void method() {
                }
            }
        """),
        before = """
            import javax.sql.DataSource;
            import org.springframework.stereotype.Component;
            @Component
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
            
            class Foo {}
        """,
        after = """
            import javax.sql.DataSource;
            
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.stereotype.Component;
            
            @Component
            @DependsOnDatabaseInitialization
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
            
            class Foo {}
        """
    )

    @Test
    fun annotateOnlyComponentDependingOnDataSource2() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.stereotype.Component;
            @Component
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
            
            @Component
            class AnotherComponent {
                public void method() {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.stereotype.Component;
            
            @Component
            @DependsOnDatabaseInitialization
            public class BeanConfig {
                public void method(DataSource ds) {
                }
            }
            
            @Component
            class AnotherComponent {
                public void method() {
                }
            }
        """
    )

    @Test
    fun dontAnnotateComponentWhenComponentHasNoDependencyToDataSource() = assertUnchanged(
        before = """
            import org.springframework.stereotype.Component;
            @Component
            public class BeanConfig {
                public void method() {
                }
            }
        """
    )

    @Test
    fun dontAnnotateBeanDeclarationWhenBeanHasNoDependencyToDataSource() = assertUnchanged(
        dependsOn = arrayOf(
            """
               public class MyBean {} 
            """
        ),
        before = """
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            @Configuration
            public class BeanConfig {
                @Bean
                public MyBean myBean() {
                    return new MyBean();
                }
            }
        """
    )
}