package org.openrewrite.java.spring;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.openrewrite.*;
import org.openrewrite.Cursor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoveMethodInvocations extends Recipe {
    private final List<String> methodSignatures;

    @JsonCreator
    public RemoveMethodInvocations(List<String> methodSignatures) {
        this.methodSignatures = methodSignatures;
    }

    @Override
    public String getDisplayName() {
        return "Remove the specified method calls if it can be deleted";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "This method can be used to remove deprecated or unnecessary method calls, but be sure to carefully " +
               "review your code before deleting any methods to avoid errors or unexpected behavior.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(8);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            private List<MethodMatcher> matchers;
            {
                matchers = new ArrayList<>(methodSignatures.size());
                for (String signature : methodSignatures) {
                    matchers.add(new MethodMatcher(signature));
                }
            }


            @Override
            public J visitMethodInvocation(J.MethodInvocation method,
                                                            ExecutionContext executionContext) {
                Expression expression = removeMethods(method, 0, isStatement(), new Stack<>());
                if (expression != null) {
                    expression = expression.withPrefix(method.getPrefix());

                }
                return expression;
            }

            @Nullable
            private Expression removeMethods(Expression expression, int depth, boolean isStatement, Stack<Space> prefix) {
                if (!(expression instanceof J.MethodInvocation)) {
                    return expression;
                }

                J.MethodInvocation m = (J.MethodInvocation) expression;

                if (matchers.stream().anyMatch(matcher -> matcher.matches(m))) {

                    boolean removable = (isStatement && depth == 0) ||
                                        TypeUtils.isAssignableTo(m.getMethodType().getReturnType(), m.getSelect().getType());
                    if (!removable) {
                        return expression;
                    }

                    if (m.getSelect() instanceof J.Identifier) {
                        boolean keepSelect = depth != 0;
                        if (keepSelect) {
                            // followedMethod.withSelect()
                            prefix.add(m.getPrefix());
                            return m.getSelect();
                        } else {
                            return isStatement ? null : expression;
                        }

                    } else if (m.getSelect() instanceof J.MethodInvocation) {
                        // remove this method
                        return removeMethods(m.getSelect(), depth, isStatement, prefix);
                    }
                }

                J.MethodInvocation method = m.withSelect(removeMethods(m.getSelect(), depth + 1, isStatement, prefix));

                // inherit prefix
                if (!prefix.isEmpty()) {
                    method = inheritFirstPrefix(method, prefix);
                }

                return method;
            }

            private boolean isStatement() {
                return getCursor().dropParentUntil(p -> p instanceof J.Block ||
                                                 p instanceof J.Assignment ||
                                                 p instanceof J.VariableDeclarations.NamedVariable || p instanceof J.Return ||
                                                 p instanceof JContainer ||
                                                 p instanceof J.CompilationUnit
                ).getValue() instanceof J.Block;
            }

            private J.MethodInvocation inheritFirstPrefix(J.MethodInvocation method, Stack<Space> prefix) {
                return (J.MethodInvocation) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public <T> JRightPadded<T> visitRightPadded(@Nullable JRightPadded<T> right,
                                                                JRightPadded.Location loc,
                                                                ExecutionContext executionContext) {
                        return prefix.isEmpty() ? right : right.withAfter(prefix.pop());
                    }
                }.visit(method, new InMemoryExecutionContext());
            }
        };
    }
}
