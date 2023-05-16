/*
 * Copyright 2021 the original author or authors.
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
import org.openrewrite.java.tree.J;

import java.util.stream.Collectors;

public class EnvironmentAcceptsProfiles extends Recipe {
    private static final MethodMatcher MATCHER = new MethodMatcher("org.springframework.core.env.Environment acceptsProfiles(java.lang.String...)");

    @Override
    public String getDisplayName() {
        return "Use `Environment#acceptsProfiles(Profiles)`";
    }

    @Override
    public String getDescription() {
        return "`Environment#acceptsProfiles(String...)` was deprecated in Spring Framework 5.1.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(MATCHER), new EnvironmentAcceptsProfilesVisitor());
    }

    private static class EnvironmentAcceptsProfilesVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (MATCHER.matches(method)) {
                maybeAddImport("org.springframework.core.env.Profiles");
                String template = "Profiles.of(" + method.getArguments().stream().map(a -> "#{any(java.lang.String)}").collect(Collectors.joining(",")) + ")";
                method = method.withTemplate(JavaTemplate.builder(template)
                                .imports("org.springframework.core.env.Profiles", "org.springframework.core.env.Environment")
                                .javaParser(JavaParser.fromJavaVersion()
                                        .classpathFromResources(ctx, "spring-core-5.*"))
                                .build(),
                        getCursor(),
                        method.getCoordinates().replaceArguments(),
                        method.getArguments().toArray()
                );
            }
            return super.visitMethodInvocation(method, ctx);
        }
    }
}
