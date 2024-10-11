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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateResponseStatusExceptionGetStatusCodeMethod extends Recipe {
    private static final MethodMatcher GET_STATUS_METHOD_MATCHER = new MethodMatcher(
            "org.springframework.web.server.ResponseStatusException getStatus()"
    );
    private static final String HTTP_STATUS_CODE_CLASS = "HttpStatusCode";
    private static final String FULL_HTTP_STATUS_CLASS = "org.springframework.http.HttpStatus";
    private static final String FULL_HTTP_STATUS_CODE_CLASS = "org.springframework.http.HttpStatusCode";

    @Override
    public String getDisplayName() {
        return "Migrate `ResponseStatusException#getStatus()` to `getStatusCode()`";
    }

    @Override
    public String getDescription() {
        return "Migrate Spring Framework 5.3's `ResponseStatusException` method `getStatus()` to Spring Framework 6's `getStatusCode()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(GET_STATUS_METHOD_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (GET_STATUS_METHOD_MATCHER.matches(mi)) {
                    return JavaTemplate.builder("#{any()}.getStatusCode()")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-core-6", "spring-beans-6", "spring-web-6"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect());
                }
                return mi;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);
                if (vd.getTypeExpression() != null && TypeUtils.isOfClassType(vd.getTypeExpression().getType(), FULL_HTTP_STATUS_CLASS)) {
                    JavaType.ShallowClass httpStatusCodeJavaType = JavaType.ShallowClass.build(FULL_HTTP_STATUS_CODE_CLASS);
                    vd = vd
                            .withTypeExpression(TypeTree.build(HTTP_STATUS_CODE_CLASS).withType(httpStatusCodeJavaType))
                            .withVariables(ListUtils.map(vd.getVariables(), variable -> {
                                        if (variable.getVariableType() != null && TypeUtils.isAssignableTo(FULL_HTTP_STATUS_CLASS, variable.getType())) {
                                            return variable
                                                    .withVariableType(variable.getVariableType().withType(httpStatusCodeJavaType))
                                                    .withName(variable.getName().withType(httpStatusCodeJavaType).withFieldType(variable.getName().getFieldType().withType(httpStatusCodeJavaType)));
                                        }
                                        return variable;
                                    })
                            );
                    maybeAddImport(FULL_HTTP_STATUS_CODE_CLASS);
                    maybeRemoveImport(FULL_HTTP_STATUS_CLASS);
                }
                return vd;
            }
        });
    }
}
