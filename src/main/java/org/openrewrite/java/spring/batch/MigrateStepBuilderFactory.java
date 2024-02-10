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
package org.openrewrite.java.spring.batch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.stream.Collectors;

public class MigrateStepBuilderFactory extends Recipe {

    private static final MethodMatcher STEP_BUILDER_FACTORY = new MethodMatcher(
            "org.springframework.batch.core.configuration.annotation.StepBuilderFactory get(java.lang.String)");

    @Override
    public String getDisplayName() {
        return "Migrate `StepBuilderFactory` to `StepBuilder`";
    }

    @Override
    public String getDescription() {
        return "`StepBuilderFactory` was deprecated in spring-batch 5.x. It is replaced by `StepBuilder`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (STEP_BUILDER_FACTORY.matches(method)) {
                    ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(ClassDeclaration.class);
                    MethodDeclaration enclosingMethod = getCursor().firstEnclosingOrThrow(MethodDeclaration.class);

                    maybeAddImport("org.springframework.batch.core.step.builder.StepBuilder", false);
                    maybeRemoveImport("org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
                    maybeAddImport("org.springframework.batch.core.repository.JobRepository");

                    doAfterVisit(new RemoveStepBuilderFactoryVisitor(clazz, enclosingMethod));

                    JavaTemplate template = JavaTemplate
                            .builder("new StepBuilder(#{any(java.lang.String)}, jobRepository)")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-batch-core-5.+", "spring-batch-infrastructure-5.+"))
                            .imports("org.springframework.batch.core.repository.JobRepository",
                                    "org.springframework.batch.core.step.builder.StepBuilder")
                            .build();
                    return template.apply(getCursor(), method.getCoordinates().replace(), method.getArguments().get(0));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        };
        return Preconditions.check(new UsesMethod<>(STEP_BUILDER_FACTORY), javaVisitor);
    }

    private static class RemoveStepBuilderFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final ClassDeclaration scope;

        private final MethodDeclaration enclosingMethod;

        public RemoveStepBuilderFactoryVisitor(ClassDeclaration scope, MethodDeclaration enclosingMethod) {
            this.scope = scope;
            this.enclosingMethod = enclosingMethod;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            if (!cd.equals(scope)) {
                return cd;
            }
            cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                if (statement instanceof J.VariableDeclarations
                    && ((J.VariableDeclarations) statement).getTypeExpression() != null) {
                    if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                            "org.springframework.batch.core.configuration.annotation.StepBuilderFactory")) {
                        return null;
                    }
                }
                return statement;
            })));
            maybeRemoveImport("org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, ctx);

            if (!enclosingMethod.equals(md) && !md.isConstructor()) {
                return md;
            }

            List<Object> params = md.getParameters().stream()
                    .filter(j -> !(j instanceof J.Empty) && !isJobBuilderFactoryParameter(j))
                    .collect(Collectors.toList());

            if (params.isEmpty() && md.isConstructor()) {
                //noinspection DataFlowIssue
                return null;
            }

            if (md.getParameters().stream().noneMatch(this::isJobRepositoryParameter) && !md.isConstructor()) {
                params.add("JobRepository jobRepository");
            }

            JavaTemplate paramsTemplate = JavaTemplate
                    .builder(params.stream().map(p -> "#{}").collect(Collectors.joining(", ")))
                    .contextSensitive()
                    .imports("org.springframework.batch.core.repository.JobRepository",
                            "org.springframework.batch.core.step.builder.StepBuilder",
                            "org.springframework.batch.core.Step")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-batch-core-5.+", "spring-batch-infrastructure-5.+"))
                    .build();

            md = paramsTemplate.apply(getCursor(), md.getCoordinates().replaceParameters(), params.toArray());

            maybeRemoveImport("org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
            maybeAddImport("org.springframework.batch.core.repository.JobRepository");
            maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
            return md;
        }

        private boolean isJobRepositoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations
                   && TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                    "org.springframework.batch.core.repository.JobRepository");
        }

        private boolean isJobBuilderFactoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations
                   && TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                    "org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
        }
    }
}