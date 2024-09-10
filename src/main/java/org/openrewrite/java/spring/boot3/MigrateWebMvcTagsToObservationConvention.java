/*
 * Copyright 2024 the original author or authors.
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

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;

import static java.util.Collections.singletonList;

public class MigrateWebMvcTagsToObservationConvention extends Recipe {

    private static final String WEBMVCTAGSPROVIDER_FQ = "org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider";
    private static final String DEFAULTSERVERREQUESTOBSERVATIONCONVENTION = "DefaultServerRequestObservationConvention";
    private static final String DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ = "org.springframework.http.server.observation.DefaultServerRequestObservationConvention";
    private static final String SERVERREQUESTOBSERVATIONCONVENTION_FQ = "org.springframework.http.server.observation.ServerRequestObservationContext";
    private static final String KEYVALUES_FQ = "io.micrometer.common.KeyValues";
    private static final String KEYVALUE_FQ = "io.micrometer.common.KeyValue";
    private static final String HTTPSERVLETREQUEST_FQ = "jakarta.servlet.http.HttpServletRequest";
    private static final String HTTPSERVLETRESPONSE_FQ = "jakarta.servlet.http.HttpServletResponse";
    private static final MethodMatcher TAGS_AND = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.String, java.lang.String)");

    private static boolean addedHttpServletRequest;
    private static boolean addedHttpServletResponse;

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Migrate `WebMvcTagsProvider` to `DefaultServerRequestObservationConvention`";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Migrate `WebMvcTagsProvider` to `DefaultServerRequestObservationConvention` as part of Spring Boot 3.2 removals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(WEBMVCTAGSPROVIDER_FQ, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;
                if (classDecl.getImplements() != null) {
                    for (TypeTree type : classDecl.getImplements()) {
                        if (TypeUtils.isOfClassType(type.getType(), WEBMVCTAGSPROVIDER_FQ)) {
                            maybeRemoveImport(WEBMVCTAGSPROVIDER_FQ);
                            maybeAddImport(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ);
                            c = classDecl.withImplements(null)
                                    .withExtends(TypeTree.build(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION)
                                            .withType(JavaType.buildType(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ))
                                            .withPrefix(Space.SINGLE_SPACE));
                            c = super.visitClassDeclaration(c, ctx);
                        }
                    }
                }
                maybeRemoveImport(HTTPSERVLETREQUEST_FQ);
                maybeRemoveImport(HTTPSERVLETRESPONSE_FQ);
                return maybeAutoFormat(classDecl, c, ctx, getCursor());
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                boolean isOverride = false;
                for (J.Annotation anno : m.getLeadingAnnotations()) {
                    if (TypeUtils.isOfType(anno.getType(), JavaType.buildType("java.lang.Override"))) {
                        isOverride = true;
                    }
                }
                if (isOverride) {
                    if (method.getName().getSimpleName().equals("getTags")) {
                        J.VariableDeclarations methodArg = JavaTemplate.builder("ServerRequestObservationContext context")
                                .contextSensitive()
                                .imports(SERVERREQUESTOBSERVATIONCONVENTION_FQ)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web-6.+"))
                                .build()
                                .<J.MethodDeclaration>apply(getCursor(), method.getCoordinates().replaceParameters())
                                .getParameters().get(0).withPrefix(Space.EMPTY);

                        Statement keyValuesInitializer = JavaTemplate.builder("KeyValues values = super.getLowCardinalityKeyValues(context);")
                                .contextSensitive()
                                .imports(KEYVALUES_FQ)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                .build()
                                .<J.MethodDeclaration>apply(getCursor(), method.getBody().getCoordinates().firstStatement())
                                .getBody().getStatements().get(0);

                        maybeAddImport(SERVERREQUESTOBSERVATIONCONVENTION_FQ);
                        maybeAddImport(KEYVALUES_FQ);
                        m = m.withName(m.getName().withSimpleName("getLowCardinalityKeyValues"))
                                .withReturnTypeExpression(TypeTree.build("KeyValues").withType(JavaType.buildType(KEYVALUES_FQ)))
                                .withParameters(singletonList(methodArg))
                                .withBody(m.getBody().withStatements(ListUtils.insert(m.getBody().getStatements(), keyValuesInitializer, 0)));
                    }
                }
                Boolean addHttpServletRequest = getCursor().pollMessage("addHttpServletRequest");
                Boolean addHttpServletResponse = getCursor().pollMessage("addHttpServletResponse");
                if (Boolean.TRUE.equals(addHttpServletRequest)) {
                    m = JavaTemplate.builder("HttpServletRequest request = context.get(HttpServletRequest.class);")
                            .imports(HTTPSERVLETREQUEST_FQ)
                            .javaParser(JavaParser.fromJavaVersion().classpath("tomcat-embed-core"))
                            .build()
                            .apply(updateCursor(m), m.getBody().getCoordinates().firstStatement());
                    addedHttpServletRequest = true;
                }
                if (Boolean.TRUE.equals(addHttpServletResponse)) {
                    m = JavaTemplate.builder("HttpServletResponse response = context.get(HttpServletResponse.class);")
                            .imports(HTTPSERVLETRESPONSE_FQ)
                            .javaParser(JavaParser.fromJavaVersion().classpath("tomcat-embed-core"))
                            .build()
                            .apply(updateCursor(m), m.getBody().getCoordinates().firstStatement());
                    addedHttpServletResponse = true;
                }
                return m;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (method.getMethodType() != null && TypeUtils.isOfType(method.getMethodType().getDeclaringType(), JavaType.buildType(HTTPSERVLETREQUEST_FQ)) && !addedHttpServletRequest) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "addHttpServletRequest", true);
                }
                if (method.getMethodType() != null && TypeUtils.isOfType(method.getMethodType().getDeclaringType(), JavaType.buildType(HTTPSERVLETRESPONSE_FQ)) && !addedHttpServletResponse) {
                    getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "addHttpServletResponse", true);
                }
                if (TAGS_AND.matches(method)) {
                    maybeAddImport(KEYVALUE_FQ);
                    m = JavaTemplate.builder("KeyValue.of(#{any(java.lang.String)}, #{any(java.lang.String)})")
                            .imports(KEYVALUE_FQ)
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                            .build()
                            .apply(getCursor(), m.getCoordinates().replace(), method.getArguments().get(0), method.getArguments().get(1));
                }
                return m;
            }
        });
    }
}