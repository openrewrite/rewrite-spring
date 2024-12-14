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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.util.MemberReferenceToMethodInvocation;
import org.openrewrite.java.tree.*;

public class MigrateAuditorAwareToOptional extends Recipe {

    private static final TypeMatcher isAuditorAware = new TypeMatcher("org.springframework.data.domain.AuditorAware", true);
    private static final MethodMatcher isCurrentAuditor = new MethodMatcher("org.springframework.data.domain.AuditorAware getCurrentAuditor()", true);
    private static final TypeMatcher isOptional = new TypeMatcher("java.util.Optional");

    @Override
    public String getDisplayName() {
        return "Make AuditorAware.getCurrentAuditor return `Optional`";
    }

    @Override
    public String getDescription() {
        return "As of Spring boot 2.0, the `AuditorAware.getCurrentAuditor` method should return an `Optional`. " +
               "This recipe will update the implementations of this method to return an `Optional` using the `ofNullable`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        ImplementationVisitor implementationVisitor = new ImplementationVisitor();
        FunctionalVisitor functionalVisitor = new FunctionalVisitor(implementationVisitor);
        return Preconditions.check(new UsesType<>("org.springframework.data.domain.AuditorAware", true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                tree = implementationVisitor.visit(tree, ctx);
                tree = functionalVisitor.visit(tree, ctx);
                return (J) tree;
            }
        });
    }

    private static class ImplementationVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            TypeTree returnType = method.getReturnTypeExpression();
            if (method.getMethodType() == null || !isCurrentAuditor.matches(method.getMethodType()) ||
                returnType == null || returnType.getType().toString().matches("java.util.Optional<.*>")) {
                return method;
            }
            Space space = returnType.getPrefix();
            returnType = TypeTree.build("java.util.Optional<" + returnType.getType() + ">");
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx).withReturnTypeExpression(returnType.withPrefix(space));
            doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(md));
            maybeAddImport("java.util.Optional");
            return md;
        }

        @Override
        public J.Return visitReturn(J.Return return_, ExecutionContext ctx) {
            Expression expression = return_.getExpression();
            if (expression == null) {
                return return_;
            }
            J.Return altered = JavaTemplate.builder("Optional.ofNullable(#{any()})")
                    .imports("java.util.Optional")
                    .build()
                    .apply(getCursor(), expression.getCoordinates().replace(), expression);
            if (altered == null) {
                return return_;
            }
            maybeAddImport("java.util.Optional");

            return altered;
        }
    }

    @RequiredArgsConstructor
    private static class FunctionalVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final JavaIsoVisitor<ExecutionContext> implementationVisitor;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (!isAuditorAware.matches(method.getReturnTypeExpression()) || method.getBody() == null || method.getBody().getStatements().size() != 1) {
                return method;
            }
            Statement statement = method.getBody().getStatements().get(0);
            if (!(statement instanceof J.Return)) {
                return method;
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public J.Return visitReturn(J.Return return_, ExecutionContext ctx) {
            Expression expression = return_.getExpression();
            if (expression instanceof J.MemberReference) {
                J.MemberReference memberReference = (J.MemberReference) expression;
                JavaType.Method methodType = memberReference.getMethodType();
                if (methodType == null || isOptional.matches(methodType.getReturnType())) {
                    return return_;
                }
                expression = (Expression) new MemberReferenceToMethodInvocation().visitNonNull(memberReference, ctx, new Cursor(getCursor(), expression).getParent());
            }
            if (expression instanceof J.Lambda) {
                J.Lambda lambda = ((J.Lambda) expression);
                J body = lambda.getBody();
                if (body instanceof J.MethodInvocation &&
                    (((J.MethodInvocation) body).getMethodType() != null && isOptional.matches(((J.MethodInvocation) body).getMethodType().getReturnType()))) {
                    return return_;
                }
                if (body instanceof J.Literal || body instanceof J.MethodInvocation) {
                    body = JavaTemplate.builder("Optional.ofNullable(#{any()})")
                            .contextSensitive()
                            .imports("java.util.Optional")
                            .build()
                            .apply(new Cursor(getCursor(), lambda), lambda.getCoordinates().replace(), body);
                    JavaType.Method methodType = ((J.MethodInvocation) body).getMethodType();
                    if (methodType != null) {
                        methodType = methodType.withReturnType(JavaType.buildType("java.util.Optional"));
                    }
                    body = ((J.MethodInvocation) body).withMethodType(methodType);
                    maybeAddImport("java.util.Optional");
                    return return_.withExpression(lambda.withBody(body));
                }
                return super.visitReturn(return_, ctx);
            }
            if (expression instanceof J.MethodInvocation) {
                if (((J.MethodInvocation) expression).getMethodType() != null && isOptional.matches(((J.MethodInvocation) expression).getMethodType().getReturnType())) {
                    return return_;
                }
                maybeAddImport("java.util.Optional");
                return return_.withExpression(JavaTemplate.builder("Optional.ofNullable(#{any()})")
                        .imports("java.util.Optional")
                        .build()
                        .apply(new Cursor(getCursor(), expression), expression.getCoordinates().replace(), expression));
            } else if (expression instanceof J.NewClass && isAuditorAware.matches(((J.NewClass) expression).getClazz().getType())) {
                implementationVisitor.setCursor(new Cursor(getCursor(), expression));
                maybeAddImport("java.util.Optional");
                return return_.withExpression(implementationVisitor.visitNewClass((J.NewClass) expression, ctx));
            }
            return return_;
        }
    }
}
