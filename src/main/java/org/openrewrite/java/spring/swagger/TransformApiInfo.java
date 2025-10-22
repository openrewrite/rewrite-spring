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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.TypeUtils;

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
        private final MethodMatcher termsOfServiceUrl = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder termsOfServiceUrl(String)");
        private final MethodMatcher licenseUrl = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder licenseUrl(String)");
        private final MethodMatcher license = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder license(String)");
        private final MethodMatcher contact = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder contact(springfox.documentation.service.Contact)");
        private final MethodMatcher contactConstructor = new MethodMatcher("springfox.documentation.service.Contact <constructor>(String, String, String)");
        private final MethodMatcher build = new MethodMatcher("springfox.documentation.builders.ApiInfoBuilder build()");

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation m = super.visitMethodInvocation(method, ctx);

            // Transform termsOfServiceUrl to termsOfService
            if (termsOfServiceUrl.matches(m)) {
                m = JavaTemplate.builder("termsOfService(#{any(String)})")
                        .build()
                        .apply(getCursor(), m.getCoordinates().replaceMethod(), m.getArguments().get(0));
                return m;
            }

            // Transform contact(new Contact(...)) to contact(new Contact().name(...).url(...).email(...))
            if (contact.matches(m) && !m.getArguments().isEmpty()) {
                Expression arg = m.getArguments().get(0);
                if (arg instanceof J.NewClass) {
                    J.NewClass newContact = (J.NewClass) arg;
                    if (contactConstructor.matches(newContact) && newContact.getArguments().size() == 3) {
                        maybeAddImport("io.swagger.v3.oas.models.info.Contact");
                        maybeRemoveImport("springfox.documentation.service.Contact");
                        return JavaTemplate.builder("contact(new Contact().name(#{any(String)}).url(#{any(String)}).email(#{any(String)}))")
                                .imports("io.swagger.v3.oas.models.info.Contact")
                                .build()
                                .apply(getCursor(), m.getCoordinates().replaceMethod(),
                                        newContact.getArguments().get(0),
                                        newContact.getArguments().get(1),
                                        newContact.getArguments().get(2));
                    }
                }
            }

            if (license.matches(m) || licenseUrl.matches(m)) {
                maybeAddImport("io.swagger.v3.oas.models.info.License");

                JavaCoordinates coords = m.getCoordinates().replaceMethod(); // effectively final
                J.MethodInvocation newLicense = getCursor().computeMessageIfAbsent("license", k -> {
                    return JavaTemplate.builder("license(new License())")
                            .imports("io.swagger.v3.oas.models.info.License")
                            .build()
                            .apply(getCursor(), coords);
                    }
                );

                Expression arg = m.getArguments().get(0);
                if (license.matches(m)) {
                    // add .name(...) to the chain
                    m = JavaTemplate.builder("#{any(io.swagger.v3.oas.models.info.License)}.name(#{any(String)})")
                            .imports("io.swagger.v3.oas.models.info.License")
                            .build()
                            .apply(getCursor(), newLicense.getCoordinates().replace(), newLicense, arg);
                } else {
                    // add .url(...) to the chain
                    m = JavaTemplate.builder("#{any(io.swagger.v3.oas.models.info.License)}.url(#{any(String)})")
                            .imports("io.swagger.v3.oas.models.info.License")
                            .build()
                            .apply(getCursor(), newLicense.getCoordinates().replace(), newLicense, arg);
                }
                return m;
            }

            // Remove .build() call
            if (build.matches(m) && m.getSelect() instanceof J.MethodInvocation) {
                return (J.MethodInvocation) m.getSelect();
            }

            return m;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass n = super.visitNewClass(newClass, ctx);

            if (apiInfoBuilderConstructor.matches(n)) {
                maybeAddImport("io.swagger.v3.oas.models.info.Info");
                maybeRemoveImport("springfox.documentation.builders.ApiInfoBuilder");
                n = JavaTemplate.builder("new Info()")
                        .imports("io.swagger.v3.oas.models.info.Info")
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
                ChangeType changeType = new ChangeType("springfox.documentation.service.ApiInfo", "io.swagger.v3.oas.models.info.Info", true);
                md = (J.MethodDeclaration) changeType.getVisitor().visitNonNull(md, ctx);
            }

            return md;
        }
    }
}
