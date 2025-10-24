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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class ReplaceApiKey extends Recipe {
    private static final MethodMatcher APIKEY_MATCHER = new MethodMatcher("springfox.documentation.service.ApiKey <constructor>(String, String, String)");

    @Override
    public String getDisplayName() {
        return "Replace SpringFox's `ApiKey` with Swagger's `SecurityScheme`";
    }

    @Override
    public String getDescription() {
        return "Replace three argument constructor with immutable builder.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
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
        });
    }

}
