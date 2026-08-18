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

import lombok.*;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.spring.IsPossibleSpringConfigFile;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.openrewrite.Preconditions.and;
import static org.openrewrite.Preconditions.or;

public class MigrateDocketBeanToGroupedOpenApiBean extends ScanningRecipe<MigrateDocketBeanToGroupedOpenApiBean.DocketBeanAccumulator> {

    private static final AnnotationMatcher BEAN_ANNOTATIONMATCHER = new AnnotationMatcher("@org.springframework.context.annotation.Bean");
    private static final TypeMatcher DOCKET_TYPEMATCHER = new TypeMatcher("springfox.documentation.spring.web.plugins.Docket");
    private static final TypeMatcher DOCUMENTATIONTYPE_TYPEMATCHER = new TypeMatcher("springfox.documentation.spi.DocumentationType");
    private static final TypeMatcher APISELECTORBUILDER_TYPEMATCHER = new TypeMatcher("springfox.documentation.spring.web.plugins.ApiSelectorBuilder");
    private static final ArgumentExtractor REQUESTHANDLERSELECTORS_ARGUMENT_EXTRACTOR = new ArgumentExtractor(Arrays.asList(new MethodMatcher("springfox.documentation.builders.RequestHandlerSelectors any()", true), new MethodMatcher("springfox.documentation.builders.RequestHandlerSelectors basePackage(..)", true)));
    private static final ArgumentExtractor PATHSELECTOR_ARGUMENT_EXTRACTOR = new ArgumentExtractor(Arrays.asList(new MethodMatcher("springfox.documentation.builders.PathSelectors any()", true), new MethodMatcher("springfox.documentation.builders.PathSelectors ant(..)", true)));
    private static final MethodMatcher WITH_METHOD_ANNOTATION = new MethodMatcher("springfox.documentation.builders.RequestHandlerSelectors withMethodAnnotation(..)", true);
    private static final MethodMatcher WITH_CLASS_ANNOTATION = new MethodMatcher("springfox.documentation.builders.RequestHandlerSelectors withClassAnnotation(..)", true);
    private static final MethodMatcher PREDICATE_NEGATE = new MethodMatcher("java.util.function.Predicate negate()", true);


    @Getter
    final String displayName = "Migrate `Docket` to `GroupedOpenAPI`";

    @Getter
    final String description = "Migrate a `Docket` bean to a `GroupedOpenAPI` bean preserving group name, packages and paths. " +
            "When possible the recipe will prefer property based configuration.";

    @Override
    public DocketBeanAccumulator getInitialValue(ExecutionContext ctx) {
        return new DocketBeanAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(DocketBeanAccumulator acc) {
        return Preconditions.check(shouldVisit(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof J.CompilationUnit) {
                    return new JavaBeanConfigurationScanner(acc).visitNonNull(tree, ctx);
                }
                if (isApplicationProperties(tree)) {
                    acc.hasProperties = true;
                }
                return super.visit(tree, ctx);
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(DocketBeanAccumulator acc) {
        if (acc.docketDefinitions.isEmpty()) {
            return TreeVisitor.noop();
        }
        boolean singleDocket = acc.docketDefinitions.size() == 1;
        DocketDefinition onlyDocket = singleDocket ? acc.docketDefinitions.get(0) : null;
        boolean canConfigureInProperties = singleDocket && canConfigureInProperties(acc, onlyDocket);
        return Preconditions.check(shouldVisit(), new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof J.CompilationUnit) {
                    return new DocketBeanVisitor(canConfigureInProperties, acc.docketDefinitions).visitNonNull(tree, ctx);
                }
                if (isApplicationProperties(tree) && canConfigureInProperties) {
                    Tree result = addSpringProperty(ctx, tree, "springdoc.api-docs.path", "/v3/api-docs");
                    result = addSpringProperty(ctx, result, "springdoc.swagger-ui.path", "/swagger-ui.html");
                    if (onlyDocket.groupName == null) {
                        result = formatGroupProperties(ctx, result, "springdoc", onlyDocket);
                    } else {
                        result = addSpringProperty(ctx, result, "springdoc.group-configs[0]" + ".group", onlyDocket.groupName.toString());
                        result = formatGroupProperties(ctx, result, "springdoc.group-configs[0]", onlyDocket);
                    }
                    return result;
                }
                return tree;
            }
        });
    }

