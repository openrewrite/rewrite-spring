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
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class ConfigurationOverEnableSecurity extends Recipe {

    private static final String CONFIGURATION_PACKAGE = "org.springframework.context.annotation";
    private static final String CONFIGURATION_SIMPLE_NAME = "Configuration";

    private static final String FQN_CONFIGURATION = "org.springframework.context.annotation.Configuration";

    private static List<String> EXCLUSIONS = Arrays.asList(new String[] {
       "org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity"
    });

    private static final String ENABLE_SECURITY_ANNOTATION_PATTERN = "org.springframework.security.config.annotation..*.Enable.*Security";

    @Override
    public String getDisplayName() {
        return "Classes annotated with '@EnableXXXSecurity' coming from pre-Boot 3 project should have @Configuration annotation added";
    }

    @Override
    public String getDescription() {
        return "Annotations '@EnableXXXSecurity' have '@Configuration' removed from their definition in Spring-Security 6. Consequently classes annotated with '@EnableXXXSecurity' coming from pre-Boot 3 should have '@Configuration' annotation added.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);
                AnnotationMatcher securityMatcher = new AnnotationMatcher(ENABLE_SECURITY_ANNOTATION_PATTERN, true);
                AnnotationMatcher configMatcher = new AnnotationMatcher(CONFIGURATION_PACKAGE + "." + CONFIGURATION_SIMPLE_NAME, true);
                boolean hasEnableSecurity = false;
                boolean hasConfiguration = false;
                for (Iterator<J.Annotation> itr = c.getLeadingAnnotations().iterator(); itr.hasNext() && !hasEnableSecurity && !hasConfiguration;) {
                    J.Annotation a = itr.next();
                    JavaType.FullyQualified annotationType = TypeUtils.asFullyQualified(a.getType());
                    if (annotationType != null) {
                        if (!hasEnableSecurity && !EXCLUSIONS.contains(annotationType.getFullyQualifiedName())) {
                            hasEnableSecurity = securityMatcher.matchesAnnotationOrMetaAnnotation(annotationType);
                        }
                        if (!hasConfiguration) {
                            hasConfiguration = configMatcher.matchesAnnotationOrMetaAnnotation(annotationType);
                        }
                    }
                }
                if (hasEnableSecurity && !hasConfiguration) {
                    return addConfigurationAnnotation(c);
                }
                return c;
            }

            private J.ClassDeclaration addConfigurationAnnotation(J.ClassDeclaration c) {
                String configurationFqn = CONFIGURATION_PACKAGE + "." + CONFIGURATION_SIMPLE_NAME;
                maybeAddImport(configurationFqn);
                JavaTemplate template = JavaTemplate.builder(() -> getCursor(), "@" + CONFIGURATION_SIMPLE_NAME).imports(configurationFqn).javaParser(() -> JavaParser.fromJavaVersion()
                        .dependsOn("package " + CONFIGURATION_PACKAGE + "; public @interface " + CONFIGURATION_SIMPLE_NAME + " {}").build()).build();
                return c.withTemplate(template, c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

        };
    }
}
