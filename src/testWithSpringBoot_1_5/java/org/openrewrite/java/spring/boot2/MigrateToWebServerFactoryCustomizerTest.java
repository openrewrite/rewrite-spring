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
package org.openrewrite.java.spring.boot2;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateToWebServerFactoryCustomizerTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath("spring-boot", "spring-context", "spring-beans").logCompilationWarningsAndErrors(true))
          .recipe(Environment.builder()
            .scanRuntimeClasspath()
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot2.MigrateToWebServerFactoryCustomizer")
          );
    }

    @Test
    void migrateToWebServerFactoryCustomizer() {
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