    private static TreeVisitor<?, ExecutionContext> shouldVisit() {
        return or(new IsPossibleSpringConfigFile(),
                and(new UsesType<>("springfox.documentation.spring.web.plugins.Docket", true),
                        new UsesType<>("org.springframework.context.annotation.Bean", true)));
    }

    private static boolean isApplicationProperties(@Nullable Tree tree) {
        return (tree instanceof Properties.File && "application.properties".equals(((Properties.File) tree).getSourcePath().getFileName().toString())) ||
                (tree instanceof Yaml.Documents && ((Yaml.Documents) tree).getSourcePath().getFileName().toString().matches("application\\.ya*ml"));
    }

    private static boolean canConfigureInProperties(DocketBeanAccumulator acc, DocketDefinition docketDefinition) {
        return acc.hasProperties &&
                docketDefinition.annotationFilters.isEmpty() &&
                (docketDefinition.groupName == null || docketDefinition.groupName instanceof J.Literal) &&
                (docketDefinition.apis == null || docketDefinition.apis instanceof J.Literal) &&
                (docketDefinition.paths == null || docketDefinition.paths instanceof J.Literal);
    }

    private Tree formatGroupProperties(ExecutionContext ctx, Tree properties, String prefix, DocketDefinition docketDefinition) {
        if (docketDefinition.paths == null && docketDefinition.apis == null) {
            properties = addSpringProperty(ctx, properties, prefix + ".paths-to-match", "/**");
        } else {
            if (docketDefinition.paths instanceof J.Literal && ((J.Literal) docketDefinition.paths).getValueSource() != null) {
                properties = addSpringProperty(ctx, properties, prefix + ".paths-to-match", ((J.Literal) docketDefinition.paths).getValueSource());
            }
            if (docketDefinition.apis instanceof J.Literal) {
                if (((J.Literal) docketDefinition.apis).getValueSource() != null) {
                    properties = addSpringProperty(ctx, properties, prefix + ".packages-to-scan", ((J.Literal) docketDefinition.apis).getValueSource());
                }
            }
        }
        return properties;
    }

    private static Tree addSpringProperty(ExecutionContext ctx, Tree properties, String property, String value) {
        return new AddSpringProperty(property, value, null, null).getVisitor().visitNonNull(properties, ctx);
    }


