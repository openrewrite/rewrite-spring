/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring.doc;

import lombok.Getter;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.IsPossibleSpringConfigFile;
import org.openrewrite.java.spring.doc.MigrateDocketBeanToGroupedOpenApiBean.DocketBeanAccumulator.DocketDefinition;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.openrewrite.Preconditions.and;
import static org.openrewrite.Preconditions.or;

public class MigrateDocketBeanToGroupedOpenApiBean extends ScanningRecipe<MigrateDocketBeanToGroupedOpenApiBean.DocketBeanAccumulator> {

    private static final AnnotationMatcher BEAN_ANNOTATIONMATCHER = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
    private static final TypeMatcher DOCKET_TYPEMATCHER = new TypeMatcher("springfox.documentation.spring.web.plugins.Docket");
    private static final TypeMatcher DOCUMENTATIONTYPE_TYPEMATCHER = new TypeMatcher("springfox.documentation.spi.DocumentationType");
    private static final TypeMatcher APISELECTORBUILDER_TYPEMATCHER = new TypeMatcher("springfox.documentation.spring.web.plugins.ApiSelectorBuilder");

    @Override
    public String getDisplayName() {
        return "Migrate `Docket` to `GroupedOpenAPI`";
    }

    @Override
    public String getDescription() {
        return "Migrate `Docket` to `GroupedOpenAPI`.";
    }

