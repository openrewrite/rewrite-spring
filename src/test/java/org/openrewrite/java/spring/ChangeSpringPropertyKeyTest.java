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
package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.yaml.Assertions.yaml;
import static org.openrewrite.properties.Assertions.properties;


class ChangeSpringPropertyKeyTest implements RewriteTest {

    @DocumentExample
    @Test
    void changeLastKey() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("server.servlet-path", "server.servlet.path", null)),
          //language=properties
          properties(
            """
              server.servlet-path=/tmp/my-server-path
              """,
            """
              server.servlet.path=/tmp/my-server-path
              """
          ),
          //language=yaml
          yaml(
            """
              server:
                servlet-path: /tmp/my-server-path
              """,
            """
              server:
                servlet.path: /tmp/my-server-path
              """
          )
        );
    }

    @Test
    void changePropertyPath() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("session.cookie.path", "servlet.session.cookie.path", null)),
          //language=properties
          properties(
            """
              session.cookie.path=/cookie-monster
              """,
            """
              servlet.session.cookie.path=/cookie-monster
              """
          ),
          //language=yaml
          yaml(
            """
              server:
                port: 8888
              session:
                cookie:
                  path: /tmp/my-server-path
                """,
            """
              server:
                port: 8888
              servlet.session.cookie.path: /tmp/my-server-path
              """
          )
        );
    }


    @Test
    void subproperties() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("spring.resources", "spring.web.resources", null)),
          //language=properties
          properties(
            """
              spring.resources.chain.strategy.content.enabled= true
              spring.resources.chain.strategy.content.paths= /foo/**, /bar/**
              """,
            """
              spring.web.resources.chain.strategy.content.enabled= true
              spring.web.resources.chain.strategy.content.paths= /foo/**, /bar/**
              """
          ),
          //language=yaml
          yaml(
            """
              spring:
                resources:
                  chain:
                    strategy:
                      content:
                        enabled: true
                        paths:
                          - /foo/**
                          - /bar/**
                """,
            """
              spring:
                web.resources:
                  chain:
                    strategy:
                      content:
                        enabled: true
                        paths:
                          - /foo/**
                          - /bar/**
                """
          )
        );
    }

    @Test
    void except() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("spring.profiles", "spring.config.activate.on-profile", List.of("active", "default", "group", "include"))),
          //language=properties
          properties(
            """
              spring.profiles.group.local= local-security, local-db
              """
          ),
          //language=yaml
          yaml(
            """
              spring:
                profiles:
                  group:
                    local:
                      - local-security
                      - local-db
                """
          )
        );
    }

    @Test
    void avoidRegenerativeChanges() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("logging.file", "logging.file.name", null)),
          properties("""
            logging.file = foo.txt
            """,
            """
            logging.file.name = foo.txt
            """
          ),
          properties(
            """
            logging.file.name = foo.txt
            """
          ),
          yaml("""
            logging:
              file: foo.txt
            """,
            """
            logging:
              file.name: foo.txt
            """
          ),
          yaml("""
            logging.file: foo.txt
            """,
            """
            logging.file.name: foo.txt
            """
          ),
          yaml("""
            logging:
              file:
                name: foo.txt
            """
          ),
          yaml("""
            logging.file.name: foo.txt
            """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/432")
    @Test
    void loggingFileSubproperties() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot2.SpringBootProperties_2_2"),
          properties("""
            logging.file.max-size=10MB
            logging.file.max-history=10
            logging.path=${user.home}/some-folder
              """, """
            logging.file.max-size=10MB
            logging.file.max-history=10
            logging.file.path=${user.home}/some-folder
              """
          )
        );
    }

}
