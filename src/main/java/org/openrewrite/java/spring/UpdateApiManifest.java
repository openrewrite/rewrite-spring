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
package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Incubating(since = "4.12.0")
public class UpdateApiManifest extends Recipe {
    private static final List<AnnotationMatcher> REST_ENDPOINTS = Stream.of("Request", "Get", "Post", "Put", "Delete", "Patch")
            .map(method -> new AnnotationMatcher("@org.springframework.web.bind.annotation." + method + "Mapping"))
            .collect(toList());

    @Override
    public String getDisplayName() {
        return "Update the API manifest";
    }

    @Override
    public String getDescription() {
        return "Keep a consolidated manifest of the API endpoints that this application exposes up-to-date.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {

        List<String> apis = new ArrayList<>();

        for (SourceFile sourceFile : before) {
            new JavaVisitor<ExecutionContext>() {
                @Override
                public J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                    method.getAllAnnotations().stream()
                            .filter(this::hasRequestMapping)
                            .findAny()
                            .ifPresent(mapping -> {
                                String path =
                                        getCursor().getPathAsStream()
                                                .filter(J.ClassDeclaration.class::isInstance)
                                                .map(classDecl -> ((J.ClassDeclaration) classDecl).getAllAnnotations().stream()
                                                        .filter(this::hasRequestMapping)
                                                        .findAny()
                                                        .map(classMapping -> getArg(classMapping, "value", ""))
                                                        .orElse(null))
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.joining("/")) +
                                                getArg(mapping, "value", "");
                                path = path.replace("//", "/");

                                JavaType.FullyQualified type = TypeUtils.asFullyQualified(mapping.getType());
                                assert type != null;
                                String httpMethod = type.getClassName().startsWith("Request") ?
                                        getArg(mapping, "method", "GET") :
                                        type.getClassName().replace("Mapping", "").toUpperCase();
                                apis.add(httpMethod + " " + path);
                            });
                    return super.visitMethodDeclaration(method, executionContext);
                }

                private boolean hasRequestMapping(J.Annotation ann) {
                    for (AnnotationMatcher restEndpoint : REST_ENDPOINTS) {
                        if (restEndpoint.matches(ann)) {
                            return true;
                        }
                    }
                    return false;
                }
            }.visit(sourceFile, ctx);
        }

        List<SourceFile> after = ListUtils.map(before, sourceFile ->
                sourceFile.getSourcePath().equals(Paths.get("META-INF/api-manifest.txt")) ?
                        generateManifest(apis) : sourceFile);

        return after == before ? ListUtils.concat(before, generateManifest(apis)) : after;
    }

    private PlainText generateManifest(List<String> apis) {
        return new PlainTextParser()
                .parse(String.join("\n", apis))
                .get(0)
                .withSourcePath(Paths.get("META-INF/api-manifest.txt"));
    }

    private String getArg(J.Annotation annotation, String key, String defaultValue) {
        if (annotation.getArguments() != null) {
            for (Expression argument : annotation.getArguments()) {
                if (argument instanceof J.Literal) {
                    //noinspection ConstantConditions
                    return (String) ((J.Literal) argument).getValue();
                } else if (argument instanceof J.Assignment) {
                    J.Assignment arg = (J.Assignment) argument;
                    if (((J.Identifier) arg.getVariable()).getSimpleName().equals(key)) {
                        if (arg.getAssignment() instanceof J.FieldAccess) {
                            return ((J.FieldAccess) arg.getAssignment()).getSimpleName();
                        } else if (arg.getAssignment() instanceof J.Identifier) {
                            return ((J.Identifier) arg.getAssignment()).getSimpleName();
                        } else if (arg.getAssignment() instanceof J.Literal) {
                            //noinspection ConstantConditions
                            return (String) ((J.Literal) arg.getAssignment()).getValue();
                        }
                    }
                }
            }
        }
        return defaultValue;
    }
}