    @Override
    public DocketBeanAccumulator getInitialValue(ExecutionContext ctx) {
        return new DocketBeanAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(DocketBeanAccumulator acc) {
        return Preconditions.check(or(isDocketJavaBeanConfiguration(), new IsPossibleSpringConfigFile()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext, Cursor parent) {
                if (tree instanceof J.CompilationUnit) {
                    return new JavaBeanConfigurationScanner(acc).visit(tree, executionContext);
                } else if (new IsPossibleSpringConfigFile().visit(tree, executionContext).getMarkers().findFirst(SearchResult.class).isPresent()) {
                    acc.hasProperties = true;
                }
                return super.visit(tree, executionContext, parent);
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(DocketBeanAccumulator acc) {
        if (acc.docketDefinitions.size() != 1) {
            return TreeVisitor.noop();
        }
        DocketDefinition docketDefinition = acc.docketDefinitions.get(0);
        return Preconditions.check(or(isDocketJavaBeanConfiguration(), new IsPossibleSpringConfigFile()), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext, Cursor parent) {
                if (tree instanceof J.CompilationUnit) {
                    return new JavaIsoVisitor<ExecutionContext>() {
                        @Override
                        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
                            if (md.getLeadingAnnotations().stream().anyMatch(BEAN_ANNOTATIONMATCHER::matches) && DOCKET_TYPEMATCHER.matches(md.getReturnTypeExpression())) {
                                maybeRemoveImport("org.springframework.context.annotation.Bean");
                                maybeRemoveImport("springfox.documentation.builders.PathSelectors");
                                maybeRemoveImport("springfox.documentation.builders.RequestHandlerSelectors");
                                maybeRemoveImport("springfox.documentation.spi.DocumentationType");
                                maybeRemoveImport("springfox.documentation.spring.web.plugins.Docket");
                                if (acc.hasProperties && (docketDefinition.groupName == null || docketDefinition.groupName instanceof J.Literal) && (docketDefinition.apis == null || docketDefinition.apis instanceof J.Literal) && (docketDefinition.paths == null || docketDefinition.paths instanceof J.Literal)) {
                                    return null;
                                } else {
                                    String methodTemplate = "@Bean\n" +
                                            "public GroupedOpenApi " + method.getSimpleName() + "() {\n" +
                                            "    return GroupedOpenApi.builder()\n";
                                    if (docketDefinition.groupName == null) {
                                        methodTemplate = methodTemplate + "            .group(\"public\")\n";
                                    } else {
                                        methodTemplate = methodTemplate + "            .group(" + (docketDefinition.groupName instanceof J.Literal ? "\"" + docketDefinition.groupName + "\"" : docketDefinition.groupName) + ")\n";
                                    }
                                    if (docketDefinition.paths == null && docketDefinition.apis == null) {
                                        methodTemplate = methodTemplate + "            .pathsToMatch(\"/**\")\n";
                                    } else {
                                        methodTemplate = methodTemplate + (docketDefinition.paths != null ? "            .pathsToMatch(" + (docketDefinition.paths instanceof J.Literal ? "\"" + docketDefinition.paths + "\"" : docketDefinition.paths) + ")\n" : "");
                                        methodTemplate = methodTemplate + (docketDefinition.apis != null ? "            .packagesToScan(" + (docketDefinition.apis instanceof J.Literal ? "\"" + docketDefinition.apis + "\"" : docketDefinition.apis) + ")\n" : "");
                                    }
                                    methodTemplate = methodTemplate + "            .build();\n" +
                                            "}";

                                    maybeAddImport("org.springdoc.core.models.GroupedOpenApi", false);
                                    maybeAddImport("org.springframework.context.annotation.Bean", false);
                                    return JavaTemplate.builder(methodTemplate)
                                            .contextSensitive()
                                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(executionContext, "spring-context", "springdoc-openapi-starter-common"))
                                            .imports("org.springdoc.core.models.GroupedOpenApi", "org.springframework.context.annotation.Bean").build()
                                            .apply(getCursor(), method.getCoordinates().replace());
                                }
                            }
                            return md;
                        }
                    }.visit(tree, executionContext);
                } else if (new IsPossibleSpringConfigFile().visit(tree, executionContext).getMarkers().findFirst(SearchResult.class).isPresent()) {
                    if (acc.hasProperties && (docketDefinition.groupName == null || docketDefinition.groupName instanceof J.Literal) && (docketDefinition.apis == null || docketDefinition.apis instanceof J.Literal) && (docketDefinition.paths == null || docketDefinition.paths instanceof J.Literal)) {
                        Tree result = new AddSpringProperty("springdoc.api-docs.path", "/v3/api-docs", null, null).getVisitor().visit(tree, executionContext);
                        result = new AddSpringProperty("springdoc.swagger-ui.path", "/swagger-ui.html", null, null).getVisitor().visit(result, executionContext);
                        if (docketDefinition.groupName == null) {
                            if (docketDefinition.paths == null && docketDefinition.apis == null) {
                                result = new AddSpringProperty("springdoc.paths-to-match", "/**", null, null).getVisitor().visit(result, executionContext);
                            } else {
                                if (docketDefinition.paths != null) {
                                    result = new AddSpringProperty("springdoc.paths-to-match", ((J.Literal) docketDefinition.paths).getValueSource(), null, null).getVisitor().visit(result, executionContext);
                                }
                                if (docketDefinition.paths != null) {
                                    result = new AddSpringProperty("springdoc.packages-to-scan", ((J.Literal) docketDefinition.apis).getValueSource(), null, null).getVisitor().visit(result, executionContext);
                                }
                            }
                        } else {
                            result = new AddSpringProperty("springdoc.group-configs[0].group", docketDefinition.groupName.toString(), null, null).getVisitor().visit(result, executionContext);
                            if (docketDefinition.paths == null && docketDefinition.apis == null) {
                                result = new AddSpringProperty("springdoc.group-configs[0].paths-to-match", "/**", null, null).getVisitor().visit(result, executionContext);
                            } else {
                                if (docketDefinition.paths != null) {
                                    result = new AddSpringProperty("springdoc.group-configs[0].paths-to-match", ((J.Literal) docketDefinition.paths).getValueSource(), null, null).getVisitor().visit(result, executionContext);
                                }
                                if (docketDefinition.paths != null) {
                                    result = new AddSpringProperty("springdoc.group-configs[0].packages-to-scan", ((J.Literal) docketDefinition.apis).getValueSource(), null, null).getVisitor().visit(result, executionContext);
                                }
                            }
                        }
                        return result;
                    }
                }
                return super.visit(tree, executionContext, parent);
            }
        });
    }

    private static @NotNull TreeVisitor<?, ExecutionContext> isDocketJavaBeanConfiguration() {
        return and(new UsesType<>("springfox.documentation.spring.web.plugins.Docket", true), new UsesType<>("org.springframework.context.annotation.Bean", true));
    }

