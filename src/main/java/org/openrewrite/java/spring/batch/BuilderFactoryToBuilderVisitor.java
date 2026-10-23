/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;

/**
 * Replaces {@code JobBuilderFactory.get(name)} and {@code StepBuilderFactory.get(name)} with
 * {@code new JobBuilder(name, jobRepository)} and {@code new StepBuilder(name, jobRepository)}.
 * <p>
 * The {@code JobRepository} comes from a parameter of the enclosing method, or else from a field of the enclosing class.
 * Only {@code @Bean} methods get a new {@code JobRepository} parameter, as Spring supplies it there; calls elsewhere
 * without a {@code JobRepository} in scope are left unchanged, as adding a parameter would break their callers.
 * Factory fields, constructor parameters and {@code @Bean} method parameters are removed once nothing references them.
 */
class BuilderFactoryToBuilderVisitor extends JavaVisitor<ExecutionContext> {

    private static final String JOB_REPOSITORY = "org.springframework.batch.core.repository.JobRepository";
    private static final String BEAN = "org.springframework.context.annotation.Bean";

    private final String factoryFqn;
    private final String builderFqn;
    private final String factoryGetPattern;
    private final MethodMatcher factoryGet;

    BuilderFactoryToBuilderVisitor(String factoryFqn, String builderFqn) {
        this.factoryFqn = factoryFqn;
        this.builderFqn = builderFqn;
        this.factoryGetPattern = factoryFqn + " get(java.lang.String)";
        this.factoryGet = new MethodMatcher(factoryGetPattern);
    }

