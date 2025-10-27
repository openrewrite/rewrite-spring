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
package org.openrewrite.java.spring.swagger;

import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Arrays;
import java.util.List;

public class SecurityContextToSecurityScheme extends Recipe {
    private static final MethodMatcher APIKEY_MATCHER = new MethodMatcher("springfox.documentation.service.ApiKey <constructor>(String, String, String)");
    private static final MethodMatcher AUTHORIZATION_SCOPE_MATCHER = new MethodMatcher("springfox.documentation.service.AuthorizationScope <constructor>(String, String)");

    @Override
    public String getDisplayName() {
        return "Replace elements of SpringFox's security with Swagger's security models.";
    }

    @Override
    public String getDescription() {
        return "Replace ApiKey, AuthorizationScope, and SecurityScheme elements with Swagger's equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                Tree t = tree;
                t = replaceApiKey(ctx, t);
                t = replaceAuthorizationScope(ctx, t);
                return t;
            }

            private Tree replaceApiKey(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(APIKEY_MATCHER), new JavaVisitor<ExecutionContext>() {
                    // Replace `Contact` constructor
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (APIKEY_MATCHER.matches(newClass)) {
                            String inValue = passAsToSecuritySchemeIn(newClass.getArguments().get(2));
                            maybeRemoveImport("springfox.documentation.service.ApiKey");
                            maybeAddImport("io.swagger.v3.oas.models.security.SecurityScheme");
                            return JavaTemplate.builder("new SecurityScheme()\n.type(SecurityScheme.Type.APIKEY)\n.name(#{any(String)})\n.in(" + inValue + ")")
                                    .imports("io.swagger.v3.oas.models.security.SecurityScheme")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(), newClass.getArguments().get(1));
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                    private String passAsToSecuritySchemeIn(Expression passAsExpr) {
                        String passAs = ((J.Literal) passAsExpr).getValueSource();

                        if ("cookie".equalsIgnoreCase(passAs)) {
                            return "SecurityScheme.In.COOKIE";
                        }
                        if ("query".equalsIgnoreCase(passAs)) {
                            return "SecurityScheme.In.QUERY";
                        }
                        return "SecurityScheme.In.HEADER";
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private Tree replaceAuthorizationScope(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(AUTHORIZATION_SCOPE_MATCHER), new JavaVisitor<ExecutionContext>() {
                    // Replace `Contact` constructor
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (AUTHORIZATION_SCOPE_MATCHER.matches(newClass)) {
                            maybeRemoveImport("springfox.documentation.service.AuthorizationScope");
                            maybeAddImport("io.swagger.v3.oas.models.security.Scopes");
                            return JavaTemplate.builder("new Scopes().addString(#{any(String)}, #{any(String)})")
                                    .imports("io.swagger.v3.oas.models.security.Scopes")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(), newClass.getArguments().get(0), newClass.getArguments().get(1));
                        }
                        return super.visitNewClass(newClass, ctx);
                    }

                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }
        };
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new ChangeType(
                        "springfox.documentation.service.ApiKey",
                        "io.swagger.v3.oas.models.security.SecurityScheme",
                        true),
                new ChangeType(
                        "springfox.documentation.service.AuthorizationScope",
                        "io.swagger.v3.oas.models.security.Scopes",
                        true),
                new ChangeType(
                        "springfox.documentation.service.SecurityScheme",
                        "io.swagger.v3.oas.models.security.SecurityScheme",
                        true)
        );
    }
}
