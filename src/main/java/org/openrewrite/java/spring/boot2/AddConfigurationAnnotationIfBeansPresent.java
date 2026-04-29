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
package org.openrewrite.java.spring.boot2;

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class AddConfigurationAnnotationIfBeansPresent extends ScanningRecipe<Set<String>> {

    private static final String FQN_BEAN = "org.springframework.context.annotation.Bean";
    private static final String CONFIGURATION_PACKAGE = "org.springframework.context.annotation";
    private static final String CONFIGURATION_SIMPLE_NAME = "Configuration";
    private static final String FQN_CONFIGURATION = CONFIGURATION_PACKAGE + "." + CONFIGURATION_SIMPLE_NAME;
    private static final AnnotationMatcher BEAN_ANNOTATION_MATCHER = new AnnotationMatcher("@" + FQN_BEAN, true);
    private static final AnnotationMatcher CONFIGURATION_ANNOTATION_MATCHER = new AnnotationMatcher("@" + FQN_CONFIGURATION, true);

    // Annotations whose `configuration` / `defaultConfiguration` attributes declare a scoped,
    // non-global configuration class that should not be globally registered with `@Configuration`.
    private static final String FQN_FEIGN_CLIENT = "org.springframework.cloud.openfeign.FeignClient";
    private static final String FQN_ENABLE_FEIGN_CLIENTS = "org.springframework.cloud.openfeign.EnableFeignClients";
    private static final String FQN_LOAD_BALANCER_CLIENT = "org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient";
    private static final String FQN_LOAD_BALANCER_CLIENTS = "org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients";
    private static final String FQN_RIBBON_CLIENT = "org.springframework.cloud.netflix.ribbon.RibbonClient";
    private static final String FQN_RIBBON_CLIENTS = "org.springframework.cloud.netflix.ribbon.RibbonClients";

    @Getter
    final String displayName = "Add missing `@Configuration` annotation";

    @Getter
    final String description = "Class having `@Bean` annotation over any methods but missing `@Configuration` annotation over the declaring class would have `@Configuration` annotation added. " +
            "Classes referenced as scoped configuration via `.class` (e.g. `@FeignClient(configuration = X.class)`) are skipped to preserve their intended per-client scope.";

    @Override
    public Set<String> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<String> scopedConfigurationClasses) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                JavaType.FullyQualified annoType = TypeUtils.asFullyQualified(annotation.getType());
                if (annoType != null) {
                    String fqn = annoType.getFullyQualifiedName();
                    if (FQN_FEIGN_CLIENT.equals(fqn) || FQN_LOAD_BALANCER_CLIENT.equals(fqn) || FQN_RIBBON_CLIENT.equals(fqn)) {
                        collectClassLiterals(annotation, "configuration", scopedConfigurationClasses);
                    } else if (FQN_ENABLE_FEIGN_CLIENTS.equals(fqn) || FQN_LOAD_BALANCER_CLIENTS.equals(fqn) || FQN_RIBBON_CLIENTS.equals(fqn)) {
                        collectClassLiterals(annotation, "defaultConfiguration", scopedConfigurationClasses);
                    }
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };
    }

    private static void collectClassLiterals(J.Annotation annotation, String attributeName, Set<String> acc) {
        if (annotation.getArguments() == null) {
            return;
        }
        for (Expression arg : annotation.getArguments()) {
            if (arg instanceof J.Assignment) {
                J.Assignment assignment = (J.Assignment) arg;
                if (assignment.getVariable() instanceof J.Identifier &&
                        attributeName.equals(((J.Identifier) assignment.getVariable()).getSimpleName())) {
                    collectClassLiteralsFromExpression(assignment.getAssignment(), acc);
                }
            }
        }
    }

    private static void collectClassLiteralsFromExpression(Expression expr, Set<String> acc) {
        if (expr instanceof J.FieldAccess) {
            J.FieldAccess fa = (J.FieldAccess) expr;
            if ("class".equals(fa.getSimpleName())) {
                JavaType.FullyQualified fq = TypeUtils.asFullyQualified(fa.getTarget().getType());
                if (fq != null) {
                    acc.add(fq.getFullyQualifiedName());
                }
            }
        } else if (expr instanceof J.NewArray) {
            J.NewArray na = (J.NewArray) expr;
            if (na.getInitializer() != null) {
                for (Expression e : na.getInitializer()) {
                    collectClassLiteralsFromExpression(e, acc);
                }
            }
        }
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<String> scopedConfigurationClasses) {
        return Preconditions.check(new UsesType<>(FQN_BEAN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (isApplicableClass(c, getCursor()) && !isScopedConfiguration(c, scopedConfigurationClasses)) {
                    c = addConfigurationAnnotation(c);
                }
                return c;
            }

            boolean isApplicableClass(J.ClassDeclaration classDecl, Cursor cursor) {
                if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
                    return false;
                }

                boolean isStatic = false;
                for (J.Modifier m : classDecl.getModifiers()) {
                    if (m.getType() == J.Modifier.Type.Abstract) {
                        return false;
                    }
                    if (m.getType() == J.Modifier.Type.Static) {
                        isStatic = true;
                    }
                }

                if (!isStatic) {
                    // no static keyword? check if it is top level class in the CU
                    Object enclosing = cursor.dropParentUntil(it -> it instanceof J.ClassDeclaration || it == Cursor.ROOT_VALUE).getValue();
                    if (enclosing instanceof J.ClassDeclaration) {
                        return false;
                    }
                }

                // check if '@Configuration' is already over the class
                if (service(AnnotationService.class).matches(getCursor(), CONFIGURATION_ANNOTATION_MATCHER)) {
                    return false;
                }

                // No '@Configuration' present. Check if any methods have '@Bean' annotation
                for (Statement s : classDecl.getBody().getStatements()) {
                    if (s instanceof J.MethodDeclaration) {
                        if (isBeanMethod((J.MethodDeclaration) s)) {
                            return true;
                        }
                    }
                }

                return false;
            }


            private J.ClassDeclaration addConfigurationAnnotation(J.ClassDeclaration c) {
                maybeAddImport(FQN_CONFIGURATION);
                return JavaTemplate.builder("@" + CONFIGURATION_SIMPLE_NAME)
                        .imports(FQN_CONFIGURATION)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                "package " + CONFIGURATION_PACKAGE +
                                        "; public @interface " + CONFIGURATION_SIMPLE_NAME + " {}"))
                        .build().apply(
                                getCursor(),
                                c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                        );
            }
        });
    }

    private static boolean isScopedConfiguration(J.ClassDeclaration classDecl, Set<String> scopedConfigurationClasses) {
        JavaType.FullyQualified type = TypeUtils.asFullyQualified(classDecl.getType());
        return type != null && scopedConfigurationClasses.contains(type.getFullyQualifiedName());
    }

    private static boolean isBeanMethod(J.MethodDeclaration methodDecl) {
        for (J.Modifier m : methodDecl.getModifiers()) {
            if (m.getType() == J.Modifier.Type.Abstract || m.getType() == J.Modifier.Type.Static) {
                return false;
            }
        }
        for (J.Annotation a : methodDecl.getLeadingAnnotations()) {
            JavaType.FullyQualified aType = TypeUtils.asFullyQualified(a.getType());
            if (aType != null && BEAN_ANNOTATION_MATCHER.matchesAnnotationOrMetaAnnotation(aType)) {
                return true;
            }
        }
        return false;
    }
}
