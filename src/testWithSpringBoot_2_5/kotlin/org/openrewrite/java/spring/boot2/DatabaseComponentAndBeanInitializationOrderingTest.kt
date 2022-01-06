package org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class DatabaseComponentAndBeanInitializationOrderingTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-beans", "spring-context", "spring-boot", "mysql-connector-java", "spring-jdbc")
            .build()

    override val recipe: Recipe
        get() = DatabaseComponentAndBeanInitializationOrdering()

    @Test
    fun jdbcTemplateBeanShouldNotBeAnnotated() = assertUnchanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.jdbc.core.JdbcTemplate;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            class MyRepository {
            
                @Bean
                JdbcTemplate template (DataSource dataSource) {
                    return new JdbcTemplate(dataSource);
                }
            }
        """
    )

    @Test
    fun nonDataSourceBeanShouldNotBeAnnotated() = assertUnchanged(
        dependsOn = arrayOf(
            """
            public class MyService {
            }
        """),
        before = """
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            class MyThing {
            
                @Bean
                MyService myService() {
                    return new MyService();
                }
            }
        """
    )

    @Test
    fun beanDeclarationForBeanHavingMethodWithDataSourceParameter() = assertChanged(
        dependsOn = arrayOf(
            """
            import javax.sql.DataSource;
            
            public class CustomDataSourceInitializer {
                public void initDatabase(DataSource ds, String sql) {}
            }
        """),
        before = """
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            public class MyAppDataConfig {
                
                @Bean
                public CustomDataSourceInitializer customDataSourceInitializer() {
                    return new CustomDataSourceInitializer();
                }
            }
        """,
        after = """
            import org.springframework.context.annotation.Configuration;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            public class MyAppDataConfig {
            
                @Bean
                @DependsOnDatabaseInitialization
                public CustomDataSourceInitializer customDataSourceInitializer() {
                    return new CustomDataSourceInitializer();
                }
            }
        """
    )

    @Test
    fun beanDeclarationForBeanDependingOnDataSourceInConfiguration() = assertChanged(
        dependsOn = arrayOf(
            """
            import javax.sql.DataSource;
            
            public class CustomDataSourceInitializer {
                private final DataSource ds;
                
                CustomDataSourceInitializer(DataSource ds) {
                    this.ds = ds;
                }
                
                public void initDatabase(String sql) {}
            }
        """),
        before = """
            import javax.sql.DataSource;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            public class MyAppDataConfig {
                
                @Bean
                public CustomDataSourceInitializer customDataSourceInitializer(DataSource ds) {
                    return new CustomDataSourceInitializer(ds);
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            import org.springframework.context.annotation.Configuration;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Bean;
            
            @Configuration
            public class MyAppDataConfig {
            
                @Bean
                @DependsOnDatabaseInitialization
                public CustomDataSourceInitializer customDataSourceInitializer(DataSource ds) {
                    return new CustomDataSourceInitializer(ds);
                }
            }
        """
    )

    @Test
    fun beanDeclarationForThirdPartyDataSourceInitialization() = assertChanged(
        dependsOn = arrayOf(
            """
            package com.db.magic;
            
            import org.springframework.beans.factory.annotation.Autowired;
            import javax.sql.DataSource;
            
            public class MagicDataSourceInitializer {
                @Autowired
                private final DataSource ds;
                
                public void initDatabase(String sql) {}
            }
        """),
        before = """
            package com.my.dbinit;
            
            import com.db.magic.MagicDataSourceInitializer;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            
            @Configuration
            public class MyMagicDataConfig {
                
                @Bean
                public MagicDataSourceInitializer magicDataSourceInitializer() {
                    return new MagicDataSourceInitializer();
                }
            }
        """,
        after = """
            package com.my.dbinit;

            import com.db.magic.MagicDataSourceInitializer;
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;
            
            @Configuration
            public class MyMagicDataConfig {
            
                @Bean
                @DependsOnDatabaseInitialization
                public MagicDataSourceInitializer magicDataSourceInitializer() {
                    return new MagicDataSourceInitializer();
                }
            }
        """
    )

    @Test
    fun componentDoesDdl() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.stereotype.Component;
            
            @Component
            public class MyDbInitializerComponent {
                
                public void initSchema(DataSource ds) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
            import org.springframework.stereotype.Component;
            
            @Component
            @DependsOnDatabaseInitialization
            public class MyDbInitializerComponent {
                
                public void initSchema(DataSource ds) {
                }
            }
        """
    )


}