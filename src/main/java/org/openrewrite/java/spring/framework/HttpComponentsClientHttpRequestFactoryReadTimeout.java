/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.framework;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HttpComponentsClientHttpRequestFactoryReadTimeout extends Recipe {
    private static final MethodMatcher SET_READ_TIMEOUT_METHOD_MATCHER = new MethodMatcher("org.springframework.http.client.HttpComponentsClientHttpRequestFactory setReadTimeout(..)");
    private static final String POOLING_CONNECTION_MANAGER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager";

    // Known case we do not handle yet
    private static final MethodMatcher SET_DEFAULT_SOCKET_CONFIG = new MethodMatcher(POOLING_CONNECTION_MANAGER + " setDefaultSocketConfig(org.apache.hc.core5.http.io.SocketConfig)");

    @Override
    public String getDisplayName() {
        return "Migrate `setReadTimeout(java.lang.int)` to SocketConfig `setSoTimeout(..)`";
    }

    @Override
    public String getDescription() {
        return "`setReadTimeout(..)` was removed in Spring Framework 6.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_READ_TIMEOUT_METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                        // Extract the argument to `setReadTimeout`
                        AtomicReference<Expression> readTimeout = new AtomicReference<>();
                        AtomicBoolean incompatibilityFound = new AtomicBoolean(false);
                        J.CompilationUnit cuWithComment = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                if (SET_READ_TIMEOUT_METHOD_MATCHER.matches(method)) {
                                    Expression expression = method.getArguments().get(0);
                                    if (expression instanceof J.Literal || expression instanceof J.FieldAccess) {
                                        readTimeout.set(expression);
                                    }

                                    String message = " Manual migration to `SocketConfig.Builder.setSoTimeout(Timeout)` necessary; see: " +
                                                     "https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/http/client/HttpComponentsClientHttpRequestFactory.html#setReadTimeout(int)";
                                    if (method.getComments().stream().noneMatch(c -> c.printComment(getCursor()).contains(message))){
                                        return method.withPrefix(method.getPrefix().withComments(ListUtils.concat(method.getComments(),
                                                new TextComment(false, message, "\n" + method.getPrefix().getIndent(), Markers.EMPTY)
                                        )));
                                    }
                                } else if (SET_DEFAULT_SOCKET_CONFIG.matches(method)) {
                                    incompatibilityFound.set(true);
                                }
                                return super.visitMethodInvocation(method, ctx);
                            }
                        }.visitNonNull(compilationUnit, ctx);

                        //noinspection ConstantValue
                        if (readTimeout.get() == null || incompatibilityFound.get()) {
                            return cuWithComment;
                        }

                        // Attempt to use expression in replacement
                        J.CompilationUnit cu = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                                for (Statement statement : block.getStatements()) {
                                    if (statement instanceof J.VariableDeclarations &&
                                        TypeUtils.isAssignableTo(POOLING_CONNECTION_MANAGER,
                                                ((J.VariableDeclarations) statement).getTypeAsFullyQualified())) {
                                        J.VariableDeclarations varDecl = (J.VariableDeclarations) statement;
                                        maybeAddImport("org.apache.hc.core5.http.io.SocketConfig");
                                        maybeAddImport("java.util.concurrent.TimeUnit");
                                        return JavaTemplate.builder("#{any()}.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(#{any()}, TimeUnit.MILLISECONDS).build());")
                                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5", "httpclient5"))
                                                .imports("java.util.concurrent.TimeUnit", "org.apache.hc.core5.http.io.SocketConfig")
                                                .build().apply(getCursor(), varDecl.getCoordinates().after(),
                                                        varDecl.getVariables().get(0).getName().withPrefix(Space.EMPTY),
                                                        readTimeout.get());
                                    }
                                }
                                return super.visitBlock(block, ctx);
                            }
                        }.visitNonNull(compilationUnit, ctx);

                        if (cu != compilationUnit) {
                            // Clear out the `setReadTimeout` method invocation
                            return super.visitCompilationUnit(cu, ctx);
                        }

                        // No replacement time out could be set; make no change at all to prevent time out being lost
                        return cuWithComment;
                    }

            @Override
            public @Nullable J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (SET_READ_TIMEOUT_METHOD_MATCHER.matches(method)) {
                            //noinspection DataFlowIssue
                            return null;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                });
    }
}
