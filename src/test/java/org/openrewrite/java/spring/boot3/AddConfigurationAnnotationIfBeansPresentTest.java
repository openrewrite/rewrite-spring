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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot2.AddConfigurationAnnotationIfBeansPresent;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class AddConfigurationAnnotationIfBeansPresentTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddConfigurationAnnotationIfBeansPresent())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-autoconfigure-3", "spring-boot-3",
              "spring-beans-6", "spring-context-6", "spring-web-6", "spring-core-6",
              "spring-security-core-6", "spring-security-config-6"));
    }

    @DocumentExample
    @Test
    void enableWebSecurityWithBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @EnableWebSecurity
              class A {
                @Bean String hello() { return "hello"; }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @Configuration
              @EnableWebSecurity
              class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void enableWebSecurityNoBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @EnableWebSecurity
              class A {}
              """
          )
        );
    }

    @Test
    void configurationMetaAnnotation() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.autoconfigure.SpringBootApplication;
              import org.springframework.context.annotation.Bean;

              @SpringBootApplication
              class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void abstractClassWithBeans() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              abstract class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

    @Test
    void noAnnotationsWithBean() {
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Bean;

              class A {
                @Bean String hello() { return "hello"; }
              }
              """,
            """
              import org.springframework.context.annotation.Bean;
              import org.springframework.context.annotation.Configuration;

              @Configuration
              class A {
                @Bean String hello() { return "hello"; }
              }
              """
          )
        );
    }

}
