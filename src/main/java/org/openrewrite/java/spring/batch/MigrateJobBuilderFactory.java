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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

public class MigrateJobBuilderFactory extends Recipe {
    private static final MethodMatcher JOB_BUILDER_FACTORY = new MethodMatcher(
            "org.springframework.batch.core.configuration.annotation.JobBuilderFactory get(java.lang.String)");

    @Override
    public String getDisplayName() {
        return "Migrate `JobBuilderFactory` to `JobBuilder`";
    }

    @Override
    public String getDescription() {
        return "`JobBuilderFactory` was deprecated in spring-batch 5.x. It is replaced by `JobBuilder`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(JOB_BUILDER_FACTORY), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (JOB_BUILDER_FACTORY.matches(method)) {
                    J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                    J.MethodDeclaration enclosingMethod = getCursor().firstEnclosingOrThrow(J.MethodDeclaration.class);

                    maybeAddImport("org.springframework.batch.core.job.builder.JobBuilder", false);
                    maybeRemoveImport("org.springframework.batch.core.configuration.annotation.JobBuilderFactory");
                    maybeAddImport("org.springframework.batch.core.repository.JobRepository");

                    doAfterVisit(new MigrateJobBuilderFactory.RemoveJobBuilderFactoryVisitor(clazz, enclosingMethod));

                    return method.withTemplate(JavaTemplate
                                    .builder("new JobBuilder(#{any(java.lang.String)}, jobRepository)")
                                    .context(getCursor())
                                    .javaParser(JavaParser.fromJavaVersion()
                                            .classpathFromResources(ctx, "spring-batch-core-5.0.0"))
                                    .imports("org.springframework.batch.core.repository.JobRepository",
                                            "org.springframework.batch.core.job.builder.JobBuilder")
                                    .build(),
                            getCursor(),
                            method.getCoordinates().replace(),
                            method.getArguments().get(0));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }

    private static class RemoveJobBuilderFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final ClassDeclaration scope;

        private final MethodDeclaration enclosingMethod;

        public RemoveJobBuilderFactoryVisitor(ClassDeclaration scope, MethodDeclaration enclosingMethod) {
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
                if (statement instanceof J.VariableDeclarations && ((J.VariableDeclarations) statement).getTypeExpression() != null) {
                    if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                            "org.springframework.batch.core.configuration.annotation.JobBuilderFactory")) {
                        return null;
                    }
                }
                return statement;
            })));
            maybeRemoveImport("org.springframework.batch.core.configuration.annotation.JobBuilderFactory");
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
                    .context(getCursor())
                    .imports("org.springframework.batch.core.repository.JobRepository",
                            "org.springframework.batch.core.job.builder.JobBuilder",
                            "org.springframework.batch.core.Step")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-batch-core-5.0.0"))
                    .build();

            md = md.withTemplate(paramsTemplate, getCursor(), md.getCoordinates().replaceParameters(), params.toArray());

            maybeRemoveImport("org.springframework.batch.core.configuration.annotation.JobBuilderFactory");
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
                    "org.springframework.batch.core.configuration.annotation.JobBuilderFactory");
        }
    }
}
