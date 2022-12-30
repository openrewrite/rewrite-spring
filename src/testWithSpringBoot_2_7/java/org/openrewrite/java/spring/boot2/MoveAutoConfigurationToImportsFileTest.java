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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.java.Assertions.*;

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
public class MoveAutoConfigurationToImportsFileTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MoveAutoConfigurationToImportsFile());
    }

    @Test
    void moveEntriesFromSpringFactories() {
        rewriteRun(
                text(
                        """
                        org.springframework.context.ApplicationContextInitializer=\\
                        org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer,\\
                        org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener
                                         
                        #Sprinkle in some comments
                        org.springframework.context.ApplicationListener=\\
                        org.springframework.boot.autoconfigure.BackgroundPreinitializer
                        
                        #Sprinkle in some comments
                        org.springframework.boot.autoconfigure.AutoConfigurationImportListener=\\
                        org.springframework.boot.autoconfigure.condition.ConditionEvaluationReportAutoConfigurationImportListener
                        
                        org.springframework.boot.autoconfigure.AutoConfigurationImportFilter=\\
                        org.springframework.boot.autoconfigure.condition.OnBeanCondition,\\
                        org.springframework.boot.autoconfigure.condition.OnClassCondition,\\
                        org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition
                        
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        #Sprinkle in some comments
                        org.springframework.boot.diagnostics.FailureAnalyzer=\\
                        org.springframework.boot.autoconfigure.data.redis.RedisUrlSyntaxFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.diagnostics.analyzer.NoSuchBeanDefinitionFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.flyway.FlywayMigrationScriptMissingFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.jdbc.DataSourceBeanCreationFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.jdbc.HikariDriverConfigurationFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.jooq.NoDslContextBeanFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryBeanCreationFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.session.NonUniqueSessionRepositoryFailureAnalyzer
                        
                        org.springframework.boot.autoconfigure.template.TemplateAvailabilityProvider=\\
                        org.springframework.boot.autoconfigure.freemarker.FreeMarkerTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.mustache.MustacheTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.thymeleaf.ThymeleafTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.web.servlet.JspTemplateAvailabilityProvider
                        """,
                        """
                        org.springframework.context.ApplicationContextInitializer=\\
                        org.springframework.boot.autoconfigure.SharedMetadataReaderFactoryContextInitializer,\\
                        org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener
                                         
                        #Sprinkle in some comments
                        org.springframework.context.ApplicationListener=\\
                        org.springframework.boot.autoconfigure.BackgroundPreinitializer
                        
                        #Sprinkle in some comments
                        org.springframework.boot.autoconfigure.AutoConfigurationImportListener=\\
                        org.springframework.boot.autoconfigure.condition.ConditionEvaluationReportAutoConfigurationImportListener
                        
                        org.springframework.boot.autoconfigure.AutoConfigurationImportFilter=\\
                        org.springframework.boot.autoconfigure.condition.OnBeanCondition,\\
                        org.springframework.boot.autoconfigure.condition.OnClassCondition,\\
                        org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition
                        
                        #Sprinkle in some comments
                        org.springframework.boot.diagnostics.FailureAnalyzer=\\
                        org.springframework.boot.autoconfigure.data.redis.RedisUrlSyntaxFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.diagnostics.analyzer.NoSuchBeanDefinitionFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.flyway.FlywayMigrationScriptMissingFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.jdbc.DataSourceBeanCreationFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.jdbc.HikariDriverConfigurationFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.jooq.NoDslContextBeanFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryBeanCreationFailureAnalyzer,\\
                        org.springframework.boot.autoconfigure.session.NonUniqueSessionRepositoryFailureAnalyzer
                        
                        org.springframework.boot.autoconfigure.template.TemplateAvailabilityProvider=\\
                        org.springframework.boot.autoconfigure.freemarker.FreeMarkerTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.mustache.MustacheTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.thymeleaf.ThymeleafTemplateAvailabilityProvider,\\
                        org.springframework.boot.autoconfigure.web.servlet.JspTemplateAvailabilityProvider
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring.factories")
                ),
                text(
                        null,
                        """
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                )
        );
    }

    @Test
    void onlyAutoConfigInSpringFactoriesShouldDeleteFile() {

        rewriteRun(
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        key1=value1
                        """,
                        """
                        key1=value1
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring.factories")
                ),
                text(
                        null,
                        """
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                )
        );
    }

    @Test
    void changeAnnotationsOnConfigurationClasses() {

        rewriteRun(
                spec -> spec.parser(JavaParser.fromJavaVersion().classpath("spring-context")),
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        key1=value1
                        """,
                        """
                        key1=value1
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring.factories")
                ),
                text(
                        null,
                        """
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                ),
                java(
                        """
                        package org.springframework.boot.autoconfigure.amqp;
                        
                        import org.springframework.context.annotation.Configuration;

                        @Configuration
                        public class RabbitAutoConfiguration {
                        }
                        """,
                        """
                        package org.springframework.boot.autoconfigure.amqp;
                        
                        import org.springframework.boot.autoconfigure.AutoConfiguration;

                        @AutoConfiguration
                        public class RabbitAutoConfiguration {
                        }
                        """
                )
        );
    }
    @Test
    void mergeAutoConfigFromSpringFactoriesIntoExisting() {
        rewriteRun(
                text(
                        """
                        org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration,\\
                        org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
                        """,
                        null,
                        spec -> spec.path("src/main/resources/META-INF/spring.factories")
                ),
                text(
                        """
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
                        org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
                        org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
                        org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
                        """,
                        """
                        org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
                        org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
                        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
                        org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
                        org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
                        org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration
                        org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
                        org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
                        """,
                        spec -> spec.path("src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                )
        );
    }

    Properties load(String propertySource) {
        Properties properties = new Properties();

        try (Reader reader = new StringReader(propertySource)){
            properties.load(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }
}
