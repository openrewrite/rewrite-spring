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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeTree;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MigrateMethodAnnotatedByBatchAPI extends Recipe {


    @Override
    public String getDisplayName() {
        return "Migrate method when it annotated by Spring Batch API";
    }

    @Override
    public String getDescription() {
        return "Migrate method when it annotated by Spring Batch API.";

    }

    private static final Set<String> annotatedMethods = new HashSet<String>() {
        {
            add("org.springframework.batch.core.annotation.OnWriteError");
            add("org.springframework.batch.core.annotation.BeforeWrite");
            add("org.springframework.batch.core.annotation.AfterWrite");
        }
    };


    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                Optional<J. Annotation> methodAnnotation =  method.getLeadingAnnotations().stream()
                        .filter(annotation -> Objects.nonNull(annotation.getType()))
                        .filter(annotation -> annotatedMethods.contains(annotation.getType().toString()))
                        .findFirst();

                if(methodAnnotation.isPresent()) {
                    method = super.visitMethodDeclaration(method, ctx);
                    doAfterVisit(new RefineMethod(method));
                    return method;
                } else {
                    return super.visitMethodDeclaration(method, ctx);
                }

            }
        };
    }

    private static class RefineMethod extends JavaIsoVisitor<ExecutionContext> {

        J.MethodDeclaration method;

        public RefineMethod(J.MethodDeclaration method) {
            this.method = method;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if(method != this.method) {
                return super.visitMethodDeclaration(method, ctx);
            }
            Optional<J.VariableDeclarations> parameterOptional = method.getParameters().stream()
                    .filter(parameter-> parameter instanceof J.VariableDeclarations)
                    .map(parameter-> ((J.VariableDeclarations) parameter))
                    .filter(parameter-> parameter.getType().isAssignableFrom(Pattern.compile("java.util.List")))
                    .findFirst();
            if(!parameterOptional.isPresent()) {
                return super.visitMethodDeclaration(method, ctx);
            }
            J.VariableDeclarations parameter = parameterOptional.get();
            String chunkTypeParameter = null;
            if ((parameter.getTypeExpression() instanceof J.ParameterizedType)) {
                if(((J.ParameterizedType) parameter.getTypeExpression()).getTypeParameters() != null) {
                    chunkTypeParameter = ((J.ParameterizedType) parameter.getTypeExpression()).getTypeParameters().get(0).toString();
                } else {
                    chunkTypeParameter = "?";
                }
            }
            String chunkType = chunkTypeParameter == null ? "" : "<" + chunkTypeParameter + ">";
            String paramName = parameter.getVariables().get(0).getSimpleName();


            J.VariableDeclarations vdd = JavaTemplate.builder("Chunk" + chunkType + " _chunk")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .<J.MethodDeclaration>apply(getCursor(), method.getCoordinates().replaceParameters())
                    .getParameters().get(0).withPrefix( Space.EMPTY);
            vdd = vdd.withTypeExpression(TypeTree.build("org.springframework.batch.item.Chunk")).withType(JavaType.buildType("org.springframework.batch.item.Chunk"));

            J.MethodDeclaration methodDeclaration = JavaTemplate.builder("List"+chunkType+" #{} = _chunk.getItems();")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-5.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .apply(getCursor(), method.getBody().getCoordinates().firstStatement(), paramName);


            methodDeclaration = methodDeclaration.withParameters(Collections.singletonList(vdd))
                    .withMethodType(method.getMethodType()
                            .withParameterTypes(Collections.singletonList(vdd.getType())));



            method = JavaTemplate.builder("#{}\n #{} void write(#{} Chunk"+chunkType+" #{})#{} #{}")
                    .contextSensitive()
                    .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-batch-core-5.+", "spring-batch-infrastructure-5.+"))
                    .imports("org.springframework.batch.item.Chunk")
                    .build()
                    .apply(
                            getCursor(),
                            method.getCoordinates().replace(),
                             method.getLeadingAnnotations().stream().map(a->a.print(getCursor())).reduce((a1,a2)->a1 + "\n" + a2).orElse(""),
                            method.getModifiers().stream()
                                    .map(J.Modifier::toString)
                                    .collect(Collectors.joining(" ")),
                            parameter.getModifiers().stream()
                                    .map(J.Modifier::toString)
                                    .collect(Collectors.joining(" ")),
                            "_chunk",
                            Optional.ofNullable(method.getThrows()).flatMap(throwsList->throwsList.stream().map(Object::toString).reduce((a, b) -> a + ", " + b).map(e-> " throws " + e)).orElse(""),
                            method.getBody() == null ? "" :  methodDeclaration.getBody().print(getCursor()));
            maybeAddImport("org.springframework.batch.item.Chunk");
            return method;

        }
    }
}
