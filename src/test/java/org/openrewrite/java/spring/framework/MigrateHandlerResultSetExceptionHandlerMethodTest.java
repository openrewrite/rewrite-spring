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
package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class MigrateHandlerResultSetExceptionHandlerMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
            .typeValidationOptions(TypeValidation.none())
            .recipe(new MigrateHandlerResultSetExceptionHandlerMethod())
            .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
                "reactor-core-3.6.+", "spring-webflux-6.1.+"));
    }

    @DocumentExample
    @Test
    void migrateHandlerResultSetExceptionHandlerMethodParameterIsInlineLambdaFunction() {
        rewriteRun(
            // language=java
            java(
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            result.setExceptionHandler(ex -> Mono.empty());
                        }
                    }
                    """,
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            result.setExceptionHandler((exchange, ex) -> Mono.empty());
                        }
                    }
                    """
            )
        );
    }

    @Test
    void migrateHandlerResultSetExceptionHandlerMethodParameterIsNonInlineLambdaFunction() {
        rewriteRun(
            // language=java
            java(
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            result.setExceptionHandler(ex -> {
                                // do something
                                return Mono.empty();
                            });
                        }
                    }
                    """,
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            result.setExceptionHandler((exchange, ex) -> {
                                // do something
                                return Mono.empty();
                            });
                        }
                    }
                    """
            )
        );
    }

    @Test
    void migrateHandlerResultSetExceptionHandlerMethodParameterIsIdentifier() {
        rewriteRun(
            // language=java
            java(
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    import java.util.function.Function;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            Function<Throwable, Mono<HandlerResult>> func = (ex -> Mono.empty());
                            result.setExceptionHandler(func);
                        }
                    }
                    """,
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    import java.util.function.Function;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            Function<Throwable, Mono<HandlerResult>> func = (ex -> Mono.empty());
                            result.setExceptionHandler((exchange, ex) -> func.apply(ex));
                        }
                    }
                    """
            )
        );
    }

    @Test
    void migrateHandlerResultSetExceptionHandlerMethodParameterIsDispatchExceptionHandler() {
        rewriteRun(
            // language=java
            java(
                """
                    import org.springframework.web.reactive.HandlerResult;
                    import reactor.core.publisher.Mono;

                    import java.util.function.Function;

                    class MyHandler {
                        void configureHandler(HandlerResult result) {
                            result.setExceptionHandler((exchange, ex) -> {
                                // do something
                                return Mono.empty();
                            });
                        }
                    }
                    """
            )
        );
    }
}
