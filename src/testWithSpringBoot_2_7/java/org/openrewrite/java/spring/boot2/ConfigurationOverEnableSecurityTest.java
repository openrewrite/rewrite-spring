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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.boot3.ConfigurationOverEnableSecurity;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConfigurationOverEnableSecurityTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ConfigurationOverEnableSecurity(false))
          .parser(JavaParser.fromJavaVersion()
            .classpath(
              "spring-beans",
              "spring-context",
              "spring-boot",
              "spring-security",
              "spring-web",
              "spring-core"));
    }

    @DocumentExample
    @Test
    void enableWebSecurity() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @EnableWebSecurity
              class A {}
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @Configuration
              @EnableWebSecurity
              class A {}
              """
          )
        );
    }

    @Test
    void doNotChangeIfItExist() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

              @Configuration
              @EnableWebSecurity
              class A {}
              """
          )
        );
    }

    @Test
    void enableRSocketSecurity() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;

              @EnableRSocketSecurity
              class A{}
              """
          )
        );
    }

    @Test
    void enableWebFluxSecurity() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

              @EnableWebFluxSecurity
              class A {}
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

              @Configuration
              @EnableWebFluxSecurity
              class A {}
              """
          )
        );
    }

    @Test
    void enableMethodSecurity() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity
              class A {}
              """,
            """
              import org.springframework.context.annotation.Configuration;
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @Configuration
              @EnableMethodSecurity
              class A {}
              """
          )
        );
    }
}
