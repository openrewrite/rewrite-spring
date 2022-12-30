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
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;


public class AddSpringPropertyTest implements RewriteTest {

    @Test
    void addNestedIntoExisting() {
        rewriteRun(
                spec -> spec.recipe(new AddSpringProperty("server.servlet.path", "/tmp/my-server-path", null, List.of("*"))),
                properties(
                        """
                        server.port=8080
                        """,
                        """
                        server.port=8080
                        server.servlet.path=/tmp/my-server-path
                        """
                ),
                yaml(
                        """
                            server:
                              port: 8080
                        """,
                        """
                            server:
                              port: 8080
                              servlet:
                                path: /tmp/my-server-path
                        """
                )
        );
    }

    @Test
    void addPropertyToRoot() {
        rewriteRun(
                spec -> spec.recipe(new AddSpringProperty("fred", "fred", null, List.of("*"))),
                properties(
                        """
                        servlet.session.cookie.path=/cookie-monster
                        """,
                        """
                        servlet.session.cookie.path=/cookie-monster
                        fred=fred
                        """
                ),
                yaml(
                        """
                            server:
                              port: 8888
                        """,
                        """
                        server:
                          port: 8888
                        fred: fred
                        """
                )
        );
    }

    @Test
    void propertyAlreadyExists() {
        rewriteRun(
                spec -> spec.recipe(new AddSpringProperty("fred", "fred", null, List.of("*"))),
                properties(
                        """
                        servlet.session.cookie.path=/cookie-monster
                        fred=doNotChangeThis
                        """
                ),
                yaml(
                        """
                        server:
                          port: 8888
                        fred: doNotChangeThis
                        """
                )
        );
    }

    @Test
    void addPropertyWithComment() {
        rewriteRun(
                spec -> spec.recipe(new AddSpringProperty("server.servlet.path", "/tmp/my-server-path", "This property was added", List.of("*"))),
                properties(
                        """
                        server.port=8080
                        """,
                        """
                        server.port=8080
                        server.servlet.path=/tmp/my-server-path
                        """
                ),
                yaml(
                        """
                            server:
                              port: 8080
                        """,
                        """
                            server:
                              port: 8080
                              servlet:
                                # This property was added
                                path: /tmp/my-server-path
                        """
                )
        );
    }

    @Test
    void makeChangeToMatchingFiles() {
        rewriteRun(
                spec -> spec.recipe(new AddSpringProperty("server.servlet.path", "/tmp/my-server-path", null, List.of("**/application.properties", "**/application.yml"))),
                properties(
                        """
                        server.port=8080
                        """,
                        """
                        server.port=8080
                        server.servlet.path=/tmp/my-server-path
                        """,
                        s -> s.path("src/main/resources/application.properties")
                ),
                yaml(
                        """
                            server:
                              port: 8080
                        """,
                        """
                            server:
                              port: 8080
                              servlet:
                                path: /tmp/my-server-path
                        """,
                        s -> s.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void doNotChangeToFilesThatDoNotMatch() {
        rewriteRun(
                spec -> spec.recipe(new AddSpringProperty("server.servlet.path", "/tmp/my-server-path", null, List.of("**/application.properties", "**/application.yml"))),
                properties(
                        """
                        server.port=8080
                        """,
                        s -> s.path("src/main/resources/application-test.properties")
                ),
                yaml(
                        """
                            server:
                              port: 8080
                        """,
                        s -> s.path("src/main/resources/application-dev.yml")
                )
        );
    }
}
