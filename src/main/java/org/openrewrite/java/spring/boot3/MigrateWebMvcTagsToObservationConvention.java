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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MigrateWebMvcTagsToObservationConvention extends Recipe {

    private static final String WEBMVCTAGSPROVIDER_FQ = "org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider";
    private static final String WEBMVCTAGS_FQ = "org.springframework.boot.actuate.metrics.web.servlet.WebMvcTags";
    private static final String DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ = "org.springframework.http.server.observation.DefaultServerRequestObservationConvention";
    private static final String SERVERREQUESTOBSERVATIONCONVENTION_FQ = "org.springframework.http.server.observation.ServerRequestObservationContext";
    private static final String KEYVALUES_FQ = "io.micrometer.common.KeyValues";
    private static final String KEYVALUE_FQ = "io.micrometer.common.KeyValue";
    private static final String HTTPSERVLETREQUEST_FQ = "jakarta.servlet.http.HttpServletRequest";
    private static final String HTTPSERVLETRESPONSE_FQ = "jakarta.servlet.http.HttpServletResponse";
    private static final String TAGS_FQ = "io.micrometer.core.instrument.Tags";
    private static final String TAG_FQ = "io.micrometer.core.instrument.Tag";
    private static final MethodMatcher TAGS_AND_STRING_STRING = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.String, java.lang.String)");
    private static final MethodMatcher TAGS_AND_STRING_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.String[])");
    private static final MethodMatcher TAGS_AND_TAG_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.Iterable)");
    private static final MethodMatcher TAGS_OF_STRING_STRING = new MethodMatcher("io.micrometer.core.instrument.Tags of(java.lang.String, java.lang.String)");
    private static final MethodMatcher TAGS_OF_STRING_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags of(java.lang.String[])");
    private static final MethodMatcher TAGS_OF_TAG_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags of(io.micrometer.core.instrument.Tag[])");

    @Override
    public String getDisplayName() {
        return "Migrate `WebMvcTagsProvider` to `DefaultServerRequestObservationConvention`";
    }

    @Override
    public String getDescription() {
        return "Migrate `WebMvcTagsProvider` to `DefaultServerRequestObservationConvention` as part of Spring Boot 3.2 removals.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(WEBMVCTAGSPROVIDER_FQ, true), new JavaVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                List<Statement> getTagsBodyStatements = new ArrayList<>();
                boolean addHttpServletRequest = false;
                boolean addHttpServletResponse = false;
                for (Statement stmt : classDecl.getBody().getStatements()) {
                    if (stmt instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration) stmt;
                        if (md.getSimpleName().equals("getTags") && md.getBody() != null) {
                            getTagsBodyStatements.addAll(md.getBody().getStatements().subList(0, md.getBody().getStatements().size() - 1));
                            addHttpServletRequest = Boolean.TRUE.equals(getCursor().pollMessage("addHttpServletRequest"));
                            addHttpServletResponse = Boolean.TRUE.equals(getCursor().pollMessage("addHttpServletResponse"));
                            break;
                        }
                    }
                }

                StringBuilder tmpl = new StringBuilder();
                tmpl.append(String.format("class %s extends DefaultServerRequestObservationConvention {\n", classDecl.getSimpleName()));
                tmpl.append("    @Override\n");
                tmpl.append("    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {\n");
                if (addHttpServletRequest) {
                    tmpl.append("        HttpServletRequest request = context.getCarrier();\n");
                }
                if (addHttpServletResponse) {
                    tmpl.append("        HttpServletResponse response = context.getResponse();\n");
                }
                tmpl.append("        KeyValues values = super.getLowCardinalityKeyValues(context);\n");
                tmpl.append("        return values;");
                tmpl.append("    }\n");
                tmpl.append("}");
                J.ClassDeclaration newClassDeclaration = JavaTemplate.builder(tmpl.toString())
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons", "spring-web-6.+", "jakarta.servlet-api"))
                        .imports(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ, KEYVALUES_FQ, HTTPSERVLETREQUEST_FQ, HTTPSERVLETRESPONSE_FQ, SERVERREQUESTOBSERVATIONCONVENTION_FQ)
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().replace());

                J.ClassDeclaration finalNewClassDeclaration = newClassDeclaration;
                newClassDeclaration = newClassDeclaration
                        .withLeadingAnnotations(classDecl.getLeadingAnnotations())
                        .withModifiers(classDecl.getModifiers())
                        .withPrefix(classDecl.getPrefix())
                        .withBody(newClassDeclaration.getBody().withStatements(ListUtils.map(classDecl.getBody().getStatements(), stmt -> {
                            if (stmt instanceof J.MethodDeclaration && ((J.MethodDeclaration) stmt).getSimpleName().equals("getTags")) {
                                J.MethodDeclaration md = (J.MethodDeclaration) finalNewClassDeclaration.getBody().getStatements().get(0);
                                md = md.withPrefix(stmt.getPrefix());
                                //noinspection DataFlowIssue
                                return md.withBody(md.getBody().withStatements(ListUtils.insertAll(md.getBody().getStatements(), md.getBody().getStatements().size() - 1, getTagsBodyStatements)));
                            }
                            return stmt;
                        })));
                maybeRemoveImport(WEBMVCTAGSPROVIDER_FQ);
                maybeAddImport(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ);
                maybeAddImport(SERVERREQUESTOBSERVATIONCONVENTION_FQ);
                maybeAddImport(KEYVALUES_FQ);
                maybeRemoveImport(HTTPSERVLETREQUEST_FQ);
                maybeRemoveImport(HTTPSERVLETRESPONSE_FQ);
                return (J.ClassDeclaration) super.visitClassDeclaration(newClassDeclaration, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                J.VariableDeclarations methodParam = (J.VariableDeclarations) m.getParameters().get(0);
                J.Identifier methodParamIdentifier = methodParam.getVariables().get(0).getName();
                Boolean addHttpServletResponse = getCursor().pollMessage("addHttpServletResponse");
                Boolean addHttpServletRequest = getCursor().pollMessage("addHttpServletRequest");
                if (Boolean.TRUE.equals(addHttpServletResponse) && m.getBody() != null) {
                    m = JavaTemplate.builder("HttpServletResponse response = #{any()}.getResponse();")
                            .imports(HTTPSERVLETRESPONSE_FQ, SERVERREQUESTOBSERVATIONCONVENTION_FQ)
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.servlet-api", "spring-web-6.+", "micrometer-commons", "micrometer-observation"))
                            .build()
                            .apply(updateCursor(m), m.getBody().getCoordinates().firstStatement(), methodParamIdentifier);
                }
                if (Boolean.TRUE.equals(addHttpServletRequest) && m.getBody() != null) {
                    m = JavaTemplate.builder("HttpServletRequest request = #{any()}.getCarrier();")
                            .imports(HTTPSERVLETREQUEST_FQ, SERVERREQUESTOBSERVATIONCONVENTION_FQ, "io.micrometer.observation.transport.ReceiverContext")
                            .contextSensitive()
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.servlet-api", "spring-web-6.+", "micrometer-commons", "micrometer-observation"))
                            .build()
                            .apply(updateCursor(m), m.getBody().getCoordinates().firstStatement(), methodParamIdentifier);
                }
                return m;
            }

            @Override
            public Statement visitStatement(Statement statement, ExecutionContext ctx) {
                Statement s = (Statement) super.visitStatement(statement, ctx);
                if (s instanceof J.VariableDeclarations) {
                    J.VariableDeclarations m = (J.VariableDeclarations) s;
                    if (TypeUtils.isOfType(m.getType(), JavaType.buildType(TAGS_FQ))) {
                        J.MethodInvocation init = ((J.MethodInvocation) m.getVariables().get(0).getInitializer());
                        if (TAGS_OF_TAG_ARRAY.matches(init)) {
                            maybeRemoveImport(TAGS_FQ);
                            maybeRemoveImport(WEBMVCTAGS_FQ);
                            //noinspection DataFlowIssue
                            return null;
                        }
                    }
                    return m;
                }
                if (s instanceof J.Assignment) {
                    J.Assignment a = (J.Assignment) s;
                    if (TypeUtils.isOfType(a.getType(), JavaType.buildType(TAGS_FQ))) {
                        return refactorTagsUsage(ctx, a);
                    }
                    return a;
                }
                return s;
            }

            private Statement refactorTagsUsage(ExecutionContext ctx, J.Assignment a) {
                J.MethodInvocation init = ((J.MethodInvocation) a.getAssignment());
                J.MethodDeclaration insideMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (insideMethod != null && insideMethod.getBody()!= null) {
                    J.Identifier returnIdentifier = (J.Identifier) ((J.Return) insideMethod.getBody().getStatements().get(insideMethod.getBody().getStatements().size() - 1)).getExpression();

                    if (returnIdentifier != null) {
                        maybeAddImport(KEYVALUE_FQ);
                        maybeRemoveImport(TAG_FQ);
                        if (TAGS_AND_STRING_STRING.matches(init)) {
                            J.MethodInvocation createKeyValue = JavaTemplate.builder("KeyValue.of(#{any(java.lang.String)}, #{any(java.lang.String)})")
                                    .imports(KEYVALUE_FQ)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                    .build()
                                    .apply(getCursor(), a.getCoordinates().replace(), init.getArguments().get(0), init.getArguments().get(1));
                            return JavaTemplate.builder("#{any()}.and(#{any(io.micrometer.common.KeyValue)})")
                                    .imports(KEYVALUES_FQ)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons"))
                                    .build()
                                    .apply(getCursor(), a.getCoordinates().replace(), returnIdentifier, createKeyValue);
                        } else if (TAGS_AND_STRING_ARRAY.matches(init)) {
                            List<J.MethodInvocation> createKeys = new ArrayList<>();
                            for (int i = 0; i < init.getArguments().size(); i += 2) {
                                createKeys.add(JavaTemplate.builder("KeyValue.of(#{any(java.lang.String)}, #{any(java.lang.String)})")
                                        .imports(KEYVALUE_FQ)
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                        .build()
                                        .apply(getCursor(), a.getCoordinates().replace(), init.getArguments().get(i), init.getArguments().get(i + 1)));
                            }
                            String keyValueVarArg = "#{any(io.micrometer.common.KeyValue)}";
                            String keyValueVarArgsCombined = String.join(", ", Collections.nCopies(createKeys.size(), keyValueVarArg));
                            return JavaTemplate.builder("values.and(" + keyValueVarArgsCombined + ")")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons"))
                                    .build()
                                    .apply(getCursor(), a.getCoordinates().replace(), createKeys.toArray());
                        } else if (TAGS_AND_TAG_ARRAY.matches(init)) {
                            J.Identifier iterable = (J.Identifier) init.getArguments().get(0);
                            String template = "for (Tag tag : #{any()}) {\n" +
                                              "    values.and(KeyValue.of(tag.getKey(), tag.getValue()));\n" +
                                              "}\n";
                            return JavaTemplate.builder(template)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons"))
                                    .doAfterVariableSubstitution(System.out::println)
                                    .build()
                                    .apply(getCursor(), a.getCoordinates().replace(), iterable);
                        }
                    }
                }
                return a;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                J.MethodDeclaration enclosingMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosingMethod != null && enclosingMethod.getSimpleName().equals("getLowCardinalityKeyValues")) {
                    if (m.getMethodType() != null && TypeUtils.isOfType(m.getMethodType().getDeclaringType(), JavaType.buildType(HTTPSERVLETREQUEST_FQ))) {
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "addHttpServletRequest", true);
                    }
                    if (m.getMethodType() != null && TypeUtils.isOfType(m.getMethodType().getDeclaringType(), JavaType.buildType(HTTPSERVLETRESPONSE_FQ))) {
                        getCursor().putMessageOnFirstEnclosing(J.MethodDeclaration.class, "addHttpServletResponse", true);
                    }
                }
                return m;
            }
        });
    }
}
