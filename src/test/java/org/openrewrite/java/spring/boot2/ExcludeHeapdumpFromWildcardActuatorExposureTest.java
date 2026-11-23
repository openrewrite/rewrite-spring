/*
 * Copyright 2026 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcMainResources;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ExcludeHeapdumpFromWildcardActuatorExposureTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ExcludeHeapdumpFromWildcardActuatorExposure());
    }

    @DocumentExample
    @Test
    void wildcardInProperties() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                """
                  management.endpoints.web.exposure.include=*
                  """,
                """
                  management.endpoints.web.exposure.exclude=heapdump
                  management.endpoints.web.exposure.include=*
                  """
              )
            )
          )
        );
    }

    @Test
    void propertiesWithOtherEndpoints() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              properties(
                """
                  management.endpoints.web.exposure.include=health, *
                  """,
                """
                  management.endpoints.web.exposure.exclude=heapdump
                  management.endpoints.web.exposure.include=health, *
                  """
              )
            )
          )
        );
    }

    @Test
    void wildcardInYaml() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: "*"
                  """,
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: "*"
                          exclude: heapdump
                  """
              )
            )
          )
        );
    }

    @Test
    void yamlSingleQuoted() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: '*'
                  """,
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: '*'
                          exclude: heapdump
                  """
              )
            )
          )
        );
    }

    @Test
    void yamlCommaSeparated() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: "health, *"
                  """,
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: "health, *"
                          exclude: heapdump
                  """
              )
            )
          )
        );
    }

    @Test
    void yamlFlowSequence() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: ["*"]
                  """,
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: ["*"]
                          exclude: heapdump
                  """
              )
            )
          )
        );
    }

    @Test
    void yamlBlockSequence() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include:
                            - health
                            - "*"
                  """,
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include:
                            - health
                            - "*"
                          exclude: heapdump
                  """
              )
            )
          )
        );
    }

    @Test
    void profileSpecificFile() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: "*"
                  """,
                """
                  management:
                    endpoints:
                      web:
                        exposure:
                          include: "*"
                          exclude: heapdump
                  """,
                spec -> spec.path("application-dev.yml")
              )
            )
          )
        );
    }

    @Test
    void flattenedYamlKey() {
        rewriteRun(
          mavenProject("project",
            srcMainResources(
              yaml(
                """
                  management.endpoints.web.exposure.include: "*"
                  """,
                """
                  management.endpoints.web.exposure.include: "*"
                  management:
                    endpoints:
                      web:
                        exposure:
                          exclude: heapdump
                  """
              )
            )
          )
        );
    }

    @Test
    void onlyProjectWithoutExcludeIsChanged() {
        rewriteRun(
          mavenProject("first",
            srcMainResources(
              properties(
                """
                  management.endpoints.web.exposure.include=*
                  management.endpoints.web.exposure.exclude=env
                  """
              )
            )
          ),
          mavenProject("second",
            srcMainResources(
              properties(
                """
                  management.endpoints.web.exposure.include=*
                  """,
                """
                  management.endpoints.web.exposure.exclude=heapdump
                  management.endpoints.web.exposure.include=*
                  """
              )
            )
          )
        );
    }

    @Nested
    class NoChange {

        @Test
        void explicitEndpoints() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  properties(
                    """
                      management.endpoints.web.exposure.include=health,info
                      """
                  )
                )
              )
            );
        }

        @Test
        void heapdumpIncludedExplicitly() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  properties(
                    """
                      management.endpoints.web.exposure.include=*,heapdump
                      """
                  )
                )
              )
            );
        }

        @Test
        void excludeAlreadySet() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  yaml(
                    """
                      management:
                        endpoints:
                          web:
                            exposure:
                              include: "*"
                              exclude: env
                      """
                  )
                )
              )
            );
        }

        @Test
        void indexedExcludeAlreadySet() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  properties(
                    """
                      management.endpoints.web.exposure.include=*
                      management.endpoints.web.exposure.exclude[0]=env
                      """
                  )
                )
              )
            );
        }

        @Test
        void indexedHeapdumpIncludedExplicitly() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  yaml(
                    """
                      management.endpoints.web.exposure.include: "*"
                      management.endpoints.web.exposure.include[1]: heapdump
                      """
                  )
                )
              )
            );
        }

        @Test
        void excludeSetInAnotherConfigFile() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  properties(
                    """
                      management.endpoints.web.exposure.exclude=env
                      """,
                    spec -> spec.path("application.properties")
                  ),
                  properties(
                    """
                      management.endpoints.web.exposure.include=*
                      """,
                    spec -> spec.path("application-dev.properties")
                  )
                )
              )
            );
        }

        @Test
        void heapdumpIncludedInAnotherConfigFile() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  properties(
                    """
                      management.endpoints.web.exposure.include=*
                      """,
                    spec -> spec.path("application.properties")
                  ),
                  properties(
                    """
                      management.endpoints.web.exposure.include=*,heapdump
                      """,
                    spec -> spec.path("application-dev.properties")
                  )
                )
              )
            );
        }

        @Test
        void multiDocumentYaml() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  yaml(
                    """
                      spring:
                        application:
                          name: demo
                      ---
                      spring:
                        config:
                          activate:
                            on-profile: dev
                      management:
                        endpoints:
                          web:
                            exposure:
                              include: "*"
                      """
                  )
                )
              )
            );
        }

        @Test
        void noExposureConfigured() {
            rewriteRun(
              mavenProject("project",
                srcMainResources(
                  properties(
                    """
                      server.port=8080
                      """
                  )
                )
              )
            );
        }
    }
}
