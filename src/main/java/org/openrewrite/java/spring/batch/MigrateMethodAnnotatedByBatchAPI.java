/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.VariableNameUtils;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.internal.NamedTypes;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.VariableNameUtils.GenerationStrategy.INCREMENT_NUMBER;

public class MigrateMethodAnnotatedByBatchAPI extends Recipe {

    @Getter
    final String displayName = "Migrate method when it annotated by Spring Batch API";

    @Getter
    final String description = "Migrate method when it annotated by Spring Batch API.";

    private static final Set<String> annotatedMethods = new HashSet<String>() {
        {
            add("org.springframework.batch.core.annotation.OnWriteError");
            add("org.springframework.batch.core.annotation.BeforeWrite");
            add("org.springframework.batch.core.annotation.AfterWrite");
        }
    };

    private static final List<AnnotationMatcher> ANNOTATION_MATCHERS = annotatedMethods.stream()
            .map(annotation -> new AnnotationMatcher("@" + annotation))
            .collect(toList());

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                boolean annotated = method.getLeadingAnnotations().stream()
                        .anyMatch(annotation -> ANNOTATION_MATCHERS.stream()
                                .anyMatch(matcher -> matcher.matches(annotation)));

                if (annotated) {
                    method = super.visitMethodDeclaration(method, ctx);
                    doAfterVisit(new RefineMethod(method));
                    return method;
                }
                return super.visitMethodDeclaration(method, ctx);

            }
        };
        return Preconditions.check(new UsesType<>("org.springframework.batch.core.annotation.*", true), visitor);
    }

    @RequiredArgsConstructor
    private static class RefineMethod extends JavaIsoVisitor<ExecutionContext> {

        final J.MethodDeclaration method;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (method != this.method) {
                return super.visitMethodDeclaration(method, ctx);
            }
            Optional<J.VariableDeclarations> parameterOptional = method.getParameters().stream()
                    .filter(J.VariableDeclarations.class::isInstance)
                    .map(parameter -> ((J.VariableDeclarations) parameter))
                    .filter(parameter -> TypeUtils.isAssignableTo("java.util.List", parameter.getType()))
                    .findFirst();
            if (!parameterOptional.isPresent()) {
                return super.visitMethodDeclaration(method, ctx);
            }
            J.VariableDeclarations parameter = parameterOptional.get();
            String[] namedTypes = NamedTypes.in(method);
            String chunkName = VariableNameUtils.generateVariableName("chunk", getCursor(), INCREMENT_NUMBER);
            maybeAddImport("org.springframework.batch.item.Chunk");
            maybeAddImport("java.util.List");

            // To convert List<T> items -> Chunk<T> chunk
            J.Identifier chunk = TypeTree.<J.Identifier>build("Chunk")
                    .withType(JavaType.buildType("org.springframework.batch.item.Chunk"));
            TypeTree listType = parameter.getTypeExpression();
            J.VariableDeclarations chunkParam = parameter
                    .withTypeExpression(listType instanceof J.ParameterizedType
                            ? ((J.ParameterizedType) listType).withClazz(chunk)
                            : chunk.withPrefix(listType == null ? Space.EMPTY : listType.getPrefix()))
                    .withVariables(parameter.getVariables().stream()
                            .map(v -> v.withName(v.getName().withSimpleName(chunkName)))
                            .collect(toList()));

            List<Object> parameters = method.getParameters().stream()
                    .map(p -> (p == parameter ? chunkParam : p).withPrefix(Space.EMPTY))
                    .collect(toList());
            J.MethodDeclaration migrated = template(parameters.stream()
                    .map(p -> "#{}")
                    .collect(joining(", ")), namedTypes, ctx)
                    .apply(getCursor(), method.getCoordinates().replaceParameters(), parameters.toArray());

            // For first statement of body List<T> items = chunk.getItems();
            // Ignore for no body case -> void foo(...);
            if (migrated.getBody() != null) {
                J.VariableDeclarations localVariable = parameter
                        .withLeadingAnnotations(emptyList())
                        .withModifiers(emptyList())
                        .withPrefix(Space.EMPTY);
                migrated = template("#{} = " + chunkName + ".getItems();", namedTypes, ctx)
                        .apply(updateCursor(migrated), migrated.getBody().getCoordinates().firstStatement(), localVariable);
            }
            return autoFormat(migrated, ctx, getCursor().getParentOrThrow());
        }

        private static JavaTemplate template(String code, String[] namedTypes, ExecutionContext ctx) {
            return JavaTemplate.builder(code)
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                    .imports("org.springframework.batch.item.Chunk", "java.util.List")
                    .imports(namedTypes)
                    .build();
        }
    }
}
