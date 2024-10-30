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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;

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
        JavaIsoVisitor<ExecutionContext> implementationVisitor = implementationVisitor();
        JavaIsoVisitor<ExecutionContext> functionalInterfaceVisitor = functionalInterfaceVisitor();

        //TODO the other visitors for new AuditorAware() {...} and method references.
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                System.out.println(TreeVisitingPrinter.printTree(getCursor()));
                cu = super.visitCompilationUnit(cu, executionContext);
                cu = implementationVisitor.visitCompilationUnit(cu, executionContext);
//                cu = functionalInterfaceVisitor.visitCompilationUnit(cu, executionContext);
                maybeAddImport("java.util.Optional");
                return cu;
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> implementationVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

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
                if (!isCurrentAuditor.matches(method.getMethodType()) || isOptional.matches(returnType)) {
                    return method;
                }
                return (super.visitMethodDeclaration(method, ctx))
                        .withReturnTypeExpression(TypeTree.build("java.util.Optional<" + returnType.printTrimmed(getCursor()) + ">"));
            }

            @Override
            public J.Return visitReturn(J.Return return_, ExecutionContext executionContext) {
                Expression expression = return_.getExpression();
                if (expression == null) {
                    return return_;
                }
                expression = JavaTemplate.builder("Optional.ofNullable(#{any()})")
                        .imports("java.util.Optional")
                        .build()
                        .apply(getCursor(), expression.getCoordinates().replace(), expression);
                if (expression == null) {
                    return return_;
                }

                return return_.withExpression(expression);
            }
        };
    }

    private JavaIsoVisitor<ExecutionContext> functionalInterfaceVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                if (!isAuditorAware.matches(method.getReturnTypeExpression())) {
                    return method;
                }
                return super.visitMethodDeclaration(method, executionContext);
            }

            @Override
            public J.Return visitReturn(J.Return return_, ExecutionContext executionContext) {
                Expression expression = return_.getExpression();
                //TODO return Optional.ofNullable(expression) of the JReturn statement in the getCurrentAuditor method
                return return_;
            }
        };
    }
}
