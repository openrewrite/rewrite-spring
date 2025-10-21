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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Expression;

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
                        .apply(getCursor(), m.getCoordinates().replace(), m.getArguments().get(0));
            }

            // Transform contact(new Contact(...)) to contact(new Contact().name(...).url(...).email(...))
            if (contact.matches(m) && !m.getArguments().isEmpty()) {
                Expression arg = m.getArguments().get(0);
                if (arg instanceof J.NewClass) {
                    J.NewClass newContact = (J.NewClass) arg;
                    if (contactConstructor.matches(newContact) && newContact.getArguments().size() == 3) {
                        maybeAddImport("io.swagger.v3.oas.models.info.Contact");
                        maybeRemoveImport("springfox.documentation.service.Contact");
                        m = JavaTemplate.builder("contact(new Contact().name(#{any(String)}).url(#{any(String)}).email(#{any(String)}))")
                                .imports("io.swagger.v3.oas.models.info.Contact")
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(),
                                        newContact.getArguments().get(0),
                                        newContact.getArguments().get(1),
                                        newContact.getArguments().get(2));
                    }
                }
            }

            // Handle license and licenseUrl together
            // This is more complex - we need to look at the chain and combine license() and licenseUrl()
            if (license.matches(m)) {
                // Check if the next method call in the chain is licenseUrl
                J.MethodInvocation parent = getCursor().getParentTreeCursor().getValue();
                if (parent instanceof J.MethodInvocation && licenseUrl.matches(parent)) {
                    // This will be handled by the licenseUrl case below
                    return m;
                }
            }

            if (licenseUrl.matches(m)) {
                // Look for the previous license() call in the chain
                if (m.getSelect() instanceof J.MethodInvocation) {
                    J.MethodInvocation select = (J.MethodInvocation) m.getSelect();
                    if (license.matches(select)) {
                        maybeAddImport("io.swagger.v3.oas.models.info.License");
                        m = JavaTemplate.builder("license(new License().name(#{any(String)}).url(#{any(String)}))")
                                .imports("io.swagger.v3.oas.models.info.License")
                                .build()
                                .apply(getCursor(), m.getCoordinates().replace(),
                                        select.getArguments().get(0),
                                        m.getArguments().get(0));
                    }
                }
            }

            // Remove .build() call and transform new ApiInfoBuilder() to new Info()
            if (build.matches(m) && m.getSelect() instanceof J.MethodInvocation) {
                // Return just the select (the chain without .build())
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
    }
}
