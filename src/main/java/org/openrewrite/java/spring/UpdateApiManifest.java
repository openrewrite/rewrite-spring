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

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.text.PlainTextVisitor;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Incubating(since = "4.12.0")
public class UpdateApiManifest extends ScanningRecipe<UpdateApiManifest.ApiManifest> {
    private static final List<AnnotationMatcher> REST_ENDPOINTS = Stream.of("Request", "Get", "Post", "Put", "Delete", "Patch")
            .map(method -> new AnnotationMatcher("@org.springframework.web.bind.annotation." + method + "Mapping"))
            .collect(Collectors.toList());

    @Override
    public String getDisplayName() {
        return "Update the API manifest";
    }

    @Override
    public String getDescription() {
        return "Keep a consolidated manifest of the API endpoints that this application exposes up-to-date.";
    }

    @Override
    public ApiManifest getInitialValue(ExecutionContext ctx) {
        return new ApiManifest();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(ApiManifest acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof JavaSourceFile) {
                    new SpringHttpEndpointCollector().visit(tree, acc.getApis());
                } else if (tree instanceof PlainText && ((PlainText) tree).getSourcePath().equals(Paths.get("META-INF/api-manifest.txt"))) {
                    acc.setGenerate(false);
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<SourceFile> generate(ApiManifest acc, ExecutionContext ctx) {
        return acc.isGenerate() ? Collections.singletonList(generateManifest(acc.getApis())) : Collections.emptyList();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(ApiManifest acc) {
        return Preconditions.check(!acc.isGenerate(), new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                if (text.getSourcePath().equals(Paths.get("META-INF/api-manifest.txt"))) {
                    return text.withText(generateManifest(acc.getApis()).getText());
                }
                return text;
            }
        });
    }

    private PlainText generateManifest(List<String> apis) {
        //noinspection OptionalGetWithoutIsPresent
        return new PlainTextParser()
                .parse(String.join("\n", apis))
                .findFirst()
                .get()
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

    @Data
    static class ApiManifest {
        boolean generate = true;
        List<String> apis = new ArrayList<>();
    }

    private class SpringHttpEndpointCollector extends JavaIsoVisitor<List<String>> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, List<String> apis) {
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
            return method;
        }

        private boolean hasRequestMapping(J.Annotation ann) {
            for (AnnotationMatcher restEndpoint : REST_ENDPOINTS) {
                if (restEndpoint.matches(ann)) {
                    return true;
                }
            }
            return false;
        }
    }
}
