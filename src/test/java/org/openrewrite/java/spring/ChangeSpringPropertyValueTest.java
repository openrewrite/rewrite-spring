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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ChangeSpringPropertyValueTest implements RewriteTest {
    @DocumentExample
    @Test
    void propFile() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null,  null)),
          properties("server.port=8080", "server.port=8081")
        );
    }

    @Test
    void yamlDotSeparated() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null,  null)),
          yaml("server.port: 8080", "server.port: 8081")
        );
    }

    @Test
    void yamlIndented() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null,  null)),
          yaml("server:\n  port: 8080", "server:\n  port: 8081")
        );
    }

    @Test
    void regex() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "80$1", "^([0-9]{2})$", true,  null)),
          properties("server.port=53", "server.port=8053"),
          yaml("server.port: 53", "server.port: 8053")
        );
    }

    @Test
    void yamlValueQuoted() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("management.endpoints.web.exposure.include", "*", null, null,  null)),
          properties("management.endpoints.web.exposure.include=info,health", "management.endpoints.web.exposure.include=*"),
          yaml(
            """
              management:
                endpoints:
                  web:
                    exposure:
                      include: info,health
            """,
            """
              management:
                endpoints:
                  web:
                    exposure:
                      include: "*"
            """
          )
        );
    }

    @Test
    void valueAnnotationDefaultValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5")),
          java(
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port:8080}")
                  private int port;
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port:8081}")
                  private int port;
              }
              """
          )
        );
    }

    @Test
    void valueAnnotationWithOldValueConstraint() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "9090", "8080", null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5")),
          java(
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port:8080}")
                  private int port;
                  @Value("${server.port:3000}")
                  private int otherPort;
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port:9090}")
                  private int port;
                  @Value("${server.port:3000}")
                  private int otherPort;
              }
              """
          )
        );
    }

    @Test
    void valueAnnotationWithRegex() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "80$1", "^([0-9]{2})$", true, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5")),
          java(
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port:53}")
                  private int port;
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port:8053}")
                  private int port;
              }
              """
          )
        );
    }

    @Test
    void valueAnnotationNoDefaultValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5")),
          java(
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server.port}")
                  private int port;
              }
              """
          )
        );
    }

    @Test
    void valueAnnotationRelaxedBinding() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, true))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5")),
          java(
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server-port:8080}")
                  private int port;
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Value;
              class A {
                  @Value("${server-port:8081}")
                  private int port;
              }
              """
          )
        );
    }

    @Test
    void conditionalOnPropertyHavingValue() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("feature.enabled", "true", "false", null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-autoconfigure-2")),
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
              @ConditionalOnProperty(name = "feature.enabled", havingValue = "false")
              class A {
              }
              """,
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
              @ConditionalOnProperty(name = "feature.enabled", havingValue = "true")
              class A {
              }
              """
          )
        );
    }

    @Test
    void conditionalOnPropertyDifferentKey() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("feature.enabled", "true", "false", null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-autoconfigure-2")),
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
              @ConditionalOnProperty(name = "other.feature", havingValue = "false")
              class A {
              }
              """
          )
        );
    }

    @Test
    void springBootTestProperties() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-test-3.2")),
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              @SpringBootTest(properties = "server.port=8080")
              class A {
              }
              """,
            """
              import org.springframework.boot.test.context.SpringBootTest;
              @SpringBootTest(properties = "server.port=8081")
              class A {
              }
              """
          )
        );
    }

    @Test
    void springBootTestPropertiesArray() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-test-3.2")),
          java(
            """
              import org.springframework.boot.test.context.SpringBootTest;
              @SpringBootTest(properties = {"server.port=8080", "other.prop=value"})
              class A {
              }
              """,
            """
              import org.springframework.boot.test.context.SpringBootTest;
              @SpringBootTest(properties = {"server.port=8081", "other.prop=value"})
              class A {
              }
              """
          )
        );
    }

    @Test
    void testPropertySourceProperties() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("app.name", "NewApp", "OldApp", null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-test-5")),
          java(
            """
              import org.springframework.test.context.TestPropertySource;
              @TestPropertySource(properties = "app.name=OldApp")
              class A {
              }
              """,
            """
              import org.springframework.test.context.TestPropertySource;
              @TestPropertySource(properties = "app.name=NewApp")
              class A {
              }
              """
          )
        );
    }

    @Test
    void testPropertySourcePropertiesArray() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("app.name", "NewApp", null, null, null))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-test-5")),
          java(
            """
              import org.springframework.test.context.TestPropertySource;
              @TestPropertySource(properties = {"app.name=OldApp", "app.version=1.0"})
              class A {
              }
              """,
            """
              import org.springframework.test.context.TestPropertySource;
              @TestPropertySource(properties = {"app.name=NewApp", "app.version=1.0"})
              class A {
              }
              """
          )
        );
    }

    @Test
    void kotlinValueAnnotation() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, null))
            .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "spring-beans-5")),
          kotlin(
            """
              import org.springframework.beans.factory.annotation.Value
              class A(@Value("\\${server.port:8080}") val port: Int)
              """,
            """
              import org.springframework.beans.factory.annotation.Value
              class A(@Value("\\${server.port:8081}") val port: Int)
              """
          )
        );
    }

    @Test
    void kotlinSpringBootTestProperties() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyValue("server.port", "8081", null, null, null))
            .parser(KotlinParser.builder().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-test-3.2")),
          kotlin(
            """
              import org.springframework.boot.test.context.SpringBootTest
              @SpringBootTest(properties = ["server.port=8080"])
              class A
              """,
            """
              import org.springframework.boot.test.context.SpringBootTest
              @SpringBootTest(properties = ["server.port=8081"])
              class A
              """
          )
        );
    }
}
