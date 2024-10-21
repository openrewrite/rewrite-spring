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
import org.openrewrite.java.search.FindImplementations;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MigrateWebMvcTagsToObservationConvention extends Recipe {

    private static final String DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ = "org.springframework.http.server.observation.DefaultServerRequestObservationConvention";
    private static final String HTTPSERVLETREQUEST_FQ = "jakarta.servlet.http.HttpServletRequest";
    private static final String HTTPSERVLETRESPONSE_FQ = "jakarta.servlet.http.HttpServletResponse";
    private static final String KEYVALUE_FQ = "io.micrometer.common.KeyValue";
    private static final String KEYVALUES_FQ = "io.micrometer.common.KeyValues";
    private static final String SERVERREQUESTOBSERVATIONCONVENTION_FQ = "org.springframework.http.server.observation.ServerRequestObservationContext";
    private static final String TAG_FQ = "io.micrometer.core.instrument.Tag";
    private static final String TAGS_FQ = "io.micrometer.core.instrument.Tags";
    private static final String WEBMVCTAGS_FQ = "org.springframework.boot.actuate.metrics.web.servlet.WebMvcTags";
    private static final String WEBMVCTAGSPROVIDER_FQ = "org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider";
    private static final MethodMatcher GET_TAGS = new MethodMatcher("* getTags(jakarta.servlet.http.HttpServletRequest, jakarta.servlet.http.HttpServletResponse, java.lang.Object, java.lang.Throwable)");
    private static final MethodMatcher GET_HIGH_CARD_KEY_VALUES = new MethodMatcher("* getHighCardinalityKeyValues(org.springframework.http.server.observation.ServerRequestObservationContext)");
    private static final MethodMatcher TAG_OF = new MethodMatcher("io.micrometer.core.instrument.Tag of(java.lang.String, java.lang.String)");
    private static final MethodMatcher TAGS_AND_STRING_STRING = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.String, java.lang.String)");
    private static final MethodMatcher TAGS_AND_STRING_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.String[])");
    private static final MethodMatcher TAGS_AND_TAG_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags and(io.micrometer.core.instrument.Tag[])");
    private static final MethodMatcher TAGS_AND_TAG_ITERABLE = new MethodMatcher("io.micrometer.core.instrument.Tags and(java.lang.Iterable)");
    private static final MethodMatcher TAGS_OF_STRING_STRING = new MethodMatcher("io.micrometer.core.instrument.Tags of(java.lang.String, java.lang.String)");
    private static final MethodMatcher TAGS_OF_STRING_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags of(java.lang.String[])");
    private static final MethodMatcher TAGS_OF_TAG_ARRAY = new MethodMatcher("io.micrometer.core.instrument.Tags of(io.micrometer.core.instrument.Tag[])");
    private static final MethodMatcher TAGS_OF_TAG_ITERABLE = new MethodMatcher("io.micrometer.core.instrument.Tags of(java.lang.Iterable)");
    private static final MethodMatcher TAGS_OF_ANY = new MethodMatcher("io.micrometer.core.instrument.Tags of(..)");

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
        return Preconditions.check(new FindImplementations(WEBMVCTAGSPROVIDER_FQ), new JavaVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.MethodDeclaration getTagsMethod = null;
                List<Statement> getTagsBodyStatements = new ArrayList<>();
                for (Statement stmt : classDecl.getBody().getStatements()) {
                    if (stmt instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration) stmt;
                        if (GET_TAGS.matches(md, classDecl)) {
                            getTagsMethod = md;
                            if (getTagsMethod.getBody() != null) {
                                getTagsBodyStatements.addAll(getTagsMethod.getBody().getStatements().subList(0, getTagsMethod.getBody().getStatements().size() - 1));
                                Statement ret = getTagsMethod.getBody().getStatements().get(getTagsMethod.getBody().getStatements().size() - 1);
                                if (ret instanceof J.Return && ((J.Return) ret).getExpression() instanceof J.MethodInvocation) {
                                    if (TAGS_OF_ANY.matches(((J.Return) ret).getExpression())) {
                                        getTagsBodyStatements.add((Statement) ((J.Return) ret).getExpression());
                                    }
                                }
                                break;
                            }

                        }
                    }
                }

                String tmpl = "class " + classDecl.getSimpleName() + " extends DefaultServerRequestObservationConvention {\n" +
                              "    @Override\n" +
                              "    public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {\n" +
                              "        KeyValues values = super.getHighCardinalityKeyValues(context);\n" +
                              "        return values;" +
                              "    }\n" +
                              "}";
                J.ClassDeclaration newClassDeclaration = JavaTemplate.builder(tmpl)
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+", "spring-web-6.+", "jakarta.servlet-api"))
                        .imports(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ, KEYVALUES_FQ, HTTPSERVLETREQUEST_FQ, HTTPSERVLETRESPONSE_FQ, SERVERREQUESTOBSERVATIONCONVENTION_FQ)
                        .build()
                        .apply(getCursor(), classDecl.getCoordinates().replace());

                J.ClassDeclaration finalNewClassDeclaration = newClassDeclaration;
                final J.MethodDeclaration finalGetTagsMethod = getTagsMethod;
                newClassDeclaration = newClassDeclaration
                        .withId(classDecl.getId())
                        .withLeadingAnnotations(classDecl.getLeadingAnnotations())
                        .withModifiers(classDecl.getModifiers())
                        .withPrefix(classDecl.getPrefix())
                        .withBody(newClassDeclaration.getBody().withStatements(ListUtils.map(classDecl.getBody().getStatements(), stmt -> {
                            if (stmt.equals(finalGetTagsMethod)) {
                                J.MethodDeclaration md = (J.MethodDeclaration) finalNewClassDeclaration.getBody().getStatements().get(0);
                                md = md.withPrefix(stmt.getPrefix());
                                //noinspection DataFlowIssue
                                return md.withBody(md.getBody().withStatements(ListUtils.insertAll(md.getBody().getStatements(), md.getBody().getStatements().size() - 1, getTagsBodyStatements)));
                            }
                            return stmt;
                        })));
                maybeAddImport(DEFAULTSERVERREQUESTOBSERVATIONCONVENTION_FQ);
                maybeAddImport(KEYVALUE_FQ);
                maybeAddImport(KEYVALUES_FQ);
                maybeAddImport(SERVERREQUESTOBSERVATIONCONVENTION_FQ);
                maybeRemoveImport(HTTPSERVLETREQUEST_FQ);
                maybeRemoveImport(HTTPSERVLETRESPONSE_FQ);
                maybeRemoveImport(TAG_FQ);
                maybeRemoveImport(TAGS_FQ);
                maybeRemoveImport(WEBMVCTAGS_FQ);
                maybeRemoveImport(WEBMVCTAGSPROVIDER_FQ);
                updateCursor(newClassDeclaration);
                return (J.ClassDeclaration) super.visitClassDeclaration(newClassDeclaration, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);
                J.ClassDeclaration cd = getCursor().firstEnclosing(J.ClassDeclaration.class);
                if (cd != null && GET_HIGH_CARD_KEY_VALUES.matches(m, cd)) {
                    J.VariableDeclarations methodParam = (J.VariableDeclarations) m.getParameters().get(0);
                    J.Identifier methodParamIdentifier = methodParam.getVariables().get(0).getName();
                    Boolean addHttpServletResponse = getCursor().pollMessage("addHttpServletResponse");
                    Boolean addHttpServletRequest = getCursor().pollMessage("addHttpServletRequest");
                    if (Boolean.TRUE.equals(addHttpServletResponse) && m.getBody() != null) {
                        m = JavaTemplate.builder("HttpServletResponse response = #{any()}.getResponse();")
                                .imports(HTTPSERVLETRESPONSE_FQ)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.servlet-api", "spring-web-6.+", "micrometer-observation-1.11.+"))
                                .build()
                                .apply(updateCursor(m), m.getBody().getCoordinates().firstStatement(), methodParamIdentifier);
                    }
                    if (Boolean.TRUE.equals(addHttpServletRequest) && m.getBody() != null) {
                        m = JavaTemplate.builder("HttpServletRequest request = #{any()}.getCarrier();")
                                .imports(HTTPSERVLETREQUEST_FQ)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "jakarta.servlet-api", "spring-web-6.+", "micrometer-observation-1.11.+"))
                                .build()
                                .apply(updateCursor(m), m.getBody().getCoordinates().firstStatement(), methodParamIdentifier);
                    }
                }
                return m;
            }

            @Override
            public Statement visitStatement(Statement statement, ExecutionContext ctx) {
                Statement s = (Statement) super.visitStatement(statement, ctx);
                if (s instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) s;
                    if (TypeUtils.isOfType(vd.getType(), JavaType.buildType(TAGS_FQ))) {
                        if (vd.getVariables().get(0).getInitializer() != null) {
                            //noinspection DataFlowIssue
                            return refactorTagsUsage(ctx, vd.getCoordinates(), (J.MethodInvocation) vd.getVariables().get(0).getInitializer(), vd);
                        }
                    }
                    return vd;
                }
                if (s instanceof J.Assignment) {
                    J.Assignment a = (J.Assignment) s;
                    if (TypeUtils.isOfType(a.getType(), JavaType.buildType(TAGS_FQ))) {
                        return refactorTagsUsage(ctx, a.getCoordinates(), (J.MethodInvocation) a.getAssignment(), a);
                    }
                    return a;
                }
                if (s instanceof J.MethodInvocation) {
                    J.MethodInvocation mi = (J.MethodInvocation) s;
                    if (TAGS_OF_ANY.matches(mi)) {
                        return refactorTagsUsage(ctx, mi.getCoordinates(), mi, mi);
                    }
                }
                return s;
            }

            private Statement refactorTagsUsage(ExecutionContext ctx, org.openrewrite.java.tree.CoordinateBuilder.Statement coords, J.MethodInvocation init, Statement original) {
                J.MethodDeclaration insideMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (insideMethod != null && insideMethod.getBody() != null) {
                    J.Identifier returnIdentifier = (J.Identifier) ((J.Return) insideMethod.getBody().getStatements().get(insideMethod.getBody().getStatements().size() - 1)).getExpression();

                    if (returnIdentifier != null) {
                        if (TAGS_AND_STRING_STRING.matches(init) || TAGS_OF_STRING_STRING.matches(init)) {
                            J.MethodInvocation createKeyValue = JavaTemplate.builder("KeyValue.of(#{any(java.lang.String)}, #{any(java.lang.String)})")
                                    .imports(KEYVALUE_FQ)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                    .build()
                                    .apply(getCursor(), coords.replace(), init.getArguments().get(0), init.getArguments().get(1));
                            return JavaTemplate.builder("#{any()}.and(#{any(io.micrometer.common.KeyValue)})")
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                    .build()
                                    .apply(getCursor(), coords.replace(), returnIdentifier, createKeyValue);
                        } else if (TAGS_AND_STRING_ARRAY.matches(init) || TAGS_OF_STRING_ARRAY.matches(init)) {
                            List<J> args = new ArrayList<>();
                            for (int i = 0; i < init.getArguments().size(); i += 2) {
                                args.add(JavaTemplate.builder("KeyValue.of(#{any(java.lang.String)}, #{any(java.lang.String)})")
                                        .imports(KEYVALUE_FQ)
                                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                        .build()
                                        .apply(getCursor(), coords.replace(), init.getArguments().get(i), init.getArguments().get(i + 1)));
                            }
                            return getMultiKeyValueStatement(ctx, coords, args, returnIdentifier);
                        } else if (TAGS_AND_TAG_ARRAY.matches(init) || TAGS_OF_TAG_ARRAY.matches(init)) {
                            List<Expression> validArgs = ListUtils.map(init.getArguments(), expression -> {
                                if (expression instanceof J.MethodInvocation && ((J.MethodInvocation) expression).getMethodType() != null && TypeUtils.isOfType(((J.MethodInvocation) expression).getMethodType().getDeclaringType(), JavaType.buildType(WEBMVCTAGS_FQ))) {
                                    //noinspection DataFlowIssue
                                    return null;
                                }
                                return expression;
                            });
                            if (validArgs.isEmpty()) {
                                //noinspection DataFlowIssue
                                return null;
                            }
                            List<J> args = new ArrayList<>();
                            for (Expression arg : validArgs) {
                                if (arg instanceof J.MethodInvocation && TAG_OF.matches(arg)) {
                                    args.add(JavaTemplate.builder("KeyValue.of(#{any(java.lang.String)}, #{any(java.lang.String)})")
                                            .imports(KEYVALUE_FQ)
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                                            .build()
                                            .apply(getCursor(), coords.replace(), ((J.MethodInvocation) arg).getArguments().get(0), ((J.MethodInvocation) arg).getArguments().get(1)));
                                } else {
                                    args.add(JavaTemplate.builder("KeyValue.of(#{any()}.getKey(), #{any()}.getValue())")
                                            .imports(KEYVALUE_FQ)
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-core-1.11.+", "micrometer-commons-1.11.+"))
                                            .build()
                                            .apply(getCursor(), coords.replace(), arg, arg));
                                }

                            }
                            return getMultiKeyValueStatement(ctx, coords, args, returnIdentifier);
                        } else if (TAGS_AND_TAG_ITERABLE.matches(init) || TAGS_OF_TAG_ITERABLE.matches(init)) {
                            Expression iterable = init.getArguments().get(0);
                            String template = "for (Tag tag : #{any()}) {\n" +
                                              "    #{any()}.and(KeyValue.of(tag.getKey(), tag.getValue()));\n" +
                                              "}\n";
                            J.ForEachLoop foreach = JavaTemplate.builder(template)
                                    .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-core-1.11.+", "micrometer-commons-1.11.+"))
                                    .imports(TAG_FQ, KEYVALUE_FQ)
                                    .build()
                                    .apply(getCursor(), coords.replace(), iterable, returnIdentifier);
                            return foreach.withControl(foreach.getControl().withIterable(foreach.getControl().getIterable().withPrefix(Space.SINGLE_SPACE)));
                        }
                    }
                }
                return original;
            }

            private Statement getMultiKeyValueStatement(ExecutionContext ctx, CoordinateBuilder.Statement coords, List<J> args, J.Identifier returnIdentifier) {
                String keyValueVarArg = "#{any(io.micrometer.common.KeyValue)}";
                String keyValueVarArgsCombined = String.join(", ", Collections.nCopies(args.size(), keyValueVarArg));
                return JavaTemplate.builder("#{any()}.and(" + keyValueVarArgsCombined + ")")
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "micrometer-commons-1.11.+"))
                        .build()
                        .apply(getCursor(), coords.replace(), ListUtils.insert(args, returnIdentifier, 0).toArray());
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                J.MethodDeclaration enclosingMethod = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosingMethod != null && enclosingMethod.getSimpleName().equals("getHighCardinalityKeyValues")) {
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
