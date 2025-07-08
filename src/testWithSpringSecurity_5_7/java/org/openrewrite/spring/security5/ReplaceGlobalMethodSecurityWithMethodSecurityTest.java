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
package org.openrewrite.spring.security5;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurity;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

class ReplaceGlobalMethodSecurityWithMethodSecurityTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReplaceGlobalMethodSecurityWithMethodSecurity())
          .parser(JavaParser.fromJavaVersion().classpath("spring-security-config-5.+"));
    }

    @DocumentExample
    @Test
    void replaceWithPrePostEnabled() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity(prePostEnabled = true)
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity
              public class config {
              }
              """
          )
        );
    }

    @Test
    void emptyAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity(prePostEnabled = false)
              public class config {
              }
              """
          )
        );
    }

    @Test
    void replaceWithNotPrePostEnabled() {
        rewriteRun(
          //language=java
          java(
            """
              import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

              @EnableGlobalMethodSecurity(securedEnabled = true)
              public class config {
              }
              """,
            """
              import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

              @EnableMethodSecurity(securedEnabled = true, prePostEnabled = false)
              public class config {
              }
              """
          )
        );
    }

    @Test
    void removeUseAuthorizationManagerAttribute() {
        rewriteRun(
          spec -> spec.recipe(
            Environment.builder()
              .scanRuntimeClasspath("org.openrewrite.java.spring")
              .build()
              .activateRecipes("org.openrewrite.java.spring.security5.ReplaceGlobalMethodSecurityWithMethodSecurityXml")
          ),
          //language=xml
          xml(
            """
              <b:beans xmlns:b="http://www.springframework.org/schema/beans"
              		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              		 xmlns="http://www.springframework.org/schema/security"
              		 xsi:schemaLocation="http://www.springframework.org/schema/security https://www.springframework.org/schema/security/spring-security.xsd
              		http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
                <global-method-security pre-post-enabled="true"/>
              </b:beans>
              """,
            """
              <b:beans xmlns:b="http://www.springframework.org/schema/beans"
              		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              		 xmlns="http://www.springframework.org/schema/security"
              		 xsi:schemaLocation="http://www.springframework.org/schema/security https://www.springframework.org/schema/security/spring-security.xsd
              		http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
                <method-security/>
              </b:beans>
              """
          )
        );
    }
}
