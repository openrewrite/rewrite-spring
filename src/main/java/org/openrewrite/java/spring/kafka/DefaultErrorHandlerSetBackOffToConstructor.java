/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.kafka;

import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DefaultErrorHandlerSetBackOffToConstructor extends Recipe {

    private static final String DEFAULT_ERROR_HANDLER = "org.springframework.kafka.listener.DefaultErrorHandler";
    private static final MethodMatcher SET_BACK_OFF = new MethodMatcher(DEFAULT_ERROR_HANDLER + " setBackOff(org.springframework.util.backoff.BackOff)");

    @Getter
    final String displayName = "Move `DefaultErrorHandler.setBackOff(BackOff)` to the constructor";

    @Getter
    final String description = "`DefaultErrorHandler` does not have a `setBackOff(BackOff)` method; " +
            "pass the `BackOff` to the constructor instead.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(SET_BACK_OFF), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitBlock(J.Block block, ExecutionContext ctx) {
                J.Block b = (J.Block) super.visitBlock(block, ctx);
                Set<UUID> toRemove = getCursor().getMessage("SET_BACK_OFF_TO_REMOVE");
                if (toRemove == null || toRemove.isEmpty()) {
                    return b;
                }
                return b.withStatements(ListUtils.map(b.getStatements(),
                        s -> toRemove.contains(s.getId()) ? null : s));
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (isNoArgDefaultErrorHandler(newClass)) {
                    Cursor parent = getCursor().getParentTreeCursor();
                    if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                        String variableName = ((J.VariableDeclarations.NamedVariable) parent.getValue()).getSimpleName();
                        Cursor blockCursor = getCursor().dropParentUntil(J.Block.class::isInstance);
                        J.MethodInvocation setBackOff = findSetBackOff((J.Block) blockCursor.getValue(), variableName);
                        if (setBackOff != null) {
                            Set<UUID> toRemove = blockCursor.getMessage("SET_BACK_OFF_TO_REMOVE", new HashSet<>());
                            toRemove.add(setBackOff.getId());
                            blockCursor.putMessage("SET_BACK_OFF_TO_REMOVE", toRemove);
                            return JavaTemplate.builder("new DefaultErrorHandler(#{any(org.springframework.util.backoff.BackOff)})")
                                    .imports(DEFAULT_ERROR_HANDLER)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-kafka-2.9", "spring-core-5"))
                                    .build()
                                    .apply(getCursor(), newClass.getCoordinates().replace(), setBackOff.getArguments().get(0));
                        }
                    }
                }
                return super.visitNewClass(newClass, ctx);
            }

            private boolean isNoArgDefaultErrorHandler(J.NewClass newClass) {
                if (!TypeUtils.isOfClassType(newClass.getType(), DEFAULT_ERROR_HANDLER)) {
                    return false;
                }
                List<Expression> arguments = newClass.getArguments();
                return arguments.isEmpty() || arguments.get(0) instanceof J.Empty;
            }

            private J.MethodInvocation findSetBackOff(J.Block block, String variableName) {
                for (Statement statement : block.getStatements()) {
                    if (statement instanceof J.MethodInvocation) {
                        J.MethodInvocation method = (J.MethodInvocation) statement;
                        if (SET_BACK_OFF.matches(method) &&
                                method.getSelect() instanceof J.Identifier &&
                                variableName.equals(((J.Identifier) method.getSelect()).getSimpleName())) {
                            return method;
                        }
                    }
                }
                return null;
            }
        });
    }
}
