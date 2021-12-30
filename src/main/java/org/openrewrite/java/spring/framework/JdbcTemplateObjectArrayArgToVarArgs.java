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
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

public class JdbcTemplateObjectArrayArgToVarArgs extends Recipe {
    @Override
    public String getDisplayName() {
        return "Use varargs equivalents for deprecated JdbcTemplate signatures";
    }

    @Override
    public String getDescription() {
        return "`JdbcTemplate` signatures with `Object[]` arguments are deprecated, in favor of their existing varargs equivalents.";
    }

    @Override
    protected UsesType<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.jdbc.core.JdbcTemplate");
    }

    @Override
    protected JdbcTemplateArgsVisitor getVisitor() {
        return new JdbcTemplateArgsVisitor();
    }

    private static class JdbcTemplateArgsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher queryMapper = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate query(..)");
        private static final MethodMatcher queryForObjectMapper = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate queryForObject(..)");
        private static final MethodMatcher queryForListMapper = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate queryForList(..)");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, executionContext);
            if (queryMapper.matches(mi) || queryForObjectMapper.matches(mi) || queryForListMapper.matches(mi)) {
                List<Expression> args = mi.getArguments();
                if (args.size() == 3 && shouldSwapArgs(args.get(1).getType(), args.get(2).getType())) {
                    List<Expression> reOrderedArgs = new ArrayList<>(3);
                    reOrderedArgs.add(args.get(0));
                    reOrderedArgs.add(args.get(2).withPrefix(args.get(1).getPrefix()));
                    reOrderedArgs.add(args.get(1).withPrefix(args.get(2).getPrefix()));
                    mi = mi.withArguments(reOrderedArgs);
                }
            }
            return mi;
        }

        private boolean shouldSwapArgs(@Nullable JavaType arg1, @Nullable JavaType arg2) {
            return arg1 instanceof JavaType.Array && (
                    (arg2 instanceof JavaType.Parameterized && ((JavaType.Parameterized)arg2).getTypeParameters().get(0) instanceof JavaType.Class)
                            || TypeUtils.isOfClassType(arg2, "org.springframework.jdbc.core.RowMapper")
                            || TypeUtils.isOfClassType(arg2, "org.springframework.jdbc.core.ResultSetExtractor")
                            || TypeUtils.isOfClassType(arg2, "org.springframework.jdbc.core.RowCallbackHandler")

            );
        }
    }
}
