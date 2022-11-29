/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Alex Boyko
 */
public class WebSecurityConfigurerAdapter extends Recipe {

    private static final Collection<J.Modifier.Type> EXPLICIT_ACCESS_LEVELS = Arrays.asList(J.Modifier.Type.Public,
            J.Modifier.Type.Private, J.Modifier.Type.Protected);

    private static final String FQN_CONFIGURATION = "org.springframework.context.annotation.Configuration";
    private static final String FQN_WEB_SECURITY_CONFIGURER_ADAPTER = "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter";
    private static final String FQN_SECURITY_FILTER_CHAIN = "org.springframework.security.web.SecurityFilterChain";
    private static final String FQN_OVERRIDE = "java.lang.Override";
    private static final String FQN_WEB_SECURITY_CUSTOMIZER = "org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer";
    private static final String BEAN_PKG = "org.springframework.context.annotation";
    private static final String BEAN_SIMPLE_NAME = "Bean";
    private static final String FQN_BEAN = BEAN_PKG + "." + BEAN_SIMPLE_NAME;
    private static final String BEAN_ANNOTATION = "@" + BEAN_SIMPLE_NAME;

    private static final MethodMatcher CONFIGURE_HTTP_SECURITY_METHOD_MATCHER =
            new MethodMatcher("org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter configure(org.springframework.security.config.annotation.web.builders.HttpSecurity)", true);
    private static final MethodMatcher CONFIGURE_WEB_SECURITY_METHOD_MATCHER =
            new MethodMatcher("org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter configure(org.springframework.security.config.annotation.web.builders.WebSecurity)", true);
    private static final MethodMatcher CONFIGURE_AUTH_MANAGER_SECURITY_METHOD_MATCHER =
            new MethodMatcher("org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter configure(org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder)", true);

    private static final MethodMatcher USER_DETAILS_SERVICE_BEAN_METHOD_MATCHER =
            new MethodMatcher("org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter  userDetailsServiceBean()", true);
    private static final MethodMatcher AUTHENTICATION_MANAGER_BEAN_METHOD_MATCHER =
            new MethodMatcher("org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter  authenticationManagerBean()", true);

    private static final String HAS_CONFLICT = "has-conflict";

    @Override
    public String getDisplayName() {
        return "Spring Security 5.4 introduces the ability to configure HttpSecurity by creating a SecurityFilterChain bean";
    }

