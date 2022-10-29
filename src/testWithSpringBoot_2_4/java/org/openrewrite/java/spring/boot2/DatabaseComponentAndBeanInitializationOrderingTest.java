/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class DatabaseComponentAndBeanInitializationOrderingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DatabaseComponentAndBeanInitializationOrdering())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-jdbc", "spring-orm", "jooq", "persistence-api", "jaxb-api"));
    }

    @Test
    void jdbcOperationsBeanShouldNotBeAnnotated() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void namedParameterJdbcOperationsBeanShouldNotBeAnnotated() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void abstractEntityManagerFactoryBeanShouldNotBeAnnotated() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void entityManagerFactoryBeanShouldNotBeAnnotated() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void dslContextBeanShouldNotBeAnnotated() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void nonDataSourceBeanShouldNotBeAnnotated() {
        //language=java 
        rewriteRun(
          java(
            """
              public class MyService {
              }
              """
          ),
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
        );
    }

    @Test
    void beanDeclarationForBeanHavingMethodWithDataSourceParameter() {
        //language=java 
        rewriteRun(
          java(
            """
              import javax.sql.DataSource;
                            
              public class CustomDataSourceInitializer {
                  public void initDatabase(DataSource ds, String sql) {}
              }
              """
          ),
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
        );
    }

    @Test
    void beanDeclarationForBeanDependingOnDataSourceInConfiguration() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void beanDeclarationForThirdPartyDataSourceInitialization() {
        //language=java 
        rewriteRun(
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
        );
    }

    @Test
    void componentDoesDdl() {
        //language=java 
        rewriteRun(
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
        );
    }
}
