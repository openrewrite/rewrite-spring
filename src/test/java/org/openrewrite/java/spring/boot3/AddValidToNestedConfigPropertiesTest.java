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
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.UpgradeParentVersion;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.maven.Assertions.pomXml;

class AddValidToNestedConfigPropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddValidToNestedConfigProperties())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-3.5",
              "spring-context-5.+",
              "jakarta.validation-api-3.0.+")
            .dependsOn(
              // language=java
              """
                package com.example.demo;

                import jakarta.validation.constraints.Min;

                public class NestedProperties {
                    @Min(1)
                    private int count;

                    public int getCount() {
                        return count;
                    }

                    public void setCount(int count) {
                        this.count = count;
                    }
                }
                """
            )
          );
    }

    @DocumentExample
    @Test
    void addValidToNestedProperties() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {

                  private String name;

                  private NestedProperties nested;
              }
              """,
            """
              package com.example.demo;

              import jakarta.validation.Valid;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {

                  private String name;

                  @Valid
                  private NestedProperties nested;
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidToSimpleTypes() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;
              import jakarta.validation.constraints.NotNull;
              import java.util.List;
              import java.util.Map;

              @ConfigurationProperties("app")
              @Validated
              public class SimpleProperties {
                  @NotNull
                  private String name;
                  private int count;
                  private List<String> items;
                  private Map<String, String> mappings;
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidIfAlreadyPresent() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;
              import jakarta.validation.Valid;

              @ConfigurationProperties("app")
              @Validated
              public class AlreadyValidatedProperties {

                  @Valid
                  private NestedProperties nested;
              }
              """
          )
        );
    }

    @Test
    void ignoreNonConfigurationPropertiesClasses() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.validation.annotation.Validated;

              @Validated
              public class RegularClass {
                  private NestedProperties nested;

                  public NestedProperties getNested() {
                      return nested;
                  }

                  public void setNested(NestedProperties nested) {
                      this.nested = nested;
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidToLocalVariables() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.context.annotation.Bean;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  @Bean
                  public NestedProperties serviceProperties() {
                      var serviceProperties = new NestedProperties();
                      return serviceProperties;
                  }
              }
              """
          )
        );
    }

    @Test
    void addValidToFieldsButNotToDeclarationsInInitializersOrEnums() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import java.util.function.Consumer;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  private NestedProperties nested;
                  private final Consumer<NestedProperties> logger = (NestedProperties each) -> each.getCount();
                  private final Object holder = new Object() {
                      class Inner {
                          NestedProperties inner;
                      }
                  };

                  {
                      NestedProperties defaults = new NestedProperties();
                      class Local {
                          NestedProperties local;
                      }
                  }

                  enum Mode {
                      COUNT((NestedProperties each) -> each.getCount());

                      private final NestedProperties sample = null;

                      Mode(Consumer<NestedProperties> action) {
                      }
                  }
              }
              """,
            """
              package com.example.demo;

              import java.util.function.Consumer;

              import jakarta.validation.Valid;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  @Valid
                  private NestedProperties nested;
                  private final Consumer<NestedProperties> logger = (NestedProperties each) -> each.getCount();
                  private final Object holder = new Object() {
                      class Inner {
                          NestedProperties inner;
                      }
                  };

                  {
                      NestedProperties defaults = new NestedProperties();
                      class Local {
                          NestedProperties local;
                      }
                  }

                  enum Mode {
                      COUNT((NestedProperties each) -> each.getCount());

                      private final NestedProperties sample = null;

                      Mode(Consumer<NestedProperties> action) {
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void addValidToFieldsAndRecordComponentsButNotToParameters() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  private NestedProperties nested;

                  public NestedProperties getNested() {
                      return nested;
                  }

                  public void setNested(NestedProperties nested) {
                      this.nested = nested;
                  }
              }
              """,
            """
              package com.example.demo;

              import jakarta.validation.Valid;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  @Valid
                  private NestedProperties nested;

                  public NestedProperties getNested() {
                      return nested;
                  }

                  public void setNested(NestedProperties nested) {
                      this.nested = nested;
                  }
              }
              """
          ),
          // language=java
          java(
            """
              package com.example.demo;

              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("record")
              @Validated
              public record RecordProperties(String name, NestedProperties nested) {
              }
              """,
            """
              package com.example.demo;

              import jakarta.validation.Valid;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("record")
              @Validated
              public record RecordProperties(String name, @Valid NestedProperties nested) {
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidToEnums() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.demo;

              import jakarta.validation.constraints.NotNull;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  @NotNull
                  private Mode mode = Mode.SINGLE;

                  public enum Mode {
                      SINGLE,
                      MULTI
                  }
              }
              """
          )
        );
    }

    @Test
    void doNotAddValidToStaticFields() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "spring-boot-3.5",
              "spring-context-5.+",
              "jakarta.validation-api-3.0.+",
              "slf4j-api-2.+")),
          // language=java
          java(
            """
              package com.example.demo;

              import org.slf4j.Logger;
              import org.slf4j.LoggerFactory;
              import org.springframework.boot.context.properties.ConfigurationProperties;
              import org.springframework.validation.annotation.Validated;

              @ConfigurationProperties("app")
              @Validated
              public class AppProperties {
                  private static final Logger log = LoggerFactory.getLogger(AppProperties.class);

                  private String name;
              }
              """
          )
        );
    }

    @Nested
    class BeforeBoot3_4 implements RewriteTest {

        @Override
        public void defaults(RecipeSpec spec) {
            AddValidToNestedConfigPropertiesTest.this.defaults(spec);
            spec.recipeFromResources("org.openrewrite.java.spring.boot3.AddValidToNestedConfigPropertiesBeforeBoot3_4");
        }

        @Test
        void addValidWithoutKnownSpringBootVersion() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.demo;

                  import org.springframework.boot.context.properties.ConfigurationProperties;
                  import org.springframework.validation.annotation.Validated;

                  @ConfigurationProperties("app")
                  @Validated
                  public class AppProperties {
                      private NestedProperties nested;
                  }
                  """,
                """
                  package com.example.demo;

                  import jakarta.validation.Valid;
                  import org.springframework.boot.context.properties.ConfigurationProperties;
                  import org.springframework.validation.annotation.Validated;

                  @ConfigurationProperties("app")
                  @Validated
                  public class AppProperties {
                      @Valid
                      private NestedProperties nested;
                  }
                  """
              )
            );
        }

        @Test
        void addValidWhenTheSameRunUpgradesSpringBootTo3_4() {
            rewriteRun(
              spec -> spec.recipes(
                new UpgradeParentVersion("org.springframework.boot", "spring-boot-starter-parent", "3.4.x", null, null),
                Environment.builder().scanYamlResources().build()
                  .activateRecipes("org.openrewrite.java.spring.boot3.AddValidToNestedConfigPropertiesBeforeBoot3_4")
              ),
              mavenProject("demo",
                pomXml(
                  // language=xml
                  """
                    <project>
                        <parent>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-parent</artifactId>
                            <version>3.3.13</version>
                        </parent>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>1.0.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter</artifactId>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> assertThat(actual)
                    .containsPattern("<parent>\\s*<groupId>org.springframework.boot</groupId>\\s*" +
                                     "<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>3\\.4\\.\\d+</version>")
                    .actual())
                ),
                // language=java
                java(
                  """
                    package com.example.demo;

                    import org.springframework.boot.context.properties.ConfigurationProperties;
                    import org.springframework.validation.annotation.Validated;

                    @ConfigurationProperties("app")
                    @Validated
                    public class AppProperties {
                        private NestedProperties nested;
                    }
                    """,
                  """
                    package com.example.demo;

                    import jakarta.validation.Valid;
                    import org.springframework.boot.context.properties.ConfigurationProperties;
                    import org.springframework.validation.annotation.Validated;

                    @ConfigurationProperties("app")
                    @Validated
                    public class AppProperties {
                        @Valid
                        private NestedProperties nested;
                    }
                    """
                )
              )
            );
        }

        @Nested
        class NoChange {

            @Test
            void onSpringBoot3_4() {
                rewriteRun(
                  mavenProject("demo",
                    pomXml(
                      // language=xml
                      """
                        <project>
                            <groupId>com.example</groupId>
                            <artifactId>demo</artifactId>
                            <version>1.0.0</version>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot</artifactId>
                                    <version>3.4.0</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """
                    ),
                    // language=java
                    java(
                      """
                        package com.example.demo;

                        import org.springframework.boot.context.properties.ConfigurationProperties;
                        import org.springframework.validation.annotation.Validated;

                        @ConfigurationProperties("app")
                        @Validated
                        public class AppProperties {
                            private NestedProperties nested;
                        }
                        """
                    )
                  )
                );
            }
        }
    }
}
