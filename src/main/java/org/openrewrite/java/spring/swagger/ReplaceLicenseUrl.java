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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;

public class ReplaceLicenseUrl extends Recipe {
    private static final MethodMatcher LICENSE_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder license(String)");
    private static final MethodMatcher LICENSEURL_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder licenseUrl(String)");

    @Override
    public String getDisplayName() {
        return "Transform SpringFox `ApiInfo` to Swagger v3 `Info`";
    }

    @Override
    public String getDescription() {
        return "Transforms SpringFox `ApiInfoBuilder` to Swagger v3 `Info` fluent API pattern.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> preconditions = Preconditions.or(new UsesMethod<>(LICENSE_MATCHER), new UsesMethod<>(LICENSEURL_MATCHER));
        return Preconditions.check(preconditions, new JavaVisitor<ExecutionContext>() {
            @Nullable Expression license;
            @Nullable Expression licenseUrl;

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (LICENSE_MATCHER.matches(mi)) {
                    license = mi.getArguments().get(0);
                } else if (LICENSEURL_MATCHER.matches(mi)) {
                    licenseUrl = mi.getArguments().get(0);
                } else {
                    return mi;
                }

                // Combine `license` & `licenseUrl`
                if (license != null && licenseUrl != null) {
                    maybeAddImport("io.swagger.v3.oas.models.info.License");
                    return JavaTemplate.builder("#{any(io.swagger.v3.oas.models.info.Info)}\n.license(new License().name(#{any(String)}).url(#{any(String)}))")
                            .imports("io.swagger.v3.oas.models.info.License")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getSelect(), license, licenseUrl);
                }

                // Remove the method itself already
                return mi.getSelect();
            }
        });
    }
}
