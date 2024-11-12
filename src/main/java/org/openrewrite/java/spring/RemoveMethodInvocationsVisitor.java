/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @deprecated for removal
 * @see org.openrewrite.java.RemoveMethodInvocationsVisitor
 */
@Deprecated
public class RemoveMethodInvocationsVisitor extends JavaVisitor<ExecutionContext> {
    private final org.openrewrite.java.RemoveMethodInvocationsVisitor impl;

    public RemoveMethodInvocationsVisitor(Map<MethodMatcher, Predicate<List<Expression>>> matchers) {
        this.impl = new org.openrewrite.java.RemoveMethodInvocationsVisitor(matchers);
    }

    public RemoveMethodInvocationsVisitor(List<String> methodSignatures) {
        this.impl = new org.openrewrite.java.RemoveMethodInvocationsVisitor(methodSignatures);
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        return impl.visitMethodInvocation(method, ctx);
    }

    public static Predicate<List<Expression>> isTrueArgument() {
        return org.openrewrite.java.RemoveMethodInvocationsVisitor.isTrueArgument();
    }

    public static Predicate<List<Expression>> isFalseArgument() {
        return org.openrewrite.java.RemoveMethodInvocationsVisitor.isFalseArgument();
    }

    public static boolean isTrue(Expression expression) {
        return org.openrewrite.java.RemoveMethodInvocationsVisitor.isTrue(expression);
    }

    public static boolean isFalse(Expression expression) {
        return org.openrewrite.java.RemoveMethodInvocationsVisitor.isFalse(expression);
    }

    @Override
    public J.Lambda visitLambda(J.Lambda lambda, ExecutionContext ctx) {
        return impl.visitLambda(lambda, ctx);
    }

    @Override
    public J.Block visitBlock(J.Block block, ExecutionContext ctx) {
        return impl.visitBlock(block, ctx);
    }
}
