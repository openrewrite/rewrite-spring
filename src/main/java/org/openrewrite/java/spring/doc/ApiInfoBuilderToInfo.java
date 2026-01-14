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
package org.openrewrite.java.spring.doc;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiInfoBuilderToInfo extends Recipe {
    private static final MethodMatcher BUILD_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder build()");
    private static final MethodMatcher CONTACT_MATCHER = new MethodMatcher("springfox.documentation.service.Contact <constructor>(String, String, String)");
    private static final MethodMatcher LICENSE_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder license(String)");
    private static final MethodMatcher LICENSEURL_MATCHER = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder licenseUrl(String)");

    @Getter
    final String displayName = "Migrate `ApiInfoBuilder` to `Info`";

    @Getter
    final String description = "Migrate SpringFox's `ApiInfoBuilder` to Swagger's `Info`.";


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext ctx) {
                stopAfterPreVisit();

                Tree t = tree;
                t = replaceContact(ctx, t);
                t = replaceLicense(ctx, t);
                return removeBuild(ctx, t);
            }

            private Tree removeBuild(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(BUILD_MATCHER), new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        if (BUILD_MATCHER.matches(method)) {
                            return autoFormat(method.getSelect().withPrefix(method.getPrefix()), ctx);
                        }
                        return super.visitMethodInvocation(method, ctx);
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private Tree replaceContact(ExecutionContext ctx, Tree t) {
                return Preconditions.check(new UsesMethod<>(CONTACT_MATCHER), new JavaVisitor<ExecutionContext>() {
                    // Replace `Contact` constructor
                    @Override
                    public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                        if (CONTACT_MATCHER.matches(newClass)) {
                            maybeRemoveImport("springfox.documentation.service.Contact");
                            maybeAddImport("io.swagger.v3.oas.models.info.Contact");
                            return JavaTemplate.builder("new Contact().name(#{any(String)}).url(#{any(String)}).email(#{any(String)})")
                                    .imports("io.swagger.v3.oas.models.info.Contact")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "swagger-models"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(), newClass.getArguments().toArray());
                        }
                        return super.visitNewClass(newClass, ctx);
                    }
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }

            private Tree replaceLicense(ExecutionContext ctx, Tree t) {
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
                }).visitNonNull(t, ctx, getCursor().getParentOrThrow());
            }
        };
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Arrays.asList(
                new ChangeMethodName(
                        "springfox.documentation.builders.ApiInfoBuilder termsOfServiceUrl(String)",
                        "termsOfService",
                        true,
                        true),
                new ChangeType(
                        "springfox.documentation.service.ApiInfo",
                        "io.swagger.v3.oas.models.info.Info",
                        true),
                new ChangeType(
                        "springfox.documentation.builders.ApiInfoBuilder",
                        "io.swagger.v3.oas.models.info.Info",
                        true)
        );
    }
}