    @RequiredArgsConstructor
    private static class JavaBeanConfigurationScanner extends JavaIsoVisitor<ExecutionContext> {
        private final DocketBeanAccumulator acc;

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (md.getLeadingAnnotations().stream().anyMatch(BEAN_ANNOTATIONMATCHER::matches) && DOCKET_TYPEMATCHER.matches(md.getReturnTypeExpression())) {
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                String owningType = enclosing != null && enclosing.getType() != null ? enclosing.getType().getFullyQualifiedName() : "";
                DocketDefinition.Builder docketDefinitionBuilder = new JavaIsoVisitor<DocketDefinition.Builder>() {
                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, DocketDefinition.Builder builder) {
                        J.NewClass nc = super.visitNewClass(newClass, builder);
                        if (builder.isValid() && !DOCKET_TYPEMATCHER.matches(newClass.getType()) ||
                                !DOCUMENTATIONTYPE_TYPEMATCHER.matches(newClass.getArguments().get(0).getType()) ||
                                !"SWAGGER_2".equals(((J.FieldAccess) newClass.getArguments().get(0)).getName().getSimpleName())) {
                            builder.invalidate();
                        }
                        return nc;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, DocketDefinition.Builder builder) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, builder);
                        if (!builder.isValid() || mi.getSelect() == null) {
                            return mi;
                        }
                        if (APISELECTORBUILDER_TYPEMATCHER.matches(mi.getSelect().getType()) &&
                                "paths".equals(mi.getSimpleName())) {
                            PATHSELECTOR_ARGUMENT_EXTRACTOR.visit(mi.getArguments(), new ArgumentExtractor.ArgumentExtractorResult(builder, builder::setPaths));
                        } else if (APISELECTORBUILDER_TYPEMATCHER.matches(mi.getSelect().getType()) &&
                                "apis".equals(mi.getSimpleName())) {
                            Expression arg = mi.getArguments().get(0);
                            AnnotationFilter filter = tryExtractAnnotationFilter(arg);
                            if (filter != null) {
                                builder.addAnnotationFilter(filter);
                            } else {
                                REQUESTHANDLERSELECTORS_ARGUMENT_EXTRACTOR.visit(mi.getArguments(), new ArgumentExtractor.ArgumentExtractorResult(builder, builder::setApis));
                            }
                        } else if (DOCKET_TYPEMATCHER.matches(mi.getSelect().getType()) &&
                                "groupName".equals(mi.getSimpleName())) {
                            builder.setGroupName(mi.getArguments().get(0));
                        }
                        return mi;
                    }
                }.reduce(md, new DocketDefinition.Builder());
                if (docketDefinitionBuilder.isValid()) {
                    acc.docketDefinitions.add(docketDefinitionBuilder.build(owningType, md.getSimpleName()));
                }
            }
            return md;
        }

        private static @Nullable AnnotationFilter tryExtractAnnotationFilter(Expression arg) {
            if (!(arg instanceof J.MethodInvocation)) {
                return null;
            }
            J.MethodInvocation call = (J.MethodInvocation) arg;
            boolean negated = false;
            if (PREDICATE_NEGATE.matches(call) && call.getSelect() instanceof J.MethodInvocation) {
                negated = true;
                call = (J.MethodInvocation) call.getSelect();
            }
            if (WITH_METHOD_ANNOTATION.matches(call) && call.getArguments().size() == 1) {
                return new AnnotationFilter(false, negated, call.getArguments().get(0));
            }
            if (WITH_CLASS_ANNOTATION.matches(call) && call.getArguments().size() == 1) {
                return new AnnotationFilter(true, negated, call.getArguments().get(0));
            }
            return null;
        }
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    private static class ArgumentExtractor extends JavaIsoVisitor<ArgumentExtractor.ArgumentExtractorResult> {

        List<MethodMatcher> methodMatchers;

        @Override
        public @Nullable J visit(@Nullable Tree tree, ArgumentExtractorResult argumentExtractorResult) {
            if (argumentExtractorResult.builder.isValid() &&
                    tree instanceof J.MethodInvocation &&
                    ((J.MethodInvocation) tree).getSelect() != null &&
                    methodMatchers.stream().anyMatch(e -> e.matches((J.MethodInvocation) tree))) {
                return super.visit(tree, argumentExtractorResult);
            }
            argumentExtractorResult.builder.invalidate();
            return (J) tree;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ArgumentExtractorResult argumentExtractorResult) {
            new JavaIsoVisitor<ArgumentExtractorResult>() {
                @Override
                public J.Literal visitLiteral(J.Literal literal, ArgumentExtractorResult argumentExtractorResult) {
                    argumentExtractorResult.success.accept(literal);
                    return literal;
                }

                @Override
                public J.Identifier visitIdentifier(J.Identifier identifier, ArgumentExtractorResult argumentExtractorResult) {
                    argumentExtractorResult.success.accept(identifier);
                    return identifier;
                }
            }.visit(method.getArguments(), argumentExtractorResult);
            return method;
        }

        @Value
        static class ArgumentExtractorResult {
            DocketDefinition.Builder builder;
            Consumer<Expression> success;
        }
    }

    public static class DocketBeanAccumulator {
        public boolean hasProperties = false;
        public List<DocketDefinition> docketDefinitions = new ArrayList<>();
    }

    @Value
    public static class DocketDefinition {
        String owningType;

        String methodName;

        @Nullable
        Expression groupName;

        @Nullable
        Expression paths;

        @Nullable
        Expression apis;

        List<AnnotationFilter> annotationFilters;

        static class Builder {
            @Getter
            private boolean valid = true;

            @Setter
            @Nullable
            private Expression paths = null;

            @Setter
            @Nullable
            private Expression apis = null;

            @Setter
            @Nullable
            private Expression groupName = null;

            private final List<AnnotationFilter> annotationFilters = new ArrayList<>();

            public void invalidate() {
                valid = false;
            }

            public void addAnnotationFilter(AnnotationFilter filter) {
                annotationFilters.add(filter);
            }

            public DocketDefinition build(String owningType, String methodName) {
                return new DocketDefinition(owningType, methodName, groupName, paths, apis, annotationFilters);
            }
        }
    }

    @Value
    public static class AnnotationFilter {
        boolean classLevel;
        boolean negated;
        Expression annotationType;
    }

    @EqualsAndHashCode(callSuper = false)
    @Value
    private static class DocketBeanVisitor extends JavaIsoVisitor<ExecutionContext> {
        boolean removeMethod;
        List<DocketDefinition> docketDefinitions;

        @Override
        public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
            if (service(AnnotationService.class).matches(getCursor(), BEAN_ANNOTATIONMATCHER) &&
                    DOCKET_TYPEMATCHER.matches(md.getReturnTypeExpression())) {
                DocketDefinition docketDefinition = findMatching(md);
                if (docketDefinition == null) {
                    return md;
                }
                maybeRemoveImport("springfox.documentation.builders.PathSelectors");
                maybeRemoveImport("springfox.documentation.builders.RequestHandlerSelectors");
                maybeRemoveImport("springfox.documentation.spi.DocumentationType");
                maybeRemoveImport("springfox.documentation.spring.web.plugins.Docket");

                if (removeMethod) {
                    maybeRemoveImport("org.springframework.context.annotation.Bean");
                    return null; // Remove the bean method when switching to properties
                }

                List<Expression> args = new ArrayList<>();
                StringBuilder template = new StringBuilder()
                        .append("@Bean\n")
                        .append("public GroupedOpenApi ").append(method.getSimpleName()).append("() {\n")
                        .append("return GroupedOpenApi.builder()\n");
                if (docketDefinition.groupName == null) {
                    template.append(".group(\"public\")\n");
                } else {
                    args.add(docketDefinition.groupName);
                    template.append(".group(#{any()})\n");
                }
                if (docketDefinition.paths == null && docketDefinition.apis == null) {
                    template.append(".pathsToMatch(\"/**\")\n");
                } else {
                    if (docketDefinition.paths != null) {
                        args.add(docketDefinition.paths);
                        template.append(".pathsToMatch(#{any()})\n");
                    }
                    if (docketDefinition.apis != null) {
                        args.add(docketDefinition.apis);
                        template.append(".packagesToScan(#{any()})\n");
                    }
                }
                if (!docketDefinition.annotationFilters.isEmpty()) {
                    template.append(".addOpenApiMethodFilter(method -> ");
                    for (int i = 0; i < docketDefinition.annotationFilters.size(); i++) {
                        AnnotationFilter filter = docketDefinition.annotationFilters.get(i);
                        if (i > 0) {
                            template.append(" && ");
                        }
                        if (filter.negated) {
                            template.append("!");
                        }
                        template.append("method");
                        if (filter.classLevel) {
                            template.append(".getDeclaringClass()");
                        }
                        template.append(".isAnnotationPresent(#{any()})");
                        args.add(filter.annotationType);
                    }
                    template.append(")\n");
                }
                template.append(".build();\n")
                        .append("}");

                maybeAddImport("org.springdoc.core.models.GroupedOpenApi", false);
                maybeAddImport("org.springframework.context.annotation.Bean", false);
                return JavaTemplate.builder(template.toString())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-context", "springdoc-openapi-starter-common"))
                        .imports("org.springdoc.core.models.GroupedOpenApi", "org.springframework.context.annotation.Bean").build()
                        .apply(getCursor(), method.getCoordinates().replace(), args.toArray());
            }
            return md;
        }

        private @Nullable DocketDefinition findMatching(J.MethodDeclaration md) {
            J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
            String owningType = enclosing != null && enclosing.getType() != null ? enclosing.getType().getFullyQualifiedName() : "";
            String methodName = md.getSimpleName();
            for (DocketDefinition d : docketDefinitions) {
                if (d.owningType.equals(owningType) && d.methodName.equals(methodName)) {
                    return d;
                }
            }
            return null;
        }
    }
}
