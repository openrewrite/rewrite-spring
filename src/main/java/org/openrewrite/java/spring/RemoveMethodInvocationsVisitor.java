package org.openrewrite.java.spring;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * This visitor can remove the specified method calls if it can be deleted without compile error,
 * It can be used to remove deprecated or unnecessary method calls, but be sure to carefully
 * review your code before deleting any methods to avoid errors or unexpected behavior.
 */
public class RemoveMethodInvocationsVisitor extends JavaVisitor<ExecutionContext> {
    private final List<MethodMatcher> matchers;

    @JsonCreator
    public RemoveMethodInvocationsVisitor(List<String> methodSignatures) {
        matchers = new ArrayList<>(methodSignatures.size());
        for (String signature : methodSignatures) {
            matchers.add(new MethodMatcher(signature));
        }
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method,
                                   ExecutionContext executionContext) {
        if (inMethodCallChain()) {
            return method;
        }

        method = (J.MethodInvocation) super.visitMethodInvocation(method, executionContext);

        Expression expression = removeMethods(method, 0, isStatement(), new Stack<>());
        if (expression != null) {
            expression = expression.withPrefix(method.getPrefix());
        }
        return expression;
    }

    @Nullable
    private Expression removeMethods(Expression expression, int depth, boolean isStatement, Stack<Space> selectAfter) {
        if (!(expression instanceof J.MethodInvocation)) {
            return expression;
        }

        J.MethodInvocation m = (J.MethodInvocation) expression;

        if (matchers.stream().anyMatch(matcher -> matcher.matches(m))) {
            boolean hasSameReturnType = TypeUtils.isAssignableTo(m.getMethodType().getReturnType(), m.getSelect().getType());
            boolean removable = (isStatement && depth == 0) || hasSameReturnType;
            if (!removable) {
                return expression;
            }

            if (m.getSelect() instanceof J.Identifier || m.getSelect() instanceof J.NewClass) {
                boolean keepSelect = depth != 0;
                if (keepSelect) {
                    selectAfter.add(getSelectAfter(m));
                    return m.getSelect();
                } else {
                    return isStatement ? null :  hasSameReturnType ? m.getSelect() : expression;
                }
            } else if (m.getSelect() instanceof J.MethodInvocation) {
                return removeMethods(m.getSelect(), depth, isStatement, selectAfter);
            }
        }

        J.MethodInvocation method = m.withSelect(removeMethods(m.getSelect(), depth + 1, isStatement, selectAfter));

        // inherit prefix
        if (!selectAfter.isEmpty()) {
            method = inheritSelectAfter(method, selectAfter);
        }

        return method;
    }

    private boolean isStatement() {
        return getCursor().dropParentUntil(p -> p instanceof J.Block ||
                                                p instanceof J.Assignment ||
                                                p instanceof J.VariableDeclarations.NamedVariable ||
                                                p instanceof J.Return ||
                                                p instanceof JContainer ||
                                                p instanceof J.CompilationUnit
        ).getValue() instanceof J.Block;
    }

    private boolean inMethodCallChain() {
        return getCursor().dropParentUntil(p -> !(p instanceof JRightPadded)).getValue() instanceof J.MethodInvocation;
    }

    private J.MethodInvocation inheritSelectAfter(J.MethodInvocation method, Stack<Space> prefix) {
        return (J.MethodInvocation) new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public <T> JRightPadded<T> visitRightPadded(@Nullable JRightPadded<T> right,
                                                        JRightPadded.Location loc,
                                                        ExecutionContext executionContext) {
                return prefix.isEmpty() ? right : right.withAfter(prefix.pop());
            }
        }.visit(method, new InMemoryExecutionContext());
    }

    private Space getSelectAfter(J.MethodInvocation method) {
        return new JavaIsoVisitor<List<Space>>() {
            @Override
            public <T> JRightPadded<T> visitRightPadded(@Nullable JRightPadded<T> right,
                                                        JRightPadded.Location loc,
                                                        List<Space> selectAfter) {
                if (selectAfter.isEmpty()) {
                    selectAfter.add(right.getAfter());
                }
                return right;
            }
        }.reduce(method, new ArrayList<>()).get(0);
    }
}
