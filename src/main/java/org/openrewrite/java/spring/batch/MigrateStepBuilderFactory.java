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
package org.openrewrite.java.spring.batch;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MigrateStepBuilderFactory extends Recipe {

    private static final String STEP_BUILDER_FACTORY_GET = "org.springframework.batch.core.configuration.annotation.StepBuilderFactory get(java.lang.String)";

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
        return Preconditions.check(new UsesMethod<>(STEP_BUILDER_FACTORY_GET),
                new JavaVisitor<ExecutionContext>() {
                    @Override
                    public J visit(@Nullable Tree tree, ExecutionContext ctx) {
                        tree = new AddJobRepositoryVisitor().visit(tree, ctx);
                        return new NewStepBuilderVisitor().visit(tree, ctx);
                    }
                }
        );
    }

    private static class AddJobRepositoryVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);

            // Remove StepBuilderFactory field if StepBuilderFactory.get(..) is used further down
            if (!FindMethods.find(classDeclaration, STEP_BUILDER_FACTORY_GET).isEmpty()) {
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                    if (statement instanceof J.VariableDeclarations &&
                        ((J.VariableDeclarations) statement).getTypeExpression() != null) {
                        if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                                "org.springframework.batch.core.configuration.annotation.StepBuilderFactory")) {
                            return null;
                        }
                    }
                    return statement;
                })));
            }

            return cd;
        }

        @Override
        public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
            // Add JobRepository parameter to method if StepBuilderFactory.get(..) is used further down
            if (!FindMethods.find(md, STEP_BUILDER_FACTORY_GET).isEmpty()) {
                List<Object> params = md.getParameters().stream()
                        .filter(j -> !(j instanceof J.Empty) && !isJobBuilderFactoryParameter(j))
                        .collect(Collectors.toList());

                if (params.isEmpty() && md.isConstructor()) {
                    //noinspection DataFlowIssue
                    return null;
                }

                if (md.getParameters().stream().noneMatch(this::isJobRepositoryParameter) && !md.isConstructor()) {
                    maybeAddImport("org.springframework.batch.core.repository.JobRepository");
                    boolean parametersEmpty = md.getParameters().isEmpty() || md.getParameters().get(0) instanceof J.Empty;
                    J.VariableDeclarations vdd = JavaTemplate.builder("JobRepository jobRepository")
                            .contextSensitive()
                            .imports("org.springframework.batch.core.repository.JobRepository")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.+"))
                            .build()
                            .<J.MethodDeclaration>apply(getCursor(), md.getCoordinates().replaceParameters())
                            .getParameters().get(0).withPrefix(parametersEmpty ? Space.EMPTY : Space.SINGLE_SPACE);
                    if (parametersEmpty) {
                        md = md.withParameters(Collections.singletonList(vdd))
                                .withMethodType(md.getMethodType()
                                        .withParameterTypes(Collections.singletonList(vdd.getType())));
                    } else {
                        md = md.withParameters(ListUtils.concat(md.getParameters(), vdd))
                                .withMethodType(md.getMethodType()
                                        .withParameterTypes(ListUtils.concat(md.getMethodType().getParameterTypes(), vdd.getType())));
                    }
                }
            }

            return super.visitMethodDeclaration(md, ctx);
        }

        private boolean isJobRepositoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations &&
                   TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                           "org.springframework.batch.core.repository.JobRepository");
        }

        private boolean isJobBuilderFactoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations &&
                   TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                           "org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
        }
    }

    private static class NewStepBuilderVisitor extends JavaVisitor<ExecutionContext> {
        final MethodMatcher STEP_BUILDER_FACTORY_MATCHER = new MethodMatcher(STEP_BUILDER_FACTORY_GET);

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (STEP_BUILDER_FACTORY_MATCHER.matches(mi)) {
                maybeAddImport("org.springframework.batch.core.step.builder.StepBuilder", false);
                maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
                maybeRemoveImport("org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
                return JavaTemplate.builder("new StepBuilder(#{any(java.lang.String)}, jobRepository)")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.+", "spring-batch-infrastructure-5.+"))
                        .imports("org.springframework.batch.core.step.builder.StepBuilder")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0));
            }
            return super.visitMethodInvocation(mi, ctx);
        }
    }
}
