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
import org.openrewrite.Option;
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

    @Option(displayName = "Force add `@Configuration`",
        description = "Force add `@Configuration` regardless current Boot version.",
        example = "true")
    private final boolean forceAddConfiguration;

    private static final String CONFIGURATION_FQN = "org.springframework.context.annotation.Configuration";

    private static final List<String> EXCLUSIONS = singletonList("org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity");

    private static final String ENABLE_SECURITY_ANNOTATION_PATTERN = "@org.springframework.security.config.annotation..*.Enable.*Security";

    private static final AnnotationMatcher SECURITY_ANNOTATION_MATCHER = new AnnotationMatcher(ENABLE_SECURITY_ANNOTATION_PATTERN, true);

    public ConfigurationOverEnableSecurity() {
        this.forceAddConfiguration = false;
    }

    public ConfigurationOverEnableSecurity(boolean forceAddConfiguration) {
        this.forceAddConfiguration = forceAddConfiguration;
    }

    @Override
    public String getDisplayName() {
        return "Add `@Configuration` to classes with `@EnableXXXSecurity` annotations";
    }

    @Override
    public String getDescription() {
        return "Prior to Spring Security 6, `@EnableXXXSecurity` implicitly had `@Configuration`. " +
                "`Configuration` was removed from the definitions of the `@EnableSecurity` definitions in Spring Security 6. " +
                "Consequently classes annotated with `@EnableXXXSecurity` coming from pre-Boot 3 should have `@Configuration` annotation added.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitJavaSourceFile(JavaSourceFile cu, ExecutionContext o) {
                for (JavaType type : cu.getTypesInUse().getTypesInUse()) {
                    if (SECURITY_ANNOTATION_MATCHER.matchesAnnotationOrMetaAnnotation(TypeUtils.asFullyQualified(type))) {
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

                Set<J.Annotation> securityAnnotations = FindAnnotations.find(bodiless,
                    ENABLE_SECURITY_ANNOTATION_PATTERN, true);
                if (securityAnnotations.isEmpty() || isExcluded(securityAnnotations)) {
                    return c;
                }

                boolean alreadyHasConfigurationAnnotation = !FindAnnotations.find(bodiless, "@" + CONFIGURATION_FQN,
                    false).isEmpty();
                if (alreadyHasConfigurationAnnotation) {
                    return c;
                }

                if (!forceAddConfiguration) {
                    J.Annotation securityAnnotation = securityAnnotations.stream().findFirst().get();
                    boolean securityAnnotationHasConfiguration = new AnnotationMatcher("@" + CONFIGURATION_FQN, true)
                        .matchesAnnotationOrMetaAnnotation(TypeUtils.asFullyQualified(securityAnnotation.getType()));

                    // The framework 6.+ (Boot 3+) removed `@Configuration` from `@EnableXXXSecurity`, so if it has not
                    // `@Configuration`, means it is already in version framework 6.+ (Boot 3+), and expected no change.
                    // Otherwise, we want to add `@Configuration`.
                    boolean isBoot3orPlus = !securityAnnotationHasConfiguration;
                    if (isBoot3orPlus) {
                        return c;
                    }
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

            private boolean isExcluded(Set<J.Annotation> securityAnnotations) {
                return (securityAnnotations.stream()
                    .map(a -> TypeUtils.asFullyQualified(a.getType()))
                    .filter(Objects::nonNull)
                    .anyMatch(it -> EXCLUSIONS.contains(it.getFullyQualifiedName())));
            }
        };
    }
}
