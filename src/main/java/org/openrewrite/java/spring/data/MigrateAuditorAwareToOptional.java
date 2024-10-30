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
package org.openrewrite.java.spring.data;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.tree.*;

public class MigrateAuditorAwareToOptional extends Recipe {

    private static final TypeMatcher isAuditorAware = new TypeMatcher("org.springframework.data.domain.AuditorAware", true);
    private static final MethodMatcher isCurrentAuditor = new MethodMatcher("org.springframework.data.domain.AuditorAware getCurrentAuditor()", true);
    private static final TypeMatcher isOptional = new TypeMatcher("java.util.Optional");
    private static final JavaTemplate wrapOptional = JavaTemplate.builder("Optional.ofNullable(#{any()})").contextSensitive().imports("java.util.Optional").build();

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
        JavaIsoVisitor<ExecutionContext> implementationVisitor = implementationVisitor();
        JavaIsoVisitor<ExecutionContext> functionalVisitor = functionalVisitor(implementationVisitor);

        //TODO the other visitors for new AuditorAware() {...} and method references.

        return new TreeVisitor<Tree, ExecutionContext>() {

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext, Cursor parent) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                tree = implementationVisitor.visit(tree, executionContext);
                tree = functionalVisitor.visit(tree, executionContext);
                return tree;
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> implementationVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                maybeAddImport("java.util.Optional");
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext executionContext) {
                if (classDeclaration.getImplements() == null || classDeclaration.getImplements().stream().noneMatch(typeTree -> isAuditorAware.matches(typeTree.getType()))) {
                    return classDeclaration;
                }
                return super.visitClassDeclaration(classDeclaration, executionContext);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                TypeTree returnType = method.getReturnTypeExpression();
                if (method.getMethodType() == null || !isCurrentAuditor.matches(method.getMethodType())
                        || returnType == null || returnType.getType().toString().matches("java.util.Optional<.*>")) {
                    return method;
                }
                Space space = returnType.getPrefix();
                returnType = TypeTree.build("java.util.Optional<" + returnType.getType() + ">");
                return super.visitMethodDeclaration(method, ctx).withReturnTypeExpression(returnType.withPrefix(space));
            }

            @Override
            public J.Return visitReturn(J.Return return_, ExecutionContext executionContext) {
                Expression expression = return_.getExpression();
                if (expression == null) {
                    return return_;
                }
                J.Return altered = wrapOptional.apply(getCursor(), expression.getCoordinates().replace(), expression);
                if (altered == null) {
                    return return_;
                }

                return altered;
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> functionalVisitor(JavaIsoVisitor<ExecutionContext> implementationVisitor) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                maybeAddImport("java.util.Optional");
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                if (!isAuditorAware.matches(method.getReturnTypeExpression()) || method.getBody() == null || method.getBody().getStatements().size() != 1) {
                    return method;
                }
                Statement statement = method.getBody().getStatements().get(0);
                if (!(statement instanceof J.Return)) {
                    return method;
                }

                return super.visitMethodDeclaration(method, executionContext);
            }


            @Override
            public J.Return visitReturn(J.Return return_, ExecutionContext executionContext) {
                Expression expression = return_.getExpression();
                if (expression instanceof J.Lambda) {
                    J.Lambda lambda = ((J.Lambda) expression);
                    J body = lambda.getBody();
                    if (body instanceof J.Literal) {
                        body = wrapOptional.apply(new Cursor(getCursor(), lambda), lambda.getCoordinates().replace(), body);
                        return return_.withExpression(lambda.withBody(body));
                    } else {
                        return super.visitReturn(return_, executionContext);
                    }
                } else if (expression instanceof J.MethodInvocation) {
                    if (isOptional.matches(((J.MethodInvocation) expression).getMethodType().getReturnType())) {
                        return return_;
                    }
                    return return_.withExpression(wrapOptional.apply(new Cursor(getCursor(), expression), expression.getCoordinates().replace(), expression));
                } else if (expression instanceof J.NewClass && isAuditorAware.matches(((J.NewClass) expression).getClazz().getType())) {
                    implementationVisitor.setCursor(new Cursor(getCursor(), expression));
                    return return_.withExpression(implementationVisitor.visitNewClass((J.NewClass) expression, executionContext));
                } else if (expression instanceof J.MemberReference) {
                    J.MemberReference memberReference = (J.MemberReference) expression;
                    JavaType.Method methodType = memberReference.getMethodType();
                    if (methodType == null || isOptional.matches(methodType.getReturnType())) {
                        return return_;
                    }
                    Expression containing = memberReference.getContaining();
                    //TODO Question for TIM: If I use #{any()} for the method name, as getName returns a String, I get a java.lang.ClassCastException: class java.lang.String cannot be cast to class org.openrewrite.java.tree.J
                    JavaTemplate template = JavaTemplate.builder("() -> Optional.ofNullable(#{any()}." + methodType.getName() + "())").imports("java.util.Optional").contextSensitive().build();
                    return return_.withExpression(template.apply(new Cursor(getCursor(), expression), memberReference.getCoordinates().replace(), containing));
                }
                return return_;
            }
        };
    }
}