    @Override
    public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);
        if (cd == classDecl) {
            return cd;
        }
        maybeRemoveImport(factoryFqn);
        maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
        return removeUnusedFactoryFields(cd);
    }

    @Override
    public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        J.MethodDeclaration md = method;
        if (md.getBody() != null && !FindMethods.find(md, factoryGetPattern).isEmpty()) {
            J.Identifier jobRepository = findJobRepository(md);
            if (jobRepository == null && isBean(md)) {
                md = addJobRepositoryParameter(md, ctx);
                jobRepository = variableName((J.VariableDeclarations) md.getParameters().get(md.getParameters().size() - 1));
            }
            if (jobRepository != null) {
                getCursor().putMessage(JOB_REPOSITORY, jobRepository);
            }
        }
        J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(md, ctx);
        if (m != method && (isBean(m) || m.isConstructor())) {
            m = removeUnusedFactoryParameters(m);
        }
        return m;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J j = super.visitMethodInvocation(method, ctx);
        if (!(j instanceof J.MethodInvocation) || !factoryGet.matches((J.MethodInvocation) j)) {
            return j;
        }
        J.Identifier jobRepository = getCursor().getNearestMessage(JOB_REPOSITORY);
        if (jobRepository == null) {
            return j;
        }
        J.MethodInvocation mi = (J.MethodInvocation) j;
        maybeAddImport(builderFqn);
        return JavaTemplate.builder("new " + builderFqn.substring(builderFqn.lastIndexOf('.') + 1) +
                        "(#{any(java.lang.String)}, #{any(" + JOB_REPOSITORY + ")})")
                .imports(builderFqn)
                .javaParser(JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                .build()
                .apply(updateCursor(mi), mi.getCoordinates().replace(), mi.getArguments().get(0), jobRepository);
    }

    private J.@Nullable Identifier findJobRepository(J.MethodDeclaration md) {
        for (Statement parameter : md.getParameters()) {
            if (isOfType(parameter, JOB_REPOSITORY)) {
                return variableName((J.VariableDeclarations) parameter);
            }
        }
        // Fields are not yet assigned while constructing, and not reachable from static methods
        if (md.isConstructor() || md.hasModifier(J.Modifier.Type.Static)) {
            return null;
        }
        J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (cd != null) {
            for (Statement statement : cd.getBody().getStatements()) {
                if (isOfType(statement, JOB_REPOSITORY)) {
                    return variableName((J.VariableDeclarations) statement);
                }
            }
        }
        return null;
    }

    private J.MethodDeclaration addJobRepositoryParameter(J.MethodDeclaration md, ExecutionContext ctx) {
        String name = VariableNameUtils.generateVariableName("jobRepository", getCursor(), VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER);
        List<Statement> params = ListUtils.filter(md.getParameters(), p -> !(p instanceof J.Empty));
        J.VariableDeclarations jobRepository = JavaTemplate.builder("JobRepository " + name)
                .contextSensitive()
                .imports(JOB_REPOSITORY)
                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.1.+"))
                .build()
                .<J.MethodDeclaration>apply(getCursor(), md.getCoordinates().replaceParameters())
                .getParameters().get(0)
                .withPrefix(params.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE);
        maybeAddImport(JOB_REPOSITORY);

        md = md.withParameters(ListUtils.concat(params, jobRepository));
        JavaType.Method type = md.getMethodType();
        if (type == null) {
            return md;
        }
        return md.withMethodType(type
                .withParameterNames(ListUtils.concat(type.getParameterNames(), name))
                .withParameterTypes(ListUtils.concat(type.getParameterTypes(), jobRepository.getType())));
    }

    private J.MethodDeclaration removeUnusedFactoryParameters(J.MethodDeclaration md) {
        List<Statement> params = md.getParameters();
        Set<Integer> unused = new HashSet<>();
        for (int i = 0; i < params.size(); i++) {
            Statement param = params.get(i);
            if (isOfType(param, factoryFqn) &&
                    !referencesParameter(md.getBody(), ((J.VariableDeclarations) param).getVariables().get(0).getSimpleName())) {
                unused.add(i);
            }
        }
        if (unused.isEmpty()) {
            return md;
        }

        List<Statement> kept = withoutIndices(params, unused);
        kept = kept.isEmpty() ?
                singletonList(new J.Empty(Tree.randomId(), Space.EMPTY, Markers.EMPTY)) :
                ListUtils.mapFirst(kept, p -> p.withPrefix(params.get(0).getPrefix()));
        md = md.withParameters(kept);
        JavaType.Method type = md.getMethodType();
        if (type == null || type.getParameterTypes().size() != params.size()) {
            return md;
        }
        return md.withMethodType(type
                .withParameterNames(withoutIndices(type.getParameterNames(), unused))
                .withParameterTypes(withoutIndices(type.getParameterTypes(), unused)));
    }

    private J.ClassDeclaration removeUnusedFactoryFields(J.ClassDeclaration cd) {
        Set<String> fieldNames = new HashSet<>();
        for (Statement statement : cd.getBody().getStatements()) {
            if (isOfType(statement, factoryFqn)) {
                for (J.VariableDeclarations.NamedVariable v : ((J.VariableDeclarations) statement).getVariables()) {
                    fieldNames.add(v.getSimpleName());
                }
            }
        }
        Set<String> unusedFields = new HashSet<>(fieldNames);
        unusedFields.removeAll(referencedFields(cd, fieldNames));
        if (unusedFields.isEmpty()) {
            return cd;
        }

        Set<String> removedFields = new HashSet<>();
        List<Statement> statements = ListUtils.map(cd.getBody().getStatements(), statement -> {
            if (isOfType(statement, factoryFqn) &&
                    ((J.VariableDeclarations) statement).getVariables().stream().allMatch(v -> unusedFields.contains(v.getSimpleName()))) {
                ((J.VariableDeclarations) statement).getVariables().forEach(v -> removedFields.add(v.getSimpleName()));
                return null;
            }
            return statement;
        });
        long constructorCount = statements.stream()
                .filter(s -> s instanceof J.MethodDeclaration && ((J.MethodDeclaration) s).isConstructor())
                .count();
        statements = ListUtils.map(statements, statement -> {
            if (!(statement instanceof J.MethodDeclaration) || !((J.MethodDeclaration) statement).isConstructor() ||
                    ((J.MethodDeclaration) statement).getBody() == null) {
                return statement;
            }
            J.MethodDeclaration constructor = (J.MethodDeclaration) statement;
            J.Block body = constructor.getBody();
            J.MethodDeclaration cleaned = constructor.withBody(body.withStatements(ListUtils.map(body.getStatements(),
                    s -> s instanceof J.Assignment && injectedField((J.Assignment) s, removedFields) != null ? null : s)));
            if (cleaned == constructor) {
                return constructor;
            }
            cleaned = removeUnusedFactoryParameters(cleaned);
            boolean empty = cleaned.getParameters().stream().allMatch(J.Empty.class::isInstance) &&
                    cleaned.getBody().getStatements().isEmpty();
            return empty && constructorCount == 1 ? null : cleaned;
        });
        return cd.withBody(cd.getBody().withStatements(statements));
    }

    /**
     * Names of the given fields that are referenced anywhere in the class, other than through
     * {@code this.field = parameter} assignments in constructors.
     */
    private static Set<String> referencedFields(J.ClassDeclaration cd, Set<String> fieldNames) {
        return new JavaIsoVisitor<Set<String>>() {
            @Override
            public J.Assignment visitAssignment(J.Assignment assignment, Set<String> referenced) {
                Cursor parent = getCursor().getParentTreeCursor();
                if (injectedField(assignment, fieldNames) != null &&
                        parent.getValue() instanceof J.Block &&
                        parent.getParentTreeCursor().getValue() instanceof J.MethodDeclaration &&
                        ((J.MethodDeclaration) parent.getParentTreeCursor().getValue()).isConstructor()) {
                    return assignment;
                }
                return super.visitAssignment(assignment, referenced);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> referenced) {
                Object parent = getCursor().getParentTreeCursor().getValue();
                boolean declaration = parent instanceof J.VariableDeclarations.NamedVariable &&
                        ((J.VariableDeclarations.NamedVariable) parent).getName() == identifier;
                if (!declaration && fieldNames.contains(identifier.getSimpleName()) &&
                        (identifier.getFieldType() == null || TypeUtils.isOfType(identifier.getFieldType().getOwner(), cd.getType()))) {
                    referenced.add(identifier.getSimpleName());
                }
                return identifier;
            }
        }.reduce(cd, new HashSet<>());
    }

    private static @Nullable String injectedField(J.Assignment assignment, Set<String> fieldNames) {
        if (!(assignment.getAssignment() instanceof J.Identifier)) {
            return null;
        }
        Expression variable = assignment.getVariable();
        String name = null;
        if (variable instanceof J.FieldAccess && ((J.FieldAccess) variable).getTarget() instanceof J.Identifier &&
                "this".equals(((J.Identifier) ((J.FieldAccess) variable).getTarget()).getSimpleName())) {
            name = ((J.FieldAccess) variable).getSimpleName();
        } else if (variable instanceof J.Identifier && ((J.Identifier) variable).getFieldType() != null &&
                ((J.Identifier) variable).getFieldType().getOwner() instanceof JavaType.FullyQualified) {
            name = ((J.Identifier) variable).getSimpleName();
        }
        return fieldNames.contains(name) ? name : null;
    }

    private static boolean referencesParameter(J.@Nullable Block body, String name) {
        if (body == null) {
            return false;
        }
        return new JavaIsoVisitor<AtomicBoolean>() {
            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean found) {
                // Field references share the name of the parameter but are owned by the class
                if (name.equals(identifier.getSimpleName()) &&
                        (identifier.getFieldType() == null || !(identifier.getFieldType().getOwner() instanceof JavaType.FullyQualified))) {
                    found.set(true);
                }
                return identifier;
            }
        }.reduce(body, new AtomicBoolean()).get();
    }

    private boolean isBean(J.MethodDeclaration md) {
        return service(AnnotationService.class).isAnnotatedWith(md, BEAN);
    }

    private static boolean isOfType(Statement statement, String fqn) {
        return statement instanceof J.VariableDeclarations &&
                TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(), fqn);
    }

    private static J.Identifier variableName(J.VariableDeclarations declarations) {
        return declarations.getVariables().get(0).getName().withId(Tree.randomId()).withPrefix(Space.EMPTY);
    }

    private static <T> List<T> withoutIndices(List<T> list, Set<Integer> indices) {
        List<T> kept = new ArrayList<>(list.size() - indices.size());
        for (int i = 0; i < list.size(); i++) {
            if (!indices.contains(i)) {
                kept.add(list.get(i));
            }
        }
        return kept;
    }
}
