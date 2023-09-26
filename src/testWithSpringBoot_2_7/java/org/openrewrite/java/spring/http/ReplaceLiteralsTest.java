/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.http;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class ReplaceLiteralsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes(
              "org.openrewrite.java.spring.http.ReplaceStringLiteralsWithHttpHeadersConstants",
              "org.openrewrite.java.spring.http.ReplaceStringLiteralsWithMediaTypeConstants"))
          .parser(JavaParser.fromJavaVersion().classpath("spring-web").logCompilationWarningsAndErrors(true));
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/325")
    @Test
    void shouldReplaceWithDirectDependency() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=Java
              java("""
                import java.util.Map;
                import org.springframework.web.bind.annotation.GetMapping;
                
                class Foo {
                    @GetMapping(path = "/foo", produces = "application/json")
                    Map<String, Object> foo() {
                        return Map.of("foo", "bar");
                    }
                }
                """, """
                import java.util.Map;
                
                import org.springframework.http.MediaType;
                import org.springframework.web.bind.annotation.GetMapping;
                
                class Foo {
                    @GetMapping(path = "/foo", produces = MediaType.APPLICATION_JSON_VALUE)
                    Map<String, Object> foo() {
                        return Map.of("foo", "bar");
                    }
                }
                """),
              //language=XML
              pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.10</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
              )
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/325")
    @Test
    void shouldReplaceWithTransitiveDependency() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=Java
              java("""
                import java.util.Map;
                import org.springframework.web.bind.annotation.GetMapping;
                
                class Foo {
                    @GetMapping(path = "/foo", produces = "application/json")
                    Map<String, Object> foo() {
                        return Map.of("foo", "bar");
                    }
                }
                """, """
                import java.util.Map;
                
                import org.springframework.http.MediaType;
                import org.springframework.web.bind.annotation.GetMapping;
                
                class Foo {
                    @GetMapping(path = "/foo", produces = MediaType.APPLICATION_JSON_VALUE)
                    Map<String, Object> foo() {
                        return Map.of("foo", "bar");
                    }
                }
                """),
              //language=XML
              pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.10</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
              )
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/325")
    @Test
    void shouldNotReplaceWithoutDependency() {
        rewriteRun(
          mavenProject("project",
            srcMainJava(
              //language=Java
              java("""
                import java.util.Map;
                
                class Foo {
                    Map<String, Object> foo() {
                        return Map.of("Accept", "application/json");
                    }
                }
                """),
              //language=XML
              pomXml("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>2.7.10</version>
                        <relativePath/> <!-- lookup parent from repository -->
                    </parent>
                    <groupId>com.example</groupId>
                    <artifactId>acme</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                </project>
                """
              )
            )
          )
        );
    }

}
