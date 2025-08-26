/*
 * Copyright 2025 the original author or authors.
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static java.util.Collections.emptyList;
import static org.openrewrite.yaml.Assertions.yaml;

class MigrateEndpointAccessValueSpring34Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-boot-starter-actuator"))
          .recipeFromResource(
            "/META-INF/rewrite/spring-boot-34-properties.yml",
            "org.openrewrite.java.spring.boot3.SpringBootProperties_3_4");
    }

    @Nested
    class EnabledByDefault {
        @Test
        void migrateTrue() {
            rewriteRun(
              properties(
                //language=properties
                """
                  management.endpoints.enabled-by-default=true
                  """,
                //language=properties
                """
                  management.endpoints.access.default=read-only
                  """,
                sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
              ),
              yaml(
                //language=yaml
                """
                  management:
                    endpoints:
                      enabled-by-default: true
                  """,
                //language=yaml
                """
                  management:
                    endpoints:
                      access.default: read-only
                  """,
                sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
              )
            );
        }

        @Test
        void migrateFalse() {
            rewriteRun(
              properties(
                //language=properties
                """
                  management.endpoints.enabled-by-default=false
                  """,
                //language=properties
                """
                  management.endpoints.access.default=none
                  """,
                sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
              ),
              yaml(
                //language=yaml
                """
                  management:
                    endpoints:
                      enabled-by-default: false
                  """,
                //language=yaml
                """
                  management:
                    endpoints:
                      access.default: none
                  """,
                sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
              )
            );
        }
    }

    @Nested
    class Endpoints {
        @Nested
        class Properties {
            @Test
            void migrateTrue() {
                rewriteRun(
                  properties(
                    //language=properties
                    """
                      management.endpoint.auditevents.enabled=true
                      management.endpoint.threaddump.enabled=true
                      """,
                    //language=properties
                    """
                      management.endpoint.auditevents.access=read-only
                      management.endpoint.threaddump.access=read-only
                      """,
                    sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
                  )
                );
            }

            @Test
            void migrateFalse() {
                rewriteRun(
                  properties(
                    //language=properties
                    """
                      management.endpoint.auditevents.enabled=false
                      management.endpoint.threaddump.enabled=false
                      """,
                    //language=properties
                    """
                      management.endpoint.auditevents.access=none
                      management.endpoint.threaddump.access=none
                      """,
                    sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
                  )
                );
            }
        }

        @Nested
        class YAML {
            @Test
            void migrateTrue() {
                rewriteRun(
                  yaml(
                    //language=yaml
                    """
                      management:
                        endpoint:
                          auditevents:
                            enabled: true
                          threaddump:
                            enabled: true
                      """,
                    //language=yaml
                    """
                      management:
                        endpoint:
                          auditevents:
                            access: read-only
                          threaddump:
                            access: read-only
                      """,
                    sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
                  )
                );
            }

            @Test
            void migrateFalse() {
                rewriteRun(
                  yaml(
                    //language=yaml
                    """
                      management:
                        endpoint:
                          auditevents:
                            enabled: false
                          threaddump:
                            enabled: false
                      """,
                    //language=yaml
                    """
                      management:
                        endpoint:
                          auditevents:
                            access: none
                          threaddump:
                            access: none
                      """,
                    sourceSpecs -> sourceSpecs.markers(JavaSourceSet.build("test", emptyList()))
                  )
                );
            }
        }
    }
}
