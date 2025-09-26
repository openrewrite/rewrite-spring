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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateToWebServerFactoryCustomizerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot2.MigrateToWebServerFactoryCustomizer"))
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-1.+", "spring-context-4.+", "spring-beans-4.+"));
    }

    @DocumentExample
    @Test
    void migrateToWebServerFactoryCustomizer() {
        //language=java
        rewriteRun(
          java(
            """
              import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
              import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;

              public class CustomContainer implements EmbeddedServletContainerCustomizer {
                  @Override
                  public void customize(ConfigurableEmbeddedServletContainer container) {
                      container.setPort(8080);
                      container.setContextPath("");
                   }
              }
              """,
            """
              import org.springframework.boot.web.server.WebServerFactoryCustomizer;
              import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;

              public class CustomContainer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
                  @Override
                  public void customize(ConfigurableServletWebServerFactory container) {
                      container.setPort(8080);
                      container.setContextPath("");
                   }
              }
              """
          )
        );
    }

    @Test
    void migrateToWebServerFactoryAndChangeTomcat() {
        //language=java
        java(
          """
            import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
            import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
            import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;

            public class CustomContainer implements EmbeddedServletContainerCustomizer {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof TomcatEmbeddedServletContainerFactory) {
                        TomcatEmbeddedServletContainerFactory tomcatContainer =\s
                          (TomcatEmbeddedServletContainerFactory) container;
                        tomcatContainer.setPort(8080);
                        tomcatContainer.setContextPath("");
                    }
                }
            }
            """,
          """
            import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
            import org.springframework.boot.web.server.WebServerFactoryCustomizer;
            import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;

            public class CustomContainer implements EmbeddedServletContainerCustomizer {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof TomcatServletWebServerFactory) {
                        TomcatServletWebServerFactory tomcatContainer =
                          (TomcatServletWebServerFactory) container;
                        tomcatContainer.setPort(8080);
                        tomcatContainer.setContextPath("");
                    }
                }
            }
            """
        );
    }

    @Test
    void migrateToWebServerFactoryAndChangeJetty() {
        //language=java
        java(
          """
            import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
            import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
            import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;

            public class CustomContainer implements EmbeddedServletContainerCustomizer {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof JettyEmbeddedServletContainerFactory) {
                        JettyEmbeddedServletContainerFactory jettyContainer =\s
                          (JettyEmbeddedServletContainerFactory) container;
                        jettyContainer.setPort(8080);
                        jettyContainer.setContextPath("");
                    }
                }
            }
            """,
          """
            import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
            import org.springframework.boot.web.server.WebServerFactoryCustomizer;
            import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;

            public class CustomContainer implements EmbeddedServletContainerCustomizer {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof JettyServletWebServerFactory) {
                        JettyServletWebServerFactory jettyContainer =
                          (JettyServletWebServerFactory) container;
                        jettyContainer.setPort(8080);
                        jettyContainer.setContextPath("");
                    }
                }
            }
            """
        );
    }

    @Test
    void migrateToWebServerFactoryAndChangeUndertow() {
        //language=java
        java(
          """
            import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
            import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
            import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;

            public class CustomContainer implements EmbeddedServletContainerCustomizer {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof UndertowEmbeddedServletContainerFactory) {
                        UndertowEmbeddedServletContainerFactory undertowContainer = (UndertowEmbeddedServletContainerFactory) container;
                        undertowContainer.setPort(8080);
                        undertowContainer.setContextPath("");
                    }
                }
            }
            """,
          """
            import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
            import org.springframework.boot.web.server.WebServerFactoryCustomizer;
            import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;

            public class CustomContainer implements EmbeddedServletContainerCustomizer {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof UndertowServletWebServerFactory) {
                        UndertowServletWebServerFactory undertowContainer = (UndertowServletWebServerFactory) container;
                        undertowContainer.setPort(8080);
                        undertowContainer.setContextPath("");
                    }
                }
            }
            """
        );
    }

}
