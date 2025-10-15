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

import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;

import java.util.*;
import java.util.regex.Pattern;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

public class MigrateMethodAnnotatedByBatchAPI extends Recipe {

    @Override
    public String getDisplayName() {
        return "Migrate method when it annotated by Spring Batch API";
    }

    @Override
    public String getDescription() {
        return "Migrate method when it annotated by Spring Batch API.";

    }

    private static final Set<String> annotatedMethods;
    static {
        annotatedMethods = new HashSet<String>();
        annotatedMethods.add("org.springframework.batch.core.annotation.OnWriteError");
        annotatedMethods.add("org.springframework.batch.core.annotation.BeforeWrite");
        annotatedMethods.add("org.springframework.batch.core.annotation.AfterWrite");
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaIsoVisitor<ExecutionContext> visitor = new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                Optional<J.Annotation> methodAnnotation = method.getLeadingAnnotations().stream()
                        .filter(annotation -> Objects.nonNull(annotation.getType()))
                        .filter(annotation -> annotatedMethods.contains(annotation.getType().toString()))
                        .findFirst();

                if (methodAnnotation.isPresent()) {
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
                    .filter(parameter -> parameter.getType().isAssignableFrom(Pattern.compile("java.util.List")))
                    .findFirst();
            if (!parameterOptional.isPresent()) {
                return super.visitMethodDeclaration(method, ctx);
            }
            J.VariableDeclarations parameter = parameterOptional.get();
            String chunkTypeParameter = null;
            if (parameter.getTypeExpression() instanceof J.ParameterizedType) {
                if (((J.ParameterizedType) parameter.getTypeExpression()).getTypeParameters() != null) {
                    chunkTypeParameter = ((J.ParameterizedType) parameter.getTypeExpression()).getTypeParameters().get(0).toString();
                } else {
                    chunkTypeParameter = "?";
                }
            }
            String chunkType = chunkTypeParameter == null ? "" : "<" + chunkTypeParameter + ">";
            String paramName = parameter.getVariables().get(0).getSimpleName();


            J.VariableDeclarations vdd = JavaTemplate.builder("Chunk" + chunkType + " _chunk")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.1.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .apply(getCursor(), method.getCoordinates().replaceParameters())
                    .getParameters().get(0).withPrefix(Space.EMPTY);
            vdd = vdd.withTypeExpression(TypeTree.build("org.springframework.batch.item.Chunk")).withType(JavaType.buildType("org.springframework.batch.item.Chunk"));

            J.MethodDeclaration methodDeclaration = JavaTemplate.builder("List" + chunkType + " #{} = _chunk.getItems();")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.1.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .apply(getCursor(), method.getBody().getCoordinates().firstStatement(), paramName);


            methodDeclaration = methodDeclaration.withParameters(singletonList(vdd))
                    .withMethodType(method.getMethodType()
                            .withParameterTypes(singletonList(vdd.getType())));


            maybeAddImport("org.springframework.batch.item.Chunk");
            String annotations = method.getLeadingAnnotations().stream().map(a -> a.print(getCursor())).reduce((a1, a2) -> a1 + "\n" + a2).orElse("");
            String methodModifiers = method.getModifiers().stream()
                    .map(J.Modifier::toString)
                    .collect(joining(" "));
            String parameterModifiers = parameter.getModifiers().stream()
                    .map(J.Modifier::toString)
                    .collect(joining(" "));
            String throwz = Optional.ofNullable(method.getThrows()).flatMap(throwsList -> throwsList.stream().map(Object::toString).reduce((a, b) -> a + ", " + b).map(e -> " throws " + e)).orElse("");
            String body = method.getBody() == null ? "" : methodDeclaration.getBody().print(getCursor());
            return JavaTemplate.builder(String.format("%s\n %s void write(%s Chunk" + chunkType + " %s)%s %s", annotations, methodModifiers, parameterModifiers, "_chunk", throwz, body))
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace());
        }
    }
}
