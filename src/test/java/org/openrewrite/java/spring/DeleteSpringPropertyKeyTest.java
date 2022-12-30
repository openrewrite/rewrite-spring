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

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;


public class DeleteSpringPropertyKeyTest implements RewriteTest {

    @Test
    void deleteOnlyKey() {
        rewriteRun(
                spec -> spec.recipe(new DeleteSpringProperty("server.servlet-path")),
                properties(
                        """
                        server.servlet-path=/tmp/my-server-path
                        """,
                        """
                        """
                ),
                yaml(
                        """
                            server:
                              servlet-path: /tmp/my-server-path
                        """,
                        """
                        """
                )
        );
    }

    @Test
    void deleteFirstKey() {
        rewriteRun(
                spec -> spec.recipe(new DeleteSpringProperty("server.servlet.session.cookie.path")),
                properties(
                        """
                        server.servlet.session.cookie.path=/cookie-monster
                        server.servlet.session.cookie.name=fred
                        """,
                        """
                        server.servlet.session.cookie.name=fred
                        """
                ),
                yaml(
                        """
                            server:
                              servlet:
                                session:
                                  cookie:
                                    path: /cookie-monster
                                    name: fred
                        """,
                        """
                            server:
                              servlet:
                                session:
                                  cookie:
                                    name: fred
                        """
                )
        );
    }

    @Test
    void deleteLastKey() {
        rewriteRun(
                spec -> spec.recipe(new DeleteSpringProperty("server.servlet.session.cookie.path")),
                properties(
                        """
                        server.servlet.session.cookie.name=fred
                        server.servlet.session.cookie.path=/cookie-monster
                        """,
                        """
                        server.servlet.session.cookie.name=fred
                        """
                ),
                yaml(
                        """
                            server:
                              servlet:
                                session:
                                  cookie:
                                    name: fred
                                    path: /cookie-monster
                        """,
                        """
                            server:
                              servlet:
                                session:
                                  cookie:
                                    name: fred
                        """
                )
        );
    }

}
