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

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

@NoArgsConstructor
@AllArgsConstructor
public class MigrateHandlerResultSetExceptionHandlerMethod extends Recipe {

    private static final String HandlerResult = "org.springframework.web.reactive.HandlerResult";

    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher(HandlerResult + " setExceptionHandler(java.util.function.Function)");

    private String exceptionHandlerFieldName = "exceptionHandler";

    @Override
    public String getDisplayName() {
        return "Migrate `org.springframework.web.reactive.HandlerResult.setExceptionHandler` method";
    }

    @Override
    public String getDescription() {
        return "`org.springframework.web.reactive.HandlerResult.setExceptionHandler(Function<Throwable, Mono<HandlerResult>>)` was deprecated, in favor of `setExceptionHandler(DispatchExceptionHandler)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(HandlerResult, false), new JavaIsoVisitor<ExecutionContext>() {

                @Override
                public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                    J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                    if (md.getBody() == null) {
                        return md;
                    }
                    for (Statement statement : md.getBody().getStatements()) {
                        if (statement instanceof J.MethodInvocation) {
                            J.MethodInvocation m = (J.MethodInvocation) statement;
                            if (METHOD_MATCHER.matches(m)) {
                                if (m.getArguments().get(0) instanceof J.Identifier) {
                                    md = JavaTemplate.builder("(exchange, ex) ->  #{any()}.apply(ex)")
                                        .build()
                                        .apply(getCursor(), m.getCoordinates().replaceArguments(), m.getArguments().get(0));
                                } else {
                                    md = JavaTemplate.builder("Function<Throwable, Mono<HandlerResult>> " + exceptionHandlerFieldName + " = (#{any(java.util.function.Function)})")
                                        .contextSensitive()
                                        .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "reactor-core", "spring-webflux-6.1.+"))
                                        .imports("reactor.core.publisher.Mono", "java.util.function.Function")
                                        .build()
                                        .apply(getCursor(), m.getCoordinates().before(), m.getArguments().get(0));
                                    maybeAddImport("reactor.core.publisher.Mono");
                                    maybeAddImport("java.util.function.Function");

                                    md = JavaTemplate.builder("(exchange, ex) -> " + exceptionHandlerFieldName + ".apply(ex)")
                                        .build()
                                        .apply(new Cursor(getCursor(), md), m.getCoordinates().replaceArguments());
                                }
                            }
                        }
                    }
                    return md;
                }
            }
        );
    }

}
