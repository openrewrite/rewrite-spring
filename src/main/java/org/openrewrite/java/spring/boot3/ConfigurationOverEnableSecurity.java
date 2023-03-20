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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.*;

import static java.util.Collections.singletonList;

public class ConfigurationOverEnableSecurity extends Recipe {

    private static final String CONFIGURATION_FQN = "org.springframework.context.annotation.Configuration";

    private static final List<String> EXCLUSIONS = singletonList("org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity");

    private static final String ENABLE_SECURITY_ANNOTATION_PATTERN = "@org.springframework.security.config.annotation..*.Enable.*Security";

    private static final AnnotationMatcher SECURITY_ANNOTATION_MATCHER = new AnnotationMatcher(ENABLE_SECURITY_ANNOTATION_PATTERN, true);

    @Override
    public String getDisplayName() {
        return "Classes annotated with `@EnableXXXSecurity` coming from pre-Boot 3 project should have `@Configuration` annotation added";
    }

    @Override
    public String getDescription() {
        return "Annotations `@EnableXXXSecurity` have `@Configuration` removed from their definition in Spring-Security 6. " +
                "Consequently classes annotated with `@EnableXXXSecurity` coming from pre-Boot 3 should have `@Configuration` annotation added.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitJavaSourceFile(JavaSourceFile cu, ExecutionContext o) {
                for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
                    if(SECURITY_ANNOTATION_MATCHER.matchesAnnotationOrMetaAnnotation(TypeUtils.asFullyQualified(type))) {
                        return SearchResult.found(cu);
                    }
                }
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                // Avoid searching within the class declaration's body, lest we accidentally find an inner class's annotation
                //noinspection DataFlowIssue
                J.ClassDeclaration bodiless = c.withBody(null);
                if(FindAnnotations.find(bodiless, "@" + CONFIGURATION_FQN, true).size() > 0) {
                    return c;
                }
                Set<J.Annotation> securityAnnotations = FindAnnotations.find(bodiless,  ENABLE_SECURITY_ANNOTATION_PATTERN, true);
                if(securityAnnotations.size() == 0) {
                    return c;
                }
                if(securityAnnotations.stream()
                        .map(a -> TypeUtils.asFullyQualified(a.getType()))
                        .filter(Objects::nonNull)
                        .anyMatch(it -> EXCLUSIONS.contains(it.getFullyQualifiedName()))) {
                    return c;
                }

                JavaTemplate template = JavaTemplate.builder(this::getCursor, "@Configuration")
                        .imports(CONFIGURATION_FQN)
                        .javaParser(() -> JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-context-5.3.+")
                                .build())
                        .build();
                maybeAddImport(CONFIGURATION_FQN);

                return c.withTemplate(template, c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }
        };
    }
}
