/*
 * Copyright (c) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class JdbcTemplateQueryForLongMigration extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert JdbcTemplate.queryForLong() to queryForObject()";
    }

    @Override
    public String getDescription() {
        return "- [Spring Framework Migration] Replaces calls to JdbcTemplate.queryForLong() with queryForObject(String, Class, Object...).";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final MethodMatcher queryForLongMatcher = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate queryForLong(..)");

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (queryForLongMatcher.matches(mi)) {
                    maybeAddImport("java.lang.Long");
                    return JavaTemplate.builder("queryForObject(#{any()}, Long.class" +
                                                (mi.getArguments().size() > 1 ? ", #{rest()}" : "") + ")")
                            .contextSensitive()
                            .build()
                            .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0));
                }
                return mi;
            }
        };
    }
}
