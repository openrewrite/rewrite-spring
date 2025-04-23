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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdbcTemplateQueryForLongMigration extends Recipe {

    private static final MethodMatcher QUERY_FOR_LONG_MATCHER = new MethodMatcher("org.springframework.jdbc.core.JdbcTemplate queryForLong(..)");

    @Override
    public String getDisplayName() {
        return "Convert `JdbcTemplate.queryForLong(..)` to `queryForObject(..)`";
    }

    @Override
    public String getDescription() {
        return "Replaces calls to `JdbcTemplate.queryForLong(..)` with `queryForObject(String, Class, Object...)`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (QUERY_FOR_LONG_MATCHER.matches(mi)) {
                    JavaType.Method oldMethodType = mi.getMethodType();
                    List<String> newParameterNames = new ArrayList<>(oldMethodType.getParameterNames());
                    newParameterNames.add(1, "requiredType");
                    List<JavaType> newParameterTypes = new ArrayList<>(oldMethodType.getParameterTypes());
                    newParameterTypes.add(1, JavaType.ShallowClass.build("java.lang.Class"));
                    List<Expression> newArguments = new ArrayList<>(mi.getArguments());
                    newArguments.add(1, longDotClass());
                    JavaType.Method newMethodType = oldMethodType.withName("queryForObject")
                            .withParameterNames(newParameterNames)
                            .withParameterTypes(newParameterTypes);
                    return mi.withName(mi.getName().withSimpleName("queryForObject").withType(newMethodType))
                            .withMethodType(newMethodType)
                            .withArguments(newArguments);
                }
                return mi;
            }

            private J.FieldAccess longDotClass() {
                return new J.FieldAccess(
                        Tree.randomId(),
                        Space.SINGLE_SPACE,
                        Markers.EMPTY,
                        new J.Identifier(
                                Tree.randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                Collections.emptyList(),
                                "Long",
                                JavaType.ShallowClass.build("java.lang.Long"),
                                null
                        ),
                        new JLeftPadded<>(
                                Space.EMPTY,
                                new J.Identifier(
                                        Tree.randomId(),
                                        Space.EMPTY,
                                        Markers.EMPTY,
                                        Collections.emptyList(),
                                        "class",
                                        JavaType.ShallowClass.build("java.lang.Class"),
                                        null),
                                Markers.EMPTY
                        ),
                        null
                );
            }
        };
        return Preconditions.check(new UsesMethod<>(QUERY_FOR_LONG_MATCHER), visitor);
    }
}
