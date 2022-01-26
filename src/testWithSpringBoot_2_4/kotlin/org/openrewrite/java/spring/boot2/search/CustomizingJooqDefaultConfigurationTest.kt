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
package org.openrewrite.java.spring.boot2.search

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class CustomizingJooqDefaultConfigurationTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-beans", "spring-context", "spring-boot", "spring-jdbc", "spring-orm", "jooq", "bind-api")
            .build()

    override val recipe: Recipe
        get() = CustomizingJooqDefaultConfiguration()

    @Test
    fun jooqSettings() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqConnectionProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqExecutionProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqTransactionProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqRecordMapperProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqRecordUnmapperProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqRecordListenerProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqExecuteListenerProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqVisitListenerProvider() = assertChanged(
        before = """
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
        after = """
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

    @Test
    fun jooqTransactionListenerProvider() = assertChanged(
        before = """
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
        after = """
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
}