    private static class JavaBeanConfigurationScanner extends JavaIsoVisitor<ExecutionContext> {
        private final DocketBeanAccumulator acc;

        public JavaBeanConfigurationScanner(DocketBeanAccumulator acc) {
            this.acc = acc;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, executionContext);
            if (md.getLeadingAnnotations().stream().anyMatch(BEAN_ANNOTATIONMATCHER::matches) && DOCKET_TYPEMATCHER.matches(md.getReturnTypeExpression())) {
                DocketDefinition.Builder docketDefinitionBuilder = new JavaIsoVisitor<DocketDefinition.Builder>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, DocketDefinition.Builder builder) {
                        J.NewClass nc = super.visitNewClass(newClass, builder);
                        if (!builder.isValid()) {
                            return nc;
                        }
                        if (!DOCKET_TYPEMATCHER.matches(newClass.getType()) ||
                                !DOCUMENTATIONTYPE_TYPEMATCHER.matches(newClass.getArguments().get(0).getType()) ||
                                !"SWAGGER_2".equals(((J.FieldAccess) newClass.getArguments().get(0)).getName().getSimpleName())) {
                            builder.invalidate();
                        }
                        return nc;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, DocketDefinition.Builder builder) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, builder);
                        if (!builder.isValid()) {
                            return mi;
                        }
                        if (mi.getSelect() != null &&
                                APISELECTORBUILDER_TYPEMATCHER.matches(mi.getSelect().getType()) &&
                                "paths".equals(mi.getSimpleName())) {
                            new ArgumentExtractor(builder).visit(mi.getArguments(), builder::addPath);
                        } else if (mi.getSelect() != null &&
                                APISELECTORBUILDER_TYPEMATCHER.matches(mi.getSelect().getType()) &&
                                "apis".equals(mi.getSimpleName())) {
                            new ArgumentExtractor(builder).visit(mi.getArguments(), builder::addApi);
                        } else if (mi.getSelect() != null &&
                                DOCKET_TYPEMATCHER.matches(mi.getSelect().getType()) &&
                                "groupName".equals(mi.getSimpleName())) {
                            builder.setGroup(mi.getArguments().get(0));
                        }
                        return mi;
                    }
                }.reduce(md, new DocketDefinition.Builder());
                if (docketDefinitionBuilder.isValid()) {
                    acc.docketDefinitions.add(docketDefinitionBuilder.build());
                }
            }
            return md;
        }
    }

    static class ArgumentExtractor extends JavaIsoVisitor<Consumer<Expression>> {
        private final DocketDefinition.Builder builder;

        public ArgumentExtractor(DocketDefinition.Builder builder) {
            this.builder = builder;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, Consumer<Expression> consumer) {
            new JavaIsoVisitor<Consumer<Expression>>() {
                @Override
                public J.Literal visitLiteral(J.Literal literal, Consumer<Expression> consumer) {
                    consumer.accept(literal);
                    return super.visitLiteral(literal, consumer);
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, Consumer<Expression> consumer) {
                    consumer.accept(identifier);
                    return super.visitIdentifier(identifier, consumer);
                }

                @Override
                public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Consumer<Expression> consumer) {
                    consumer.accept(fieldAccess);
                    return super.visitFieldAccess(fieldAccess, consumer);
                }
            }.visit(method.getArguments(), consumer);
            return method;
        }
    }

    static class DocketBeanAccumulator {
        public boolean hasProperties = false;

        public List<DocketDefinition> docketDefinitions = new ArrayList<>();

        @Value
        static class DocketDefinition {
            Expression groupName;
            Expression paths;

            Expression apis;

            static class Builder {
                @Getter
                private boolean valid = true;
                private Expression paths = null;
                private Expression apis = null;

                private Expression groupName = null;

                public void invalidate() {
                    valid = false;
                }

                public void addPath(Expression expression) {
                    paths = expression;
                }

                public void setGroup(Expression expression) {
                    groupName = expression;
                }

                public void addApi(Expression expression) {
                    apis = expression;
                }

                public DocketDefinition build() {
                    return new DocketDefinition(groupName, paths, apis);
                }
            }
        }
    }
}