    @Override
    public String getDescription() {
        return "The Spring-Security WebSecurityConfigurerAdapter was deprecated 5.7, this recipe will transform `WebSecurityConfigurerAdapter` classes by using a component based approach. Check out the [spring-security-without-the-websecurityconfigureradapter](https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter) blog for more details.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getApplicableTest() {
        return new UsesType<>(FQN_WEB_SECURITY_CONFIGURER_ADAPTER);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                if (TypeUtils.isAssignableTo(FQN_WEB_SECURITY_CONFIGURER_ADAPTER, classDecl.getType())
                        && classDecl.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), FQN_CONFIGURATION))) {
                    boolean hasConflict = false;
                    for (JavaType.Method method : classDecl.getType().getMethods()) {
                        if (isConflictingMethod(method, method.getName())) {
                            hasConflict = true;
                        }
                    }
                    getCursor().putMessage(HAS_CONFLICT, hasConflict);
                    if (!hasConflict) {
                        maybeRemoveImport(FQN_WEB_SECURITY_CONFIGURER_ADAPTER);
                        classDecl = classDecl.withExtends(null);
                    }
                }
                return super.visitClassDeclaration(classDecl, context);
            }

            private boolean isConflictingMethod(@Nullable JavaType.Method methodType, String methodName) {
                return (methodType == null && "authenticationManagerBean".equals(methodName) || "userDetailsServiceBean".equals(methodName)) ||
                        (USER_DETAILS_SERVICE_BEAN_METHOD_MATCHER.matches(methodType)
                        || AUTHENTICATION_MANAGER_BEAN_METHOD_MATCHER.matches(methodType)
                        || CONFIGURE_AUTH_MANAGER_SECURITY_METHOD_MATCHER.matches(methodType));
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext context) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, context);
                Cursor classCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                if (isConflictingMethod(m.getMethodType(), method.getSimpleName())) {
                    m = SearchResult.found(m, "Migrate manually based on https://spring.io/blog/2022/02/21/spring-security-without-the-websecurityconfigureradapter");
                } else if (!classCursor.getMessage(HAS_CONFLICT, true)) {
                    if (CONFIGURE_HTTP_SECURITY_METHOD_MATCHER.matches(m, classCursor.getValue())) {
                        JavaType securityChainType = JavaType.buildType(FQN_SECURITY_FILTER_CHAIN);
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName("filterChain").withReturnType(securityChainType);
                        }

                        Space returnPrefix = m.getReturnTypeExpression() == null ? Space.EMPTY : m.getReturnTypeExpression().getPrefix();
                        m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), anno -> {
                                    if (TypeUtils.isOfClassType(anno.getType(), FQN_OVERRIDE)) {
                                        maybeRemoveImport(FQN_OVERRIDE);
                                        return null;
                                    }
                                    return anno;
                                }))
                                .withReturnTypeExpression(new J.Identifier(Tree.randomId(), returnPrefix, Markers.EMPTY,"SecurityFilterChain", securityChainType, null))
                                .withName(m.getName().withSimpleName("filterChain"))
                                .withMethodType(type)
                                .withModifiers(ListUtils.map(m.getModifiers(), (modifier) -> EXPLICIT_ACCESS_LEVELS.contains(modifier.getType()) ? null : modifier));

                        m = addBeanAnnotation(m, getCursor());
                        maybeAddImport(FQN_SECURITY_FILTER_CHAIN);
                    } else if (CONFIGURE_WEB_SECURITY_METHOD_MATCHER.matches(m, classCursor.getValue())) {
                        JavaType securityCustomizerType = JavaType.buildType(FQN_WEB_SECURITY_CUSTOMIZER);
                        JavaType.Method type = m.getMethodType();
                        if (type != null) {
                            type = type.withName("webSecurityCustomizer").withReturnType(securityCustomizerType);
                        }
                        Space returnPrefix = m.getReturnTypeExpression() == null ? Space.EMPTY : m.getReturnTypeExpression().getPrefix();
                        m = m.withLeadingAnnotations(ListUtils.map(m.getLeadingAnnotations(), anno -> {
                                    if (TypeUtils.isOfClassType(anno.getType(), FQN_OVERRIDE)) {
                                        maybeRemoveImport(FQN_OVERRIDE);
                                        return null;
                                    }
                                    return anno;
                                }))
                                .withMethodType(type)
                                .withParameters(Collections.emptyList())
                                .withReturnTypeExpression(new J.Identifier(Tree.randomId(), returnPrefix, Markers.EMPTY,"WebSecurityCustomizer", securityCustomizerType, null))
                                .withName(m.getName().withSimpleName("webSecurityCustomizer"))
                                .withModifiers(ListUtils.map(m.getModifiers(), (modifier) -> EXPLICIT_ACCESS_LEVELS.contains(modifier.getType()) ? null : modifier));

                        m = addBeanAnnotation(m, getCursor());
                        maybeAddImport(FQN_WEB_SECURITY_CUSTOMIZER);
                    }
                }
                return m;
            }

            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext context) {
                J.Block b = super.visitBlock(block, context);
                if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.MethodDeclaration) {
                    J.MethodDeclaration parentMethod = getCursor().getParent().getValue();
                    Cursor classDeclCursor = getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance);
                    J.ClassDeclaration classDecl = classDeclCursor.getValue();
                    if (!classDeclCursor.getMessage(HAS_CONFLICT, true)) {
                        if (CONFIGURE_HTTP_SECURITY_METHOD_MATCHER.matches(parentMethod, classDecl)) {
                            JavaTemplate template = JavaTemplate.builder(this::getCursor,  "return #{any(org.springframework.security.config.annotation.SecurityBuilder)}.build();")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn("package org.springframework.security.config.annotation;" +
                                                    "public interface SecurityBuilder<O> {\n" +
                                                    "    O build() throws Exception;" +
                                                    "}")
                                            .build()).imports("org.springframework.security.config.annotation.SecurityBuilder").build();
                            b = b.withTemplate(template, b.getCoordinates().lastStatement(),
                                    ((J.VariableDeclarations)parentMethod.getParameters().get(0)).getVariables().get(0).getName());
                        } else if (CONFIGURE_WEB_SECURITY_METHOD_MATCHER.matches(parentMethod, classDecl)) {
                            String t = "return (" + ((J.VariableDeclarations)parentMethod.getParameters().get(0)).getVariables().get(0).getName().getSimpleName() + ") -> #{any()};";
                            JavaTemplate template = JavaTemplate.builder(this::getCursor, t).javaParser(() -> JavaParser.fromJavaVersion().build()).build();
                            b = b.withTemplate(template, b.getCoordinates().firstStatement(), b);
                            b = b.withStatements(ListUtils.map(b.getStatements(), (index, stmt) -> {
                                if (index == 0){
                                    return stmt;
                                }
                                return null;
                            }));
                        }
                    }
                }
                return b;
            }

            private J.MethodDeclaration addBeanAnnotation(J.MethodDeclaration m, Cursor c) {
                maybeAddImport(FQN_BEAN);
                JavaTemplate template = JavaTemplate.builder(() -> c, BEAN_ANNOTATION).imports(FQN_BEAN).javaParser(() -> JavaParser.fromJavaVersion()
                        .dependsOn("package " + BEAN_PKG + "; public @interface " + BEAN_SIMPLE_NAME + " {}").build()).build();
                return m.withTemplate(template, m.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
            }

        };
    }
}
