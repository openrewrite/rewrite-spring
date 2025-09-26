/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.boot2;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class DatabaseComponentAndBeanInitializationOrderingTest implements RewriteTest {

    private static final @Language("xml") String POM_WITH_SPRING_BOOT_25 = """
      <project>
          <groupId>com.example</groupId>
          <artifactId>foo</artifactId>
          <version>1.0.0</version>
          <dependencies>
            <dependency>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot</artifactId>
              <version>2.5.1</version>
            </dependency>
          </dependencies>
      </project>
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.java.spring.boot2.DatabaseComponentAndBeanInitializationOrdering")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-beans-5.+", "spring-context-5.+", "spring-boot-2.+", "spring-jdbc-4.1.+", "spring-orm-5.3.+",
              "jooq-3.14.15", "jakarta.persistence-api-2.2.3"));
    }

    @DocumentExample
    @Test
    void dslContextBeanShouldNotBeAnnotated() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import org.jooq.impl.DSL;
                import org.jooq.DSLContext;
                import org.jooq.SQLDialect;
                import javax.sql.DataSource;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    public static class A { private DataSource ds;}

                    @Bean
                    DSLContext dslContext(DataSource ds) {
                        return DSL.using(ds, SQLDialect.SQLITE);
                    }

                    @Bean
                    A a() {
                        return new A();
                    }
                }
                """,
              """
                import org.jooq.impl.DSL;
                import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
                import org.jooq.DSLContext;
                import org.jooq.SQLDialect;
                import javax.sql.DataSource;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    public static class A { private DataSource ds;}

                    @Bean
                    DSLContext dslContext(DataSource ds) {
                        return DSL.using(ds, SQLDialect.SQLITE);
                    }

                    @Bean
                    @DependsOnDatabaseInitialization
                    A a() {
                        return new A();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void jdbcOperationsBeanShouldNotBeAnnotated() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import javax.sql.DataSource;
                import org.springframework.jdbc.core.JdbcOperations;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.jdbc.core.JdbcTemplate;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    @Bean
                    JdbcOperations template(DataSource dataSource) {
                        return new JdbcTemplate(dataSource);
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void namedParameterJdbcOperationsBeanShouldNotBeAnnotated() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import javax.sql.DataSource;
                import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
                import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    @Bean
                    NamedParameterJdbcOperations template(DataSource dataSource) {
                        return new NamedParameterJdbcTemplate(dataSource);
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void abstractEntityManagerFactoryBeanShouldNotBeAnnotated() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import org.springframework.orm.jpa.AbstractEntityManagerFactoryBean;
                import org.springframework.orm.jpa.LocalEntityManagerFactoryBean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    @Bean
                    AbstractEntityManagerFactoryBean entityManagerFactoryBean() {
                        return new LocalEntityManagerFactoryBean();
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void entityManagerFactoryBeanShouldNotBeAnnotated() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import javax.persistence.Persistence;
                import javax.persistence.EntityManagerFactory;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    @Bean
                    EntityManagerFactory entityManagerFactory() {
                        return Persistence.createEntityManagerFactory("PERSISTENCE_UNIT_NAME");
                    }
                }
                """
            )
          )
        );
    }

    @Test
    void nonDataSourceBeanShouldNotBeAnnotated() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                public class MyService {
                }
                """
            ),
            //language=java
            java(
              """
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
          )
        );
    }

    @Test
    void beanDeclarationForBeanHavingMethodWithDataSourceParameter() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import javax.sql.DataSource;

                public class CustomDataSourceInitializer {
                    public void initDatabase(DataSource ds, String sql) {}
                }
                """
            ),
            //language=java
            java(
              """
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
              """
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
          )
        );
    }

    @Test
    void beanDeclarationForBeanDependingOnDataSourceInConfiguration() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import javax.sql.DataSource;

                public class CustomDataSourceInitializer {
                    private final DataSource ds;

                    CustomDataSourceInitializer(DataSource ds) {
                        this.ds = ds;
                    }

                    public void initDatabase(String sql) {}
                }
                """
            ),
            //language=java
            java(
              """
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
              """
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
          )
        );
    }

    @Test
    void beanDeclarationForThirdPartyDataSourceInitialization() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                package com.db.magic;

                import org.springframework.beans.factory.annotation.Autowired;
                import javax.sql.DataSource;

                public class MagicDataSourceInitializer {
                    @Autowired
                    private final DataSource ds;

                    public void initDatabase(String sql) {}
                }
                """
            ),
            //language=java
            java(
              """
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
              """
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
          )
        );
    }

    @Test
    void componentDoesDdl() {
        rewriteRun(
          mavenProject("project-maven",
            pomXml(POM_WITH_SPRING_BOOT_25),
            //language=java
            java(
              """
                import javax.sql.DataSource;
                import org.springframework.stereotype.Component;

                @Component
                public class MyDbInitializerComponent {

                    public void initSchema(DataSource ds) {
                    }
                }
                """,
              """
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
          )
        );
    }

    @Test
    void dontAnnotateNonBootModules() {
        rewriteRun(
          mavenProject("project-maven",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>foo</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                      <dependency>
                        <groupId>org.springframework</groupId>
                        <artifactId>spring-context</artifactId>
                        <version>5.3.1</version>
                      </dependency>
                    </dependencies>
                </project>
                """
            ),
            //language=java
            java(
              """
                import org.jooq.impl.DSL;
                import org.jooq.DSLContext;
                import org.jooq.SQLDialect;
                import javax.sql.DataSource;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.context.annotation.Bean;

                @Configuration
                class PersistenceConfiguration {

                    public static class A { private DataSource ds;}

                    @Bean
                    DSLContext dslContext(DataSource ds) {
                        return DSL.using(ds, SQLDialect.SQLITE);
                    }

                    @Bean
                    A a() {
                        return new A();
                    }
                }
                """
            )
          )
        );
    }
}
