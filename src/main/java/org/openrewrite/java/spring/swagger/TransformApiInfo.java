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
package org.openrewrite.java.spring.swagger;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;

public class TransformApiInfo extends Recipe {

    @Override
    public String getDisplayName() {
        return "Transform SpringFox `ApiInfo` to Swagger v3 `Info`";
    }

    @Override
    public String getDescription() {
        return "Transforms SpringFox `ApiInfoBuilder` to Swagger v3 `Info` fluent API pattern.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("springfox.documentation.builders.ApiInfoBuilder", true),
                new TransformApiInfoVisitor()
        );
    }

    private static class TransformApiInfoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final MethodMatcher apiInfoBuilderConstructor = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder <constructor>()");

        // accumulator for AppInfoBuilder methods
        private static class Accumulator {
            private final Map<String, Function<J.MethodInvocation, String>> transformations = new HashMap<>();
            private final Map<String, J.MethodInvocation> methodInvocations = new HashMap<>();
            private final BiFunction<J.MethodInvocation, J.MethodInvocation, String> licenseTransformer =
                    (name, url) -> {
                        if (name == null && url == null) {
                            return "";
                        }
                        StringBuilder sb = new StringBuilder(".license(new License()");
                        if (name != null) {
                            sb.append(".name(\"").append(name.getArguments().get(0)).append("\")");
                        }
                        if (url != null) {
                            sb.append(".url(\"").append(url.getArguments().get(0)).append("\")");
                        }
                        sb.append(')');
                        return sb.toString();
                    };

            private Accumulator() {
                transformations.put("termsOfServiceUrl", mi -> String.format("termsOfService(\"%s\")", mi.getArguments().get(0)));
                transformations.put("license", mi -> String.format("name(\"%s\")", mi.getArguments().get(0)));
                transformations.put("licenseUrl", mi -> String.format("url(\"%s\")", mi.getArguments().get(0)));
                transformations.put("contact", mi -> {
                    J.NewClass c = (J.NewClass) mi.getArguments().get(0);
                    return String.format("contact(new Contact().name(\"%s\").url(\"%s\").email(\"%s\"))", c.getArguments().get(0), c.getArguments().get(1), c.getArguments().get(2));
                });
            }

            public void add(J.MethodInvocation mi) {
                if (!"build".equals(mi.getSimpleName())) {
                    methodInvocations.put(mi.getSimpleName(), mi);
                }
            }

            public String toTemplate() {

                // special case for license and licenseUrl
                J.MethodInvocation licenseMi = methodInvocations.remove("license");
                J.MethodInvocation licenseUrlMi = methodInvocations.remove("licenseUrl");
                String licenseFormat = licenseTransformer.apply(licenseMi, licenseUrlMi);

                String template = methodInvocations.entrySet().stream()
                        .map(e -> transformations.getOrDefault(e.getKey(),
                                m -> String.format("%s(\"%s\")", m.getSimpleName(), m.getArguments().get(0))).apply(e.getValue()))
                        .collect(joining("."));

                return licenseFormat.isEmpty() ? template : template.concat(licenseFormat);
            }
        }

        Accumulator accumulator = new Accumulator();

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
            accumulator.add(m);
            return m;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass n = super.visitNewClass(newClass, ctx);

            if (apiInfoBuilderConstructor.matches(n)) {
                maybeAddImport("io.swagger.v3.oas.models.info.Info");
                maybeAddImport("io.swagger.v3.oas.models.info.License");
                maybeAddImport("io.swagger.v3.oas.models.info.Contact");
                maybeRemoveImport("springfox.documentation.builders.ApiInfoBuilder");
                return JavaTemplate.builder("new Info()")
                        .imports("io.swagger.v3.oas.models.info.Info")
                        .imports("io.swagger.v3.oas.models.info.License")
                        .imports("io.swagger.v3.oas.models.info.Contact")
                        .build()
                        .apply(getCursor(), n.getCoordinates().replace());
            }

            return n;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

            // Transform return type from ApiInfo to Info
            if (TypeUtils.isOfClassType(md.getType(), "springfox.documentation.service.ApiInfo")) {
                // doAfterVisit here...
                ChangeType changeType = new ChangeType("springfox.documentation.service.ApiInfo", "io.swagger.v3.oas.models.info.Info", true);
                return (J.MethodDeclaration) changeType.getVisitor().visitNonNull(md, ctx);
            }

            return md;
        }
    }
}
