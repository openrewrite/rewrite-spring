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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class ChangeSpringPropertyKeyTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.beforeRecipe(sources -> {

        });
    }

    @DocumentExample
    @Test
    void changeLastKey() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("server.servlet-path", "server.servlet.path", null)),
          mavenProject("",
            srcMainResources(
              properties(
                """
                  server.servlet-path=/tmp/my-server-path
                  """,
                """
                  server.servlet.path=/tmp/my-server-path
                  """,
                spec -> spec.path("application.properties")
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
              ))
          )
          //language=properties
        );
    }

    @Test
    void changePropertyPath() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("session.cookie.path", "servlet.session.cookie.path", null)),
          mavenProject("",
            srcMainResources(
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
            )));
    }


    @Test
    void subproperties() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("spring.resources", "spring.web.resources", null)),
          mavenProject("",
            srcMainResources(
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
            )));
    }

    @Test
    void except() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("spring.profiles", "spring.config.activate.on-profile", List.of("active", "default", "group", "include"))),
          mavenProject("",
            srcMainResources(
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
            )
          )
        );
    }

    @Test
    void avoidRegenerativeChanges() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("logging.file", "logging.file.name", null)),
          mavenProject("",
            srcMainResources(
              properties(
                """
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
              yaml(
                """
                  logging:
                    file: foo.txt
                  """,
                """
                  logging:
                    file.name: foo.txt
                  """
              ),
              yaml(
                """
                  logging.file: foo.txt
                  """,
                """
                  logging.file.name: foo.txt
                  """
              ),
              yaml(
                """
                  logging:
                    file:
                      name: foo.txt
                  """
              ),
              yaml(
                """
                  logging.file.name: foo.txt
                  """
              )
            )
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/432")
    @Test
    void loggingFileSubproperties() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/spring-boot-22-properties.yml", "org.openrewrite.java.spring.boot2.SpringBootProperties_2_2"),
          mavenProject("",
            srcMainResources(
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
            )
          )
        );
    }

    @Test
    @Disabled
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/436")
    void loggingFileSubpropertiesYaml() {
        rewriteRun(
          spec -> spec.recipeFromResource("/META-INF/rewrite/spring-boot-22-properties.yml", "org.openrewrite.java.spring.boot2.SpringBootProperties_2_2"),
          mavenProject("",
            srcMainResources(
              yaml(
                """
                  logging:
                    file:
                      max-history: 10
                      max-size: 10MB
                    level:
                      org: INFO
                    path: ${user.home}/some-folder
                  """,
                """
                  logging:
                    file:
                      max-history: 10
                      max-size: 10MB
                      path: ${user.home}/some-folder
                    level:
                      org: INFO
                  """
              )
            )
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/231")
    void changeValueAnnotation() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("server.servlet-path", "server.servlet.path", List.of("foo")))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-beans")),
          java(
            """
              import org.springframework.beans.factory.annotation.Value;

              class MyConfiguration {
                  @Value("${server.servlet-path.foo.bar} ${server.servlet-path.bar}")
                  private String servletPath;

                  @Value("${server.servlet-path.foo:/\\\\{foo\\\\}} ${server.servlet-path.bar:/\\\\{bar\\\\}}")
                  private String servletPathWithDefault;

                  @Value("${server.servlet-path}/api")
                  private String apiPath;
              }
              """,
            """
              import org.springframework.beans.factory.annotation.Value;

              class MyConfiguration {
                  @Value("${server.servlet-path.foo.bar} ${server.servlet.path.bar}")
                  private String servletPath;

                  @Value("${server.servlet-path.foo:/\\\\{foo\\\\}} ${server.servlet.path.bar:/\\\\{bar\\\\}}")
                  private String servletPathWithDefault;

                  @Value("${server.servlet.path}/api")
                  private String apiPath;
              }
              """
          )
        );
    }

    @Test
    @Issue("https://github.com/openrewrite/rewrite-spring/issues/231")
    void changeConditionalOnPropertyAnnotation() {
        rewriteRun(
          spec -> spec.recipe(new ChangeSpringPropertyKey("foo", "bar", List.of("baz")))
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-autoconfigure")),
          java(
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

              class MyConfiguration {
                  @ConditionalOnProperty(name = "foo", havingValue = "true")
                  public String foo() {
                      return "foo";
                  }

                  @ConditionalOnProperty(name = "foo.foo", havingValue = "true")
                  public String fooFoo() {
                      return "foo.foo";
                  }

                  @ConditionalOnProperty(name = "foo.bar", havingValue = "true")
                  public String fooBar() {
                      return "foo.bar";
                  }

                  @ConditionalOnProperty(name = "foo.baz", havingValue = "true")
                  public String fooBaz() {
                      return "foo.baz";
                  }
              }
              """,
            """
              import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

              class MyConfiguration {
                  @ConditionalOnProperty(name = "bar", havingValue = "true")
                  public String foo() {
                      return "foo";
                  }

                  @ConditionalOnProperty(name = "bar.foo", havingValue = "true")
                  public String fooFoo() {
                      return "foo.foo";
                  }

                  @ConditionalOnProperty(name = "bar.bar", havingValue = "true")
                  public String fooBar() {
                      return "foo.bar";
                  }

                  @ConditionalOnProperty(name = "foo.baz", havingValue = "true")
                  public String fooBaz() {
                      return "foo.baz";
                  }
              }
              """
          )
        );
    }
}
