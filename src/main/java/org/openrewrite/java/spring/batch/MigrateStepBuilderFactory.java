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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class MigrateStepBuilderFactory extends Recipe {

    private static final String STEP_BUILDER_FACTORY_GET = "org.springframework.batch.core.configuration.annotation.StepBuilderFactory get(java.lang.String)";

    @Getter
    final String displayName = "Migrate `StepBuilderFactory` to `StepBuilder`";

    @Getter
    final String description = "`StepBuilderFactory` was deprecated in spring-batch 5.x. It is replaced by `StepBuilder`.";

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

    private static final String STEP_BUILDER_FACTORY_FQN =
            "org.springframework.batch.core.configuration.annotation.StepBuilderFactory";

    private static class AddJobRepositoryVisitor extends JavaIsoVisitor<ExecutionContext> {

        // Set true before children are visited when the enclosing class has references to a
        // StepBuilderFactory field that the visitor will not rewrite (getters/setters, helper
        // methods). In that case we leave the field, constructor parameters, and constructor
        // body assignments intact rather than producing broken code.
        private boolean preserveField;

        private Set<String> stepBuilderFactoryFieldNames = new HashSet<>();

        private @Nullable JavaType scopeType;

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
            boolean classHasGet = !FindMethods.find(classDeclaration, STEP_BUILDER_FACTORY_GET).isEmpty();
            if (classHasGet) {
                scopeType = classDeclaration.getType();
                stepBuilderFactoryFieldNames = collectStepBuilderFactoryFieldNames(classDeclaration);
                preserveField = !stepBuilderFactoryFieldNames.isEmpty() &&
                        hasReferencesOutsideRewrittenMethods(classDeclaration, stepBuilderFactoryFieldNames);
            }

            J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);

            // Remove StepBuilderFactory field if StepBuilderFactory.get(..) is used further down
            if (classHasGet && !preserveField) {
                cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                    if (statement instanceof J.VariableDeclarations &&
                            ((J.VariableDeclarations) statement).getTypeExpression() != null) {
                        if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                                STEP_BUILDER_FACTORY_FQN)) {
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
            boolean bodyHasGet = !FindMethods.find(md, STEP_BUILDER_FACTORY_GET).isEmpty();
            boolean isConstructorToRewrite = md.isConstructor() && !preserveField &&
                    md.getParameters().stream().anyMatch(this::isStepBuilderFactoryParameter);

            // Add JobRepository parameter to method if StepBuilderFactory.get(..) is used further down,
            // or rewrite a constructor that takes StepBuilderFactory as a parameter (Defect 3).
            if (bodyHasGet || isConstructorToRewrite) {
                Set<String> removedParamNames = md.getParameters().stream()
                        .filter(this::isStepBuilderFactoryParameter)
                        .map(p -> ((J.VariableDeclarations) p).getVariables().get(0).getSimpleName())
                        .collect(toSet());

                List<Statement> params = ListUtils.filter(md.getParameters(), j -> !isStepBuilderFactoryParameter(j));
                if (params.isEmpty() && md.isConstructor()) {
                    return null;
                }
                Space firstParamPrefix = md.getParameters().get(0).getPrefix().withComments(emptyList());
                params = ListUtils.mapFirst(params, p -> p.withPrefix(firstParamPrefix));

                if (md.getParameters().stream().noneMatch(this::isJobRepositoryParameter) && !md.isConstructor()) {
                    maybeAddImport("org.springframework.batch.core.repository.JobRepository");
                    boolean parametersEmpty = params.isEmpty() || params.get(0) instanceof J.Empty;
                    J.VariableDeclarations vdd = JavaTemplate.builder("JobRepository jobRepository")
                            .contextSensitive()
                            .imports("org.springframework.batch.core.repository.JobRepository")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.1.+"))
                            .build()
                            .<J.MethodDeclaration>apply(getCursor(), md.getCoordinates().replaceParameters())
                            .getParameters().get(0).withPrefix(parametersEmpty ? Space.EMPTY : Space.SINGLE_SPACE);
                    if (parametersEmpty) {
                        md = md.withParameters(singletonList(vdd))
                                .withMethodType(md.getMethodType()
                                        .withParameterTypes(singletonList(vdd.getType())));
                    } else {
                        md = md.withParameters(ListUtils.concat(params, vdd))
                                .withMethodType(md.getMethodType()
                                        .withParameterTypes(ListUtils.concat(md.getMethodType().getParameterTypes(), vdd.getType())));
                    }
                } else if (md.isConstructor()) {
                    // For constructors we drop the StepBuilderFactory parameter without adding JobRepository.
                    md = md.withParameters(params);
                    if (md.getMethodType() != null) {
                        List<org.openrewrite.java.tree.JavaType> remainingTypes = md.getParameters().stream()
                                .filter(J.VariableDeclarations.class::isInstance)
                                .map(p -> ((J.VariableDeclarations) p).getType())
                                .collect(toList());
                        md = md.withMethodType(md.getMethodType().withParameterTypes(remainingTypes));
                    }
                }

                // Remove orphaned `this.<field> = <removedParam>;` body assignments left by parameter removal.
                return removeOrphanedFieldAssignments(md, removedParamNames);
            }

            return super.visitMethodDeclaration(md, ctx);
        }

        private boolean isJobRepositoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations &&
                    TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                            "org.springframework.batch.core.repository.JobRepository");
        }

        private boolean isStepBuilderFactoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations &&
                    TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                            STEP_BUILDER_FACTORY_FQN);
        }

        private Set<String> collectStepBuilderFactoryFieldNames(J.ClassDeclaration cd) {
            Set<String> names = new HashSet<>();
            for (Statement stmt : cd.getBody().getStatements()) {
                if (stmt instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                    if (vd.getTypeExpression() != null &&
                            TypeUtils.isOfClassType(vd.getTypeExpression().getType(), STEP_BUILDER_FACTORY_FQN)) {
                        vd.getVariables().forEach(v -> names.add(v.getSimpleName()));
                    }
                }
            }
            return names;
        }

        private boolean hasReferencesOutsideRewrittenMethods(J.ClassDeclaration cd, Set<String> fieldNames) {
            for (Statement stmt : cd.getBody().getStatements()) {
                if (!(stmt instanceof J.MethodDeclaration)) {
                    continue;
                }
                J.MethodDeclaration m = (J.MethodDeclaration) stmt;
                if (m.isConstructor()) {
                    continue;
                }
                if (!FindMethods.find(m, STEP_BUILDER_FACTORY_GET).isEmpty()) {
                    continue;
                }
                if (containsFieldReference(m, fieldNames)) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsFieldReference(J.MethodDeclaration m, Set<String> fieldNames) {
            JavaType currentScopeType = scopeType;
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean flag) {
                    JavaType.Variable fieldType = identifier.getFieldType();
                    // getFieldType() is non-null only for field references; local variables
                    // and parameters that happen to share the field's simple name are skipped.
                    if (fieldType != null &&
                            fieldNames.contains(identifier.getSimpleName()) &&
                            TypeUtils.isOfType(fieldType.getOwner(), currentScopeType)) {
                        flag.set(true);
                    }
                    return super.visitIdentifier(identifier, flag);
                }
            }.reduce(m, new AtomicBoolean()).get();
        }

        private J.MethodDeclaration removeOrphanedFieldAssignments(J.MethodDeclaration md, Set<String> removedParamNames) {
            if (md.getBody() == null || removedParamNames.isEmpty()) {
                return md;
            }
            return md.withBody(md.getBody().withStatements(ListUtils.map(md.getBody().getStatements(), stmt -> {
                if (stmt instanceof J.Assignment) {
                    Expression rhs = ((J.Assignment) stmt).getAssignment();
                    if (rhs instanceof J.Identifier && removedParamNames.contains(((J.Identifier) rhs).getSimpleName())) {
                        return null;
                    }
                }
                return stmt;
            })));
        }
    }

    private static class NewStepBuilderVisitor extends JavaVisitor<ExecutionContext> {
        final MethodMatcher STEP_BUILDER_FACTORY_MATCHER = new MethodMatcher(STEP_BUILDER_FACTORY_GET);

        @Override
        public J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
            if (STEP_BUILDER_FACTORY_MATCHER.matches(mi)) {
                maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
                maybeRemoveImport("org.springframework.batch.core.configuration.annotation.StepBuilderFactory");
                maybeAddImport("org.springframework.batch.core.step.builder.StepBuilder", false);
                return JavaTemplate.builder("new StepBuilder(#{any(java.lang.String)}, jobRepository)")
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                        .imports("org.springframework.batch.core.step.builder.StepBuilder")
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), mi.getArguments().get(0));
            }
            return super.visitMethodInvocation(mi, ctx);
        }
    }
}
