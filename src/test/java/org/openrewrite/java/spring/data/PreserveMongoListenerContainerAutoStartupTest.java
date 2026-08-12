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
package org.openrewrite.java.spring.data;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class PreserveMongoListenerContainerAutoStartupTest implements RewriteTest {

    private static final String REVIEW_COMMENT =
            "// Unable to verify this DefaultMessageListenerContainer return path; preserve startup behavior manually if required.";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PreserveMongoListenerContainerAutoStartup())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(
            new InMemoryExecutionContext(), "spring-data-mongodb-5.+", "spring-context-6.+"));
    }

    @DocumentExample
    @Test
    void updatesLocallyDeclaredContainer() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                return container;
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(false);
                return container;
            }
            """)
        ));
    }

    @Test
    void updatesEveryDirectReturnPath() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean flag) {
                if (flag) {
                    return new DefaultMessageListenerContainer(template);
                }
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean flag) {
                if (flag) {
                    DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    container.setAutoStartup(false);
                    return container;
                }
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(false);
                return container;
            }
            """)
        ));
    }

    @Test
    void evaluatesSameNamedVariablesIndependently() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean flag) {
                if (flag) {
                    DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    container.setAutoStartup(true);
                    return container;
                }
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                return container;
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean flag) {
                if (flag) {
                    DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    container.setAutoStartup(true);
                    return container;
                }
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(false);
                return container;
            }
            """)
        ));
    }

    @Test
    void preservesExplicitStartupConfiguration() {
        rewriteRun(java(source("""
          @Bean
          DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean autoStartup) {
              DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
              container.setAutoStartup(autoStartup);
              return container;
          }
          """)));
    }

    @Test
    void commentsConditionallyConfiguredReturnPath() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean enabled) {
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                if (enabled) {
                    container.setAutoStartup(true);
                }
                return container;
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean enabled) {
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                if (enabled) {
                    container.setAutoStartup(true);
                }
                %s
                return container;
            }
            """.formatted(REVIEW_COMMENT))
        ));
    }

    @Test
    void configuresContainerReassignedAfterExplicitConfiguration() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(true);
                container = new DefaultMessageListenerContainer(template);
                return container;
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(true);
                container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(false);
                return container;
            }
            """)
        ));
    }

    @Test
    void commentsUnbracedReturnThatCannotBeExpandedIsomorphically() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean enabled) {
                if (enabled)
                    return new DefaultMessageListenerContainer(template);
                throw new IllegalStateException();
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean enabled) {
                if (enabled)
                    %s
                    return new DefaultMessageListenerContainer(template);
                throw new IllegalStateException();
            }
            """.formatted(REVIEW_COMMENT))
        ));
    }

    @Test
    void updatesBeanDeclaredAsListenerContainerInterface() {
        rewriteRun(java(
          """
            package com.example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.data.mongodb.core.MongoTemplate;
            import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
            import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;

            class MongoConfig {
                @Bean
                MessageListenerContainer listenerContainer(MongoTemplate template) {
                    return new DefaultMessageListenerContainer(template);
                }
            }
            """,
          """
            package com.example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.data.mongodb.core.MongoTemplate;
            import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
            import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;

            class MongoConfig {
                @Bean
                MessageListenerContainer listenerContainer(MongoTemplate template) {
                    DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    container.setAutoStartup(false);
                    return container;
                }
            }
            """
        ));
    }

    @Test
    void commentsInterfaceTypedLocalThatCannotCallConcreteSetter() {
        rewriteRun(java(
          """
            package com.example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.data.mongodb.core.MongoTemplate;
            import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
            import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;

            class MongoConfig {
                @Bean
                MessageListenerContainer listenerContainer(MongoTemplate template) {
                    MessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    return container;
                }
            }
            """,
          """
            package com.example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.data.mongodb.core.MongoTemplate;
            import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
            import org.springframework.data.mongodb.core.messaging.MessageListenerContainer;

            class MongoConfig {
                @Bean
                MessageListenerContainer listenerContainer(MongoTemplate template) {
                    MessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    // Unable to verify this DefaultMessageListenerContainer return path; preserve startup behavior manually if required.
                    return container;
                }
            }
            """
        ));
    }

    @Test
    void avoidsVariableNameCollisionWithParameter() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, String container) {
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, String container) {
                DefaultMessageListenerContainer container2 = new DefaultMessageListenerContainer(template);
                container2.setAutoStartup(false);
                return container2;
            }
            """)
        ));
    }

    @Test
    void updatesFieldAssignedDirectlyBeforeReturn() {
        rewriteRun(java(
          sourceWithField("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                container = new DefaultMessageListenerContainer(template);
                return container;
            }
            """),
          sourceWithField("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                container = new DefaultMessageListenerContainer(template);
                container.setAutoStartup(false);
                return container;
            }
            """)
        ));
    }

    @Test
    void commentsFieldWhenProvenanceIsNotLocal() {
        rewriteRun(java(
          sourceWithField("""
            @Bean
            DefaultMessageListenerContainer listenerContainer() {
                return container;
            }
            """),
          sourceWithField("""
            @Bean
            DefaultMessageListenerContainer listenerContainer() {
                %s
                return container;
            }
            """.formatted(REVIEW_COMMENT))
        ));
    }

    @Test
    void handlesOneHopSameClassHelperReturningNewContainer() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = buildContainer(template);
                container.setAutoStartup(false);
                return container;
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """)
        ));
    }

    @Test
    void handlesOneHopSameClassHelperReturningLocalVariable() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                DefaultMessageListenerContainer result = new DefaultMessageListenerContainer(template);
                return result;
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = buildContainer(template);
                container.setAutoStartup(false);
                return container;
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                DefaultMessageListenerContainer result = new DefaultMessageListenerContainer(template);
                return result;
            }
            """)
        ));
    }

    @Test
    void leavesConfiguredHelperResultUnchanged() {
        rewriteRun(java(source("""
          @Bean
          DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
              return buildContainer(template);
          }

          private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
              DefaultMessageListenerContainer result = new DefaultMessageListenerContainer(template);
              result.setAutoStartup(true);
              return result;
          }
          """)));
    }

    @Test
    void commentsDeeperHelperChain() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return createContainer(template);
            }

            private DefaultMessageListenerContainer createContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                %s
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return createContainer(template);
            }

            private DefaultMessageListenerContainer createContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """.formatted(REVIEW_COMMENT))
        ));
    }

    @Test
    void commentsCrossClassHelper() {
        rewriteRun(java(
          """
            package com.example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.data.mongodb.core.MongoTemplate;
            import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

            class MongoConfig {
                private final Factory factory = new Factory();

                @Bean
                DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                    return factory.build(template);
                }
            }

            class Factory {
                DefaultMessageListenerContainer build(MongoTemplate template) {
                    return new DefaultMessageListenerContainer(template);
                }
            }
            """,
          """
            package com.example;

            import org.springframework.context.annotation.Bean;
            import org.springframework.data.mongodb.core.MongoTemplate;
            import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

            class MongoConfig {
                private final Factory factory = new Factory();

                @Bean
                DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                    // Unable to verify this DefaultMessageListenerContainer return path; preserve startup behavior manually if required.
                    return factory.build(template);
                }
            }

            class Factory {
                DefaultMessageListenerContainer build(MongoTemplate template) {
                    return new DefaultMessageListenerContainer(template);
                }
            }
            """
        ));
    }

    @Test
    void transformsSupportedPathAndCommentsUnsupportedPath() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean flag) {
                if (flag) {
                    return new DefaultMessageListenerContainer(template);
                }
                return external(template);
            }

            private DefaultMessageListenerContainer external(MongoTemplate template) {
                return other(template);
            }

            private DefaultMessageListenerContainer other(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template, boolean flag) {
                if (flag) {
                    DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
                    container.setAutoStartup(false);
                    return container;
                }
                %s
                return external(template);
            }

            private DefaultMessageListenerContainer external(MongoTemplate template) {
                return other(template);
            }

            private DefaultMessageListenerContainer other(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """.formatted(REVIEW_COMMENT))
        ));
    }

    @Test
    void configuresEachBeanWhenTheyShareAHelper() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer first(MongoTemplate template) {
                return buildContainer(template);
            }

            @Bean
            DefaultMessageListenerContainer second(MongoTemplate template) {
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer first(MongoTemplate template) {
                DefaultMessageListenerContainer container = buildContainer(template);
                container.setAutoStartup(false);
                return container;
            }

            @Bean
            DefaultMessageListenerContainer second(MongoTemplate template) {
                DefaultMessageListenerContainer container = buildContainer(template);
                container.setAutoStartup(false);
                return container;
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """)
        ));
    }

    @Test
    void leavesNonBeanCallerUnchangedWhenItSharesAHelperWithBean() {
        rewriteRun(java(
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                return buildContainer(template);
            }

            DefaultMessageListenerContainer createForManualUse(MongoTemplate template) {
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """),
          source("""
            @Bean
            DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
                DefaultMessageListenerContainer container = buildContainer(template);
                container.setAutoStartup(false);
                return container;
            }

            DefaultMessageListenerContainer createForManualUse(MongoTemplate template) {
                return buildContainer(template);
            }

            private DefaultMessageListenerContainer buildContainer(MongoTemplate template) {
                return new DefaultMessageListenerContainer(template);
            }
            """)
        ));
    }

    @Test
    void ignoresNonBeanUsage() {
        rewriteRun(java("""
          package com.example;

          import org.springframework.data.mongodb.core.MongoTemplate;
          import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

          class LocalUsage {
              DefaultMessageListenerContainer create(MongoTemplate template) {
                  return new DefaultMessageListenerContainer(template);
              }
          }
          """));
    }

    @Test
    void ignoresUnrelatedBeanInCompilationUnitUsingListenerContainer() {
        rewriteRun(java("""
          package com.example;

          import org.springframework.context.annotation.Bean;
          import org.springframework.data.mongodb.core.MongoTemplate;
          import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

          class MongoConfig {
              @Bean
              String greeting() {
                  return "hello";
              }

              DefaultMessageListenerContainer createForManualUse(MongoTemplate template) {
                  return new DefaultMessageListenerContainer(template);
              }
          }
          """));
    }

    @Test
    void isIdempotent() {
        rewriteRun(java(source("""
          @Bean
          DefaultMessageListenerContainer listenerContainer(MongoTemplate template) {
              DefaultMessageListenerContainer container = new DefaultMessageListenerContainer(template);
              container.setAutoStartup(false);
              return container;
          }
          """)));
    }

    @Test
    void diagnosticIsIdempotent() {
        rewriteRun(java(sourceWithField("""
          @Bean
          DefaultMessageListenerContainer listenerContainer() {
              %s
              return container;
          }
          """.formatted(REVIEW_COMMENT))));
    }

    private static String source(String body) {
        return """
          package com.example;

          import org.springframework.context.annotation.Bean;
          import org.springframework.data.mongodb.core.MongoTemplate;
          import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

          class MongoConfig {
          %s
          }
          """.formatted(indent(body, 4));
    }

    private static String sourceWithField(String body) {
        return """
          package com.example;

          import org.springframework.context.annotation.Bean;
          import org.springframework.data.mongodb.core.MongoTemplate;
          import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;

          class MongoConfig {
              private DefaultMessageListenerContainer container;

          %s
          }
          """.formatted(indent(body, 4));
    }

    private static String indent(String value, int spaces) {
        return value.indent(spaces).stripTrailing();
    }
}
