/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.kafka;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;

public class KafkaTestUtilsDuration extends Recipe {

    private static final String JAVA_TIME_DURATION = "java.time.Duration";
    private static final MethodMatcher LAST_ARGUMENT_MATCHER = new MethodMatcher("org.springframework.kafka.test.utils.KafkaTestUtils get*Record*(.., long)");
    private static final MethodMatcher SECOND_ARGUMENT_MATCHER = new MethodMatcher("org.springframework.kafka.test.utils.KafkaTestUtils getRecords(.., long, int)");

    @Override
    public String getDisplayName() {
        return "Use `Duration` in `KafkaTestUtils`";
    }

    @Override
    public String getDescription() {
        return "Replace `KafkaTestUtils` methods that take a `long` argument with methods that take a `Duration`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(LAST_ARGUMENT_MATCHER),
                        new UsesMethod<>(SECOND_ARGUMENT_MATCHER)
                ),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                        List<Expression> originalArguments = mi.getArguments();
                        final Expression millis;
                        if (LAST_ARGUMENT_MATCHER.matches(mi)) {
                            millis = originalArguments.get(originalArguments.size() - 1);
                        } else if (SECOND_ARGUMENT_MATCHER.matches(mi)) {
                            millis = originalArguments.get(1);
                        } else {
                            return mi;
                        }

                        JavaType durationType = JavaType.buildType(JAVA_TIME_DURATION);
                        List<String> newParameterNames = new ArrayList<>(mi.getMethodType().getParameterNames());
                        List<JavaType> newParameterTypes = new ArrayList<>(mi.getMethodType().getParameterTypes());
                        List<Expression> newArguments = ListUtils.map(originalArguments, (index, arg) -> {
                            if (arg == millis) {
                                newParameterNames.set(index, "duration");
                                newParameterTypes.set(index, durationType);
                                maybeAddImport(JAVA_TIME_DURATION);
                                return JavaTemplate.builder("Duration.ofMillis(#{})")
                                        .imports(JAVA_TIME_DURATION)
                                        .build()
                                        .apply(new Cursor(getCursor(), arg), arg.getCoordinates().replace(), millis);
                            }
                            return arg;
                        });

                        return mi.withArguments(newArguments)
                                .withMethodType(mi.getMethodType()
                                        .withParameterNames(newParameterNames)
                                        .withParameterTypes(newParameterTypes));
                    }
                }
        );
    }
}
