/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

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

public class MigrateMultipartConfigFactory extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `MultipartConfigFactory` with `DataSize` arguments";
    }

    @Override
    public String getDescription() {
        return "Methods to set `DataSize` with primitive arguments were deprecated in 2.1 and removed in 2.2.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.boot.web.servlet.MultipartConfigFactory", true),
                new JavaIsoVisitor<ExecutionContext>() {
                    final MethodMatcher setMaxFileSizeByLong = new MethodMatcher("org.springframework.boot.web.servlet.MultipartConfigFactory setMaxFileSize(long)");
                    final MethodMatcher setMaxRequestSizeByLong = new MethodMatcher("org.springframework.boot.web.servlet.MultipartConfigFactory setMaxRequestSize(long)");
                    final MethodMatcher setFileSizeThresholdByInt = new MethodMatcher("org.springframework.boot.web.servlet.MultipartConfigFactory setFileSizeThreshold(int)");

                    final MethodMatcher setMaxFileSizeByString = new MethodMatcher("org.springframework.boot.web.servlet.MultipartConfigFactory setMaxFileSize(java.lang.String)");
                    final MethodMatcher setMaxRequestSizeByString = new MethodMatcher("org.springframework.boot.web.servlet.MultipartConfigFactory setMaxRequestSize(java.lang.String)");
                    final MethodMatcher setFileSizeThresholdByString = new MethodMatcher("org.springframework.boot.web.servlet.MultipartConfigFactory setFileSizeThreshold(java.lang.String)");

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                        if (setMaxFileSizeByLong.matches(m) || setMaxRequestSizeByLong.matches(m) || setFileSizeThresholdByInt.matches(m)) {
                            m = JavaTemplate.builder("DataSize.ofBytes(#{any()})")
                                            .imports("org.springframework.util.unit.DataSize")
                                            .javaParser(JavaParser.fromJavaVersion()
                                                    .classpathFromResources(ctx, "spring-core-5.*", "spring-boot-2.*"))
                                            .build().apply(
                                    getCursor(),
                                    m.getCoordinates().replaceArguments(),
                                    m.getArguments().get(0));
                        } else if (setMaxFileSizeByString.matches(m) || setMaxRequestSizeByString.matches(m) || setFileSizeThresholdByString.matches(m)) {
                            m = JavaTemplate
                                .builder("DataSize.parse(#{any(java.lang.String)})")
                                .imports("org.springframework.util.unit.DataSize")
                                .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-core-5.*", "spring-boot-2.*"))
                                .build().apply(
                                    getCursor(),
                                    m.getCoordinates().replaceArguments(),
                                    m.getArguments().get(0));
                        }
                        maybeAddImport("org.springframework.util.unit.DataSize");
                        return m;
                    }
                });
    }
}
