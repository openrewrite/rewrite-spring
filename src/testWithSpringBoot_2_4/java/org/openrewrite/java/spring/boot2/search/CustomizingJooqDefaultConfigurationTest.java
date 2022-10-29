/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CustomizingJooqDefaultConfigurationTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CustomizingJooqDefaultConfiguration())
          .parser(JavaParser.fromJavaVersion()
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-jdbc", "spring-orm", "jooq", "bind-api"));
    }

    @Test
    void jooqSettings() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.conf.Settings;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class PersistenceConfiguration {
              
                  @Bean
                  Settings settings() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.conf.Settings;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;

              @Configuration
              class PersistenceConfiguration {
              
                  /*~~>*/@Bean
                  Settings settings() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqConnectionProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.ConnectionProvider;
              import org.jooq.impl.DataSourceConnectionProvider;
              import javax.sql.DataSource;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class PersistenceConfiguration {
              
                  @Bean
                  ConnectionProvider connectionProvider(DataSource dataSource) {
                      return new DataSourceConnectionProvider(dataSource);
                  }
              }
              """,
            """
              import org.jooq.ConnectionProvider;
              import org.jooq.impl.DataSourceConnectionProvider;
              import javax.sql.DataSource;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;

              @Configuration
              class PersistenceConfiguration {

                  /*~~>*/@Bean
                  ConnectionProvider connectionProvider(DataSource dataSource) {
                      return new DataSourceConnectionProvider(dataSource);
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqExecutionProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.ExecutorProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  ExecutorProvider executorProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.ExecutorProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  ExecutorProvider executorProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqTransactionProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.TransactionProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  TransactionProvider transactionProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.TransactionProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  TransactionProvider transactionProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqRecordMapperProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.RecordMapperProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  RecordMapperProvider recordMapperProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.RecordMapperProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  RecordMapperProvider recordMapperProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqRecordUnmapperProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.RecordUnmapperProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  RecordUnmapperProvider recordUnmapperProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.RecordUnmapperProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  RecordUnmapperProvider recordUnmapperProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqRecordListenerProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.RecordListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  RecordListenerProvider recordListenerProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.RecordListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  RecordListenerProvider recordListenerProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqExecuteListenerProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.ExecuteListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  ExecuteListenerProvider executeListenerProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.ExecuteListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  ExecuteListenerProvider executeListenerProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqVisitListenerProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.VisitListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  VisitListenerProvider visitListenerProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.VisitListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  VisitListenerProvider visitListenerProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }

    @Test
    void jooqTransactionListenerProvider() {
        //language=java
        rewriteRun(
          java(
            """
              import org.jooq.TransactionListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  @Bean
                  TransactionListenerProvider transactionListenerProvider() {
                      return null;
                  }
              }
              """,
            """
              import org.jooq.TransactionListenerProvider;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.context.annotation.Bean;
              
              @Configuration
              class BeanConfiguration {
              
                  /*~~>*/@Bean
                  TransactionListenerProvider transactionListenerProvider() {
                      return null;
                  }
              }
              """
          )
        );
    }
}
