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
import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
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
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class MigrateJobBuilderFactory extends Recipe {
    private static final MethodMatcher JOB_BUILDER_FACTORY = new MethodMatcher(
            "org.springframework.batch.core.configuration.annotation.JobBuilderFactory get(java.lang.String)");

    @Getter
    final String displayName = "Migrate `JobBuilderFactory` to `JobBuilder`";

    @Getter
    final String description = "`JobBuilderFactory` was deprecated in spring-batch 5.x. It is replaced by `JobBuilder`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(JOB_BUILDER_FACTORY), new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (JOB_BUILDER_FACTORY.matches(method)) {
                    J.ClassDeclaration clazz = getCursor().firstEnclosingOrThrow(J.ClassDeclaration.class);
                    J.MethodDeclaration enclosingMethod = getCursor().firstEnclosingOrThrow(J.MethodDeclaration.class);

                    maybeRemoveImport("org.springframework.batch.core.configuration.annotation.JobBuilderFactory");
                    maybeAddImport("org.springframework.batch.core.job.builder.JobBuilder", false);
                    maybeAddImport("org.springframework.batch.core.repository.JobRepository");

                    doAfterVisit(new MigrateJobBuilderFactory.RemoveJobBuilderFactoryVisitor(clazz, enclosingMethod));

                    // The `jobRepository` argument references the parameter introduced by
                    // RemoveJobBuilderFactoryVisitor, which doesn't exist yet. Pass a typed shim
                    // identifier so the generated reference carries `JobRepository` type attribution.
                    J.Identifier jobRepository = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                            emptyList(), "jobRepository",
                            JavaType.ShallowClass.build("org.springframework.batch.core.repository.JobRepository"), null);

                    return JavaTemplate
                            .builder("new JobBuilder(#{any(java.lang.String)}, #{any(org.springframework.batch.core.repository.JobRepository)})")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion()
                                    .classpathFromResources(ctx, "spring-batch-core-5.1.+"))
                            .imports("org.springframework.batch.core.repository.JobRepository",
                                    "org.springframework.batch.core.job.builder.JobBuilder")
                            .build().apply(
                                    getCursor(),
                                    method.getCoordinates().replace(),
                                    method.getArguments().get(0),
                                    jobRepository
                            );
                }
                return super.visitMethodInvocation(method, ctx);
            }
        });
    }

    @RequiredArgsConstructor
    private static class RemoveJobBuilderFactoryVisitor extends JavaIsoVisitor<ExecutionContext> {

        private static final String JOB_BUILDER_FACTORY_FQN =
                "org.springframework.batch.core.configuration.annotation.JobBuilderFactory";

        private final J.ClassDeclaration scope;

        private final J.MethodDeclaration enclosingMethod;

        // Set true before children are visited when the class has references to a
        // JobBuilderFactory field that the visitor will not rewrite (getters/setters,
        // helper methods). In that case we leave the field, constructor parameters,
        // and constructor body assignments intact rather than producing broken code.
        private boolean preserveField;

        private Set<String> jobBuilderFactoryFieldNames = new HashSet<>();

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (classDecl.equals(scope)) {
                jobBuilderFactoryFieldNames = collectJobBuilderFactoryFieldNames(classDecl);
                preserveField = !jobBuilderFactoryFieldNames.isEmpty() &&
                        hasReferencesOutsideRewrittenMethods(classDecl, jobBuilderFactoryFieldNames);
            }

            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            if (!cd.equals(scope) || preserveField) {
                return cd;
            }
            cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
                if (statement instanceof J.VariableDeclarations && ((J.VariableDeclarations) statement).getTypeExpression() != null) {
                    if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
                            JOB_BUILDER_FACTORY_FQN)) {
                        //noinspection DataFlowIssue
                        return null;
                    }
                }
                return statement;
            })));
            maybeRemoveImport(JOB_BUILDER_FACTORY_FQN);
            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, ctx);

            if (!enclosingMethod.equals(md) && !md.isConstructor()) {
                return md;
            }

            // When the field is preserved (external references exist), leave constructors alone too —
            // their parameter + body assignment form a wired-up trio with the field.
            if (preserveField && md.isConstructor() && !enclosingMethod.equals(md)) {
                return md;
            }

            Set<String> removedParamNames = md.getParameters().stream()
                    .filter(this::isJobBuilderFactoryParameter)
                    .map(p -> ((J.VariableDeclarations) p).getVariables().get(0).getSimpleName())
                    .collect(toSet());

            List<Object> params = md.getParameters().stream()
                    .filter(j -> !(j instanceof J.Empty) && !isJobBuilderFactoryParameter(j))
                    .collect(toList());

            if (params.isEmpty() && md.isConstructor()) {
                //noinspection DataFlowIssue
                return null;
            }

            if (md.getParameters().stream().noneMatch(this::isJobRepositoryParameter) && !md.isConstructor()) {
                params.add("JobRepository jobRepository");
            }

            JavaTemplate paramsTemplate = JavaTemplate
                    .builder(params.stream().map(p -> "#{}").collect(joining(", ")))
                    .contextSensitive()
                    .imports("org.springframework.batch.core.repository.JobRepository",
                            "org.springframework.batch.core.job.builder.JobBuilder",
                            "org.springframework.batch.core.Step")
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-batch-core-5.1.+"))
                    .build();

            md = paramsTemplate.apply(getCursor(), md.getCoordinates().replaceParameters(), params.toArray());

            // Remove orphaned `this.<field> = <removedParam>;` assignments left behind by parameter removal.
            md = removeOrphanedFieldAssignments(md, removedParamNames);

            maybeRemoveImport(JOB_BUILDER_FACTORY_FQN);
            maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
            maybeAddImport("org.springframework.batch.core.repository.JobRepository");
            return md;
        }

        private boolean isJobRepositoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations &&
                    TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                            "org.springframework.batch.core.repository.JobRepository");
        }

        private boolean isJobBuilderFactoryParameter(Statement statement) {
            return statement instanceof J.VariableDeclarations &&
                    TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(),
                            JOB_BUILDER_FACTORY_FQN);
        }

        private Set<String> collectJobBuilderFactoryFieldNames(J.ClassDeclaration cd) {
            Set<String> names = new HashSet<>();
            for (Statement stmt : cd.getBody().getStatements()) {
                if (stmt instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) stmt;
                    if (vd.getTypeExpression() != null &&
                            TypeUtils.isOfClassType(vd.getTypeExpression().getType(), JOB_BUILDER_FACTORY_FQN)) {
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
                // Constructors get rewritten by visitMethodDeclaration
                if (m.isConstructor()) {
                    continue;
                }
                // The enclosingMethod for this visitor instance gets rewritten
                if (m.equals(enclosingMethod)) {
                    continue;
                }
                // Other methods containing JobBuilderFactory.get(...) are handled by other visitor instances
                if (!FindMethods.find(m, JOB_BUILDER_FACTORY_FQN + " get(java.lang.String)").isEmpty()) {
                    continue;
                }
                if (containsFieldReference(m, fieldNames)) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsFieldReference(J.MethodDeclaration m, Set<String> fieldNames) {
            JavaType scopeType = scope.getType();
            return new JavaIsoVisitor<AtomicBoolean>() {
                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean flag) {
                    JavaType.Variable fieldType = identifier.getFieldType();
                    // getFieldType() is non-null only for field references; local variables
                    // and parameters that happen to share the field's simple name are skipped.
                    if (fieldType != null &&
                            fieldNames.contains(identifier.getSimpleName()) &&
                            TypeUtils.isOfType(fieldType.getOwner(), scopeType)) {
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
                        //noinspection DataFlowIssue
                        return null;
                    }
                }
                return stmt;
            })));
        }
    }
}
