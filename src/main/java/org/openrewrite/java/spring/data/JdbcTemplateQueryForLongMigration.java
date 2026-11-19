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
package org.openrewrite.java.spring.data;

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

public class JdbcTemplateQueryForLongMigration extends Recipe {

    private static final MethodMatcher QUERY_FOR_LONG_MATCHER = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate queryForLong(..)");
    private static final MethodMatcher QUERY_FOR_LONG_WITH_ARG_TYPES_MATCHER = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate queryForLong(String, Object[], int[])");

    @Getter
    final String displayName = "Convert `JdbcTemplate.queryForLong(..)` to `queryForObject(..)`";

    @Getter
    final String description = "Replaces calls to `JdbcTemplate.queryForLong(..)` with `queryForObject(..)` and a `Long.class` required type. " +
            "`queryForLong` returned `0` for an SQL `NULL` result where `queryForObject` returns `null`, " +
            "so the call is wrapped in `Optional.ofNullable(..).orElse(0L)` to keep that behavior.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(QUERY_FOR_LONG_MATCHER), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                // An unqualified call from a `JdbcTemplate` subclass has no receiver to pass to the template
                if (!QUERY_FOR_LONG_MATCHER.matches(mi) || mi.getSelect() == null) {
                    return mi;
                }

                List<Expression> arguments = mi.getArguments();
                StringBuilder template = new StringBuilder("Optional.ofNullable(#{any(org.springframework.jdbc.core.JdbcTemplate)}.queryForObject(#{any(java.lang.String)}");
                if (QUERY_FOR_LONG_WITH_ARG_TYPES_MATCHER.matches(mi)) {
                    // `queryForObject(String, Class, Object...)` would bind both arrays as bind arguments
                    template.append(", #{anyArray(java.lang.Object)}, #{anyArray(int)}, Long.class");
                } else {
                    template.append(", Long.class");
                    for (int i = 1; i < arguments.size(); i++) {
                        template.append(", #{any()}");
                    }
                }
                template.append(")).orElse(0L)");

                List<Object> parameters = new ArrayList<>(arguments.size() + 1);
                parameters.add(mi.getSelect());
                parameters.addAll(arguments);
                maybeAddImport("java.util.Optional");
                return JavaTemplate.builder(template.toString())
                        .imports("java.util.Optional")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-jdbc-4", "spring-tx-5", "spring-beans-5"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), parameters.toArray());
            }
        });
    }
}
