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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

public class HttpComponentsClientHttpRequestFactoryReadTimeout extends Recipe {
    private static final MethodMatcher METHOD_MATCHER = new MethodMatcher("org.springframework.http.client.HttpComponentsClientHttpRequestFactory setReadTimeout(..)");
    private static final String POOLING_HTTP_CLIENT_CONNECTION_MANAGER = "org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager";

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
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(METHOD_MATCHER),
                        new UsesType<>(POOLING_HTTP_CLIENT_CONNECTION_MANAGER, false)
                ), new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (METHOD_MATCHER.matches(method)) {
                            Expression expression = method.getArguments().get(0);
                            doAfterVisit(
                                    Preconditions.check(
                                            // Perhaps this should not be a precondition but a check, if this already gets called we place a comment
                                            // to inform the user we've removed the `HttpComponentsClientHttpRequestFactory.setReadTimeout`
                                            Preconditions.not(new UsesMethod<>(POOLING_HTTP_CLIENT_CONNECTION_MANAGER + " setDefaultSocketConfig(..)")
                                            ), new JavaIsoVisitor<ExecutionContext>() {
                                                @Override
                                                public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
                                                    for (Statement statement : block.getStatements()) {
                                                        if (statement instanceof J.VariableDeclarations &&
                                                            TypeUtils.isAssignableTo(POOLING_HTTP_CLIENT_CONNECTION_MANAGER,
                                                                    ((J.VariableDeclarations) statement).getTypeAsFullyQualified())) {
                                                            J.VariableDeclarations varDecl = (J.VariableDeclarations) statement;
                                                            maybeAddImport("org.apache.hc.core5.http.io.SocketConfig");
                                                            maybeAddImport("java.util.concurrent.TimeUnit");
                                                            return JavaTemplate.builder("#{any()}.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(#{any()}, TimeUnit.MILLISECONDS).build());")
                                                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "httpcore5", "httpclient5"))
                                                                    .imports("java.util.concurrent.TimeUnit", "org.apache.hc.core5.http.io.SocketConfig")
                                                                    .build().apply(getCursor(), varDecl.getCoordinates().after(),
                                                                            varDecl.getVariables().get(0).getName().withPrefix(Space.EMPTY),
                                                                            expression);
                                                        }
                                                    }
                                                    return super.visitBlock(block, ctx);
                                                }
                                            }));
                            //noinspection DataFlowIssue
                            return null;
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                });
    }
}
