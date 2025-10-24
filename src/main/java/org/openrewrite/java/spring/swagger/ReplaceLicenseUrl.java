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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.UUID;

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

                Expression license;
                Expression licenseUrl;
                if (LICENSE_MATCHER.matches(mi)) {
                    license = mi.getArguments().get(0);
                    licenseUrl = getCursor().pollNearestMessage("LICENSE_URL");
                    if (licenseUrl == null) {
                        return replaceLicense(mi, ctx, nameOnyTemplate(), mi.getSelect(), license);
                    }
                    // Combine license and url
                    return replaceLicense(mi, ctx, fullTemplate(), mi.getSelect(), license, licenseUrl);
                }
                if (LICENSEURL_MATCHER.matches(mi)) {
                    license = getCursor().pollNearestMessage("LICENSE");
                    licenseUrl = mi.getArguments().get(0);
                    if (license == null) {
                        return replaceLicense(mi, ctx, urlOnlyTemplate(), mi.getSelect(), licenseUrl);
                    }
                    // Remove the method itself already
                    return mi.getSelect().withPrefix(mi.getPrefix());
                }
                return mi;
            }

            private String fullTemplate() {
                return makeTemplate(true, true);
            }

            private String nameOnyTemplate() {
                return makeTemplate(true, false);
            }

            private String urlOnlyTemplate() {
                return makeTemplate(false, true);
            }

            private String makeTemplate(boolean withName, boolean withUrl) {
                StringBuilder sb = new StringBuilder("#{any(io.swagger.v3.oas.models.info.Info)}\n.license(new License()");
                if (withName) {
                    sb.append(".name(#{any(String)})");
                }
                if (withUrl) {
                    sb.append(".url(#{any(String)})");
                }
                sb.append(')');
                return sb.toString();
            }

            private J.MethodInvocation replaceLicense(J.MethodInvocation mi, ExecutionContext ctx, String template, Object... args) {
                maybeAddImport("io.swagger.v3.oas.models.info.License");
                return JavaTemplate.builder(template)
                        .imports("io.swagger.v3.oas.models.info.License")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), args);
            }
        });
    }
}
