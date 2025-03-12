package org.openrewrite.java.spring.batch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ConvertReceiveTypeWhenCallStepExecutionMethod extends Recipe {
    @Override
    public String getDisplayName() {
        return "Convert receive type in some invocation of StepExecution.xx()";
    }

    @Override
    public String getDescription() {
        return "Convert receive type in some invocation of StepExecution.xx().";
    }


    private static final MethodMatcher CommitCount = new MethodMatcher("org.springframework.batch.core.StepExecution getCommitCount()");
    private static final MethodMatcher ReadCount = new MethodMatcher("org.springframework.batch.core.StepExecution getReadCount()");
    private static final MethodMatcher WriteCount = new MethodMatcher("org.springframework.batch.core.StepExecution getWriteCount()");
    private static final MethodMatcher RollbackCount = new MethodMatcher("org.springframework.batch.core.StepExecution getRollbackCount()");
    private static final MethodMatcher FilterCount = new MethodMatcher("org.springframework.batch.core.StepExecution getFilterCount()");
    private static final MethodMatcher SkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getSkipCount()");
    private static final MethodMatcher ReadSkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getReadSkipCount()");
    private static final MethodMatcher WriteSkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getWriteSkipCount()");
    private static final MethodMatcher ProcessSkipCount = new MethodMatcher("org.springframework.batch.core.StepExecution getProcessSkipCount()");

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {

        return Preconditions.check(
                Preconditions.or(new UsesMethod<>(CommitCount),
                        new UsesMethod<>(ReadCount),
                        new UsesMethod<>(WriteCount),
                        new UsesMethod<>(RollbackCount),
                        new UsesMethod<>(FilterCount),
                        new UsesMethod<>(SkipCount),
                        new UsesMethod<>(ReadSkipCount),
                        new UsesMethod<>(WriteSkipCount),
                        new UsesMethod<>(ProcessSkipCount)
                ),
                new JavaIsoVisitor<ExecutionContext>() {

                    @Override
                    public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                        memberRef = super.visitMemberReference(memberRef, ctx);
                        final J.MemberReference mr = memberRef;
                        if (Stream.of(CommitCount, ReadCount, WriteCount, RollbackCount, FilterCount, SkipCount, ReadSkipCount, WriteSkipCount, ProcessSkipCount).anyMatch(methodMatcher -> methodMatcher.matches(mr))) {
                            doAfterVisit(new MemberReferenceToMethodInvocation(memberRef));
                        }
                        return memberRef;
                    }


                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                        method = super.visitMethodInvocation(method, executionContext);
                        final J.MethodInvocation md = method;
                        if (Stream.of(CommitCount, ReadCount, WriteCount, RollbackCount, FilterCount, SkipCount, ReadSkipCount, WriteSkipCount, ProcessSkipCount).anyMatch(methodMatcher -> methodMatcher.matches(md))) {
                            doAfterVisit(new AddCastToMethodInvocation(method));
                        }
                        return method;
                    }

                });
    }

    private class AddCastToMethodInvocation extends JavaVisitor<ExecutionContext> {

        private J.MethodInvocation selfMethodInvocation;


        public AddCastToMethodInvocation(J.MethodInvocation selfMethodInvocation) {
            this.selfMethodInvocation = selfMethodInvocation;
        }



        @Override
        public J visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext executionContext) {
            J j =  super.visitVariableDeclarations(multiVariable, executionContext);
            VisitMethodInvocation visitMethodInvocation = new VisitMethodInvocation(selfMethodInvocation);
            visitMethodInvocation.visitVariableDeclarations(multiVariable, executionContext);
            if(visitMethodInvocation.isFound) {
                doAfterVisit(new AddCast());
            }
            return j;
        }




        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            J j = super.visitMethodInvocation(method, executionContext);
            if(method != selfMethodInvocation) {
                if(new MethodMatcher("org.mockito.stubbing.OngoingStubbing thenReturn(..)").matches(method)) {

                    if(method.getArguments().get(0).getType() != null && ("int".equals(method.getArguments().get(0).getType().toString()) || method.getArguments().get(0).getType().isAssignableFrom(Pattern.compile("java.lang.Integer")))) {

                        final AtomicBoolean findWhen = new AtomicBoolean(false);
                        new JavaIsoVisitor<ExecutionContext>() {
                            @Override
                            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                                if(new MethodMatcher("org.mockito.Mockito when(..)").matches(method)) {
                                    findWhen.set(true);
                                }
                                return super.visitMethodInvocation(method, executionContext);
                            }
                        }.visit(method, executionContext);
                        if(findWhen.get()) {
                            j = JavaTemplate.builder("#{}.thenReturn((long) #{})")
                                    .contextSensitive()
                                    .build().apply(getCursor(), method.getCoordinates().replace(), method.getSelect().print(getCursor()), method.getArguments().get(0).print(getCursor()))
                                    .withPrefix(method.getPrefix());
                        }
                    }
                    return j;

                } else if(new MethodMatcher("org.mockito.Mockito when(..)").matches(method)) {
                    return j;
                } else {
                    VisitMethodInvocation visitMethodInvocation = new VisitMethodInvocation(selfMethodInvocation);
                    visitMethodInvocation.visitMethodInvocation(method, executionContext);
                    if(visitMethodInvocation.isFound) {
                        doAfterVisit(new AddCast());
                    }
                }

            }
            return j;

        }

        @Override
        public J visitReturn(J.Return return_, ExecutionContext executionContext) {
            J j =  super.visitReturn(return_, executionContext);
            VisitMethodInvocation visitMethodInvocation = new VisitMethodInvocation(selfMethodInvocation);
            visitMethodInvocation.visitReturn(return_, executionContext);
            if(visitMethodInvocation.isFound) {
                doAfterVisit(new AddCast());
            }
            return j;
        }

        private class AddCast extends JavaVisitor<ExecutionContext> {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                if (selfMethodInvocation == method) {
                    return JavaTemplate.builder("(int) #{}")
                            .contextSensitive()
                            .build().apply(getCursor(), method.getCoordinates().replace(), method.print(getCursor()))
                            .withPrefix(method.getPrefix());
                } else {
                    return super.visitMethodInvocation(method, executionContext);
                }

            }
        }

    }


    private class VisitMethodInvocation extends JavaIsoVisitor<ExecutionContext> {

        private J.MethodInvocation selfMethodInvocation;

        public VisitMethodInvocation(J.MethodInvocation selfMethodInvocation) {
            this.selfMethodInvocation = selfMethodInvocation;
        }

        private boolean isFound = false;


        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            method = super.visitMethodInvocation(method, executionContext);
            if(method == selfMethodInvocation) {
                isFound = true;
            }
            return method;
        }
    }



    private class MemberReferenceToMethodInvocation extends JavaVisitor<ExecutionContext> {


        private J.MemberReference selfMemberRef;

        public MemberReferenceToMethodInvocation(J.MemberReference selfMemberRef) {
            this.selfMemberRef = selfMemberRef;
        }

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
            } else {
                return super.visitMemberReference(memberRef, ctx);
            }
        }

    }

}
