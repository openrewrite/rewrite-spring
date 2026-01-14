/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.batch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ConvertReceiveTypeWhenCallStepExecutionMethod extends Recipe {

    @Getter
    final String displayName = "Convert receive type in some invocation of StepExecution.xx()";

    @Getter
    final String description = "Convert receive type in some invocation of StepExecution.xx().";


    private static final MethodMatcher CommitCount = new MethodMatcher("org.springframework.batch.core.StepExecution getCommitCount()");
    private static final MethodMatcher ReadCount = new MethodMatcher("org.springframework.batch.core.StepExecution getReadCount()");
    private static final MethodMatcher WriteCount = new MethodMatcher("org.springframework.batch.core.StepExecution getWriteCount()");
    private static final MethodMatcher RollbackCount = new MethodMatcher("org.springframework.batch.core.StepExecution getRollbackCount()");
    private static final MethodMatcher FilterCount = new MethodMatcher("org.springframework.batch.core.StepExecution getFilterCount()");
    private static final MethodMatcher SkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getSkipCount()");
    private static final MethodMatcher ReadSkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getReadSkipCount()");
    private static final MethodMatcher WriteSkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getWriteSkipCount()");
    private static final MethodMatcher ProcessSkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getProcessSkipCount()");

    private static final MethodMatcher WHEN_MATCHER = new MethodMatcher("org.mockito.Mockito when(..)");
    private static final MethodMatcher THEN_RETURN_MATCHER = new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>("org.springframework.batch.core.StepExecution get*Count()"),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                        memberRef = super.visitMemberReference(memberRef, ctx);
                        final J.MemberReference mr = memberRef;
                        if (Stream.of(CommitCount, ReadCount, WriteCount, RollbackCount, FilterCount, SkipCount, ReadSkipCount, WriteSkipCount, ProcessSkipCount)
                                .anyMatch(methodMatcher -> methodMatcher.matches(mr))) {
                            doAfterVisit(new MemberReferenceToMethodInvocation(memberRef));
                        }
                        return memberRef;
                    }


                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        method = super.visitMethodInvocation(method, ctx);
                        final J.MethodInvocation md = method;
                        if (Stream.of(CommitCount, ReadCount, WriteCount, RollbackCount, FilterCount, SkipCount, ReadSkipCount, WriteSkipCount, ProcessSkipCount)
                                .anyMatch(methodMatcher -> methodMatcher.matches(md))) {
                            doAfterVisit(new AddCastToMethodInvocation(method));
                        }
                        return method;
                    }
                });
    }

    @RequiredArgsConstructor
    private static class AddCastToMethodInvocation extends JavaVisitor<ExecutionContext> {

        private final J.MethodInvocation selfMethodInvocation;

        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            J j = super.visitVariableDeclarations(multiVariable, ctx);
            VisitMethodInvocation visitMethodInvocation = new VisitMethodInvocation(selfMethodInvocation);
            visitMethodInvocation.visitVariableDeclarations(multiVariable, ctx);
            if (visitMethodInvocation.isFound) {
                doAfterVisit(new AddCast());
            }
            return j;
        }

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J j = super.visitMethodInvocation(method, ctx);
            if (method != selfMethodInvocation) {
                if (THEN_RETURN_MATCHER.matches(method)) {
                    if (method.getArguments().get(0).getType() != null &&
                            ("int".equals(method.getArguments().get(0).getType().toString()) ||
                                    method.getArguments().get(0).getType().isAssignableFrom(Pattern.compile("java.lang.Integer")))) {

                        final AtomicBoolean findWhen = new AtomicBoolean(false);
                        new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                                if (WHEN_MATCHER.matches(method)) {
                                    findWhen.set(true);
                                }
                                return super.visitMethodInvocation(method, ctx);
                            }
                        }.visit(method, ctx);
                        if (findWhen.get()) {
                            j = JavaTemplate.builder("#{any(org.mockito.stubbing.OngoingStubbing)}.thenReturn((long) #{})")
                                    .contextSensitive()
                                    .build().apply(getCursor(), method.getCoordinates().replace(), method.getSelect(), method.getArguments().get(0))
                                    .withPrefix(method.getPrefix());
                        }
                    }
                    return j;

                }
                if (WHEN_MATCHER.matches(method)) {
                    return j;
                }
                VisitMethodInvocation visitMethodInvocation = new VisitMethodInvocation(selfMethodInvocation);
                visitMethodInvocation.visitMethodInvocation(method, ctx);
                if (visitMethodInvocation.isFound) {
                    doAfterVisit(new AddCast());
                }

            }
            return j;

        }

        @Override
        public J visitReturn(J.Return return_, ExecutionContext ctx) {
            J j = super.visitReturn(return_, ctx);
            VisitMethodInvocation visitMethodInvocation = new VisitMethodInvocation(selfMethodInvocation);
            visitMethodInvocation.visitReturn(return_, ctx);
            if (visitMethodInvocation.isFound) {
                doAfterVisit(new AddCast());
            }
            return j;
        }

        private class AddCast extends JavaVisitor<ExecutionContext> {
            @Override
            protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
                return new JavadocVisitor<ExecutionContext>(this) {
                    @Override
                    public Javadoc visitReference(Javadoc.Reference reference, ExecutionContext ctx) {
                        return reference;
                    }
                };
            }

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J parent = getCursor().getParentTreeCursor().getValue();
                if (selfMethodInvocation == method &&
                        !(parent instanceof J.TypeCast) &&
                        !(parent instanceof Expression && WHEN_MATCHER.matches((Expression) parent))) {
                    return JavaTemplate.builder("(int) #{any(int)}")
                            .contextSensitive()
                            .build().apply(getCursor(), method.getCoordinates().replace(), method)
                            .withPrefix(method.getPrefix());
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }

    }

    @RequiredArgsConstructor
    private static class VisitMethodInvocation extends JavaIsoVisitor<ExecutionContext> {

        private final J.MethodInvocation selfMethodInvocation;

        private boolean isFound = false;

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            method = super.visitMethodInvocation(method, ctx);
            if (method == selfMethodInvocation) {
                isFound = true;
            }
            return method;
        }
    }

    @RequiredArgsConstructor
    private static class MemberReferenceToMethodInvocation extends JavaVisitor<ExecutionContext> {

        private final J.MemberReference selfMemberRef;

        @Override
        public J visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
            if (selfMemberRef == memberRef) {
                J.MemberReference mr = (J.MemberReference) super.visitMemberReference(memberRef, ctx);
                if (mr.getMethodType() == null) {
                    return mr;
                }

                String templateCode = String.format("_stepExecution -> (int)_stepExecution.%s()",
                        mr.getReference().getSimpleName());
                return JavaTemplate.builder(templateCode)
                        .contextSensitive()
                        .build().apply(getCursor(), mr.getCoordinates().replace())
                        .withPrefix(mr.getPrefix());
            }
            return super.visitMemberReference(memberRef, ctx);
        }
    }
}
