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
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

public class ReplaceLicenseUrl extends Recipe {
    private static final MethodMatcher LICENSE_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder license(String)");
    private static final MethodMatcher LICENSEURL_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder licenseUrl(String)");

    @Override
    public String getDisplayName() {
        return "Replace SpringFox's `license` and `licenseUrl`";
    }

    @Override
    public String getDescription() {
        return "Replace SpringFox's license methods with Swaggers immutable `new License().name(String).url(String)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> preconditions = Preconditions.or(new UsesMethod<>(LICENSE_MATCHER), new UsesMethod<>(LICENSEURL_MATCHER));
        return Preconditions.check(preconditions, new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // Extract and store values or cursor
                Cursor mdCursor = getCursor();
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (LICENSE_MATCHER.matches(mi)) {
                            mdCursor.putMessage("LICENSE", mi.getArguments().get(0));
                        } else if (LICENSEURL_MATCHER.matches(mi)) {
                            mdCursor.putMessage("LICENSE_URL", mi.getArguments().get(0));
                        }
                        return mi;
                    }
                }.visit(method, ctx, getCursor().getParentOrThrow());

                // Now replace any downstream method invocations
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);

                if (LICENSE_MATCHER.matches(mi)) {
                    return replaceLicense(mi, ctx, mi.getArguments().get(0), getCursor().pollNearestMessage("LICENSE_URL"));
                }
                if (LICENSEURL_MATCHER.matches(mi)) {
                    Expression license = getCursor().pollNearestMessage("LICENSE");
                    if (license == null) {
                        return replaceLicense(mi, ctx, null, mi.getArguments().get(0));
                    }
                    // Remove the method itself already
                    return mi.getSelect().withPrefix(mi.getPrefix());
                }
                return mi;
            }

            private J.MethodInvocation replaceLicense(
                    J.MethodInvocation mi,
                    ExecutionContext ctx,
                    @Nullable Expression license,
                    @Nullable Expression licenseUrl) {
                StringBuilder sb = new StringBuilder("#{any(io.swagger.v3.oas.models.info.Info)}\n.license(new License()");
                List<Object> args = new ArrayList<>();
                args.add(mi.getSelect());
                if (license != null) {
                    sb.append(".name(#{any(String)})");
                    args.add(license);
                }
                if (licenseUrl != null) {
                    sb.append(".url(#{any(String)})");
                    args.add(licenseUrl);
                }
                sb.append(')');

                maybeAddImport("io.swagger.v3.oas.models.info.License");
                return JavaTemplate.builder(sb.toString())
                        .imports("io.swagger.v3.oas.models.info.License")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), args.toArray());
            }
        });
    }
}
