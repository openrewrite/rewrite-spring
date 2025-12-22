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

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import static java.util.Objects.requireNonNull;

public class ChangeEmbeddedServletContainerCustomizer extends Recipe {

    private static final String DEPRECATED_INTERFACE_FQN = "org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer";

    @Override
    public String getDisplayName() {
        return "Adjust configuration classes to use the `WebServerFactoryCustomizer` interface";
    }

    @Override
    public String getDescription() {
        return "Find any classes implementing `EmbeddedServletContainerCustomizer` and change the interface to " +
                "`WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(DEPRECATED_INTERFACE_FQN, false), new JavaIsoVisitor<ExecutionContext>() {
            private J.@Nullable ParameterizedType webFactoryCustomizerIdentifier;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                return c.withImplements(
                        ListUtils.map(c.getImplements(), i -> {
                            if (TypeUtils.isOfClassType(i.getType(), DEPRECATED_INTERFACE_FQN)) {
                                maybeRemoveImport(DEPRECATED_INTERFACE_FQN);
                                maybeAddImport("org.springframework.boot.web.server.WebServerFactoryCustomizer");
                                maybeAddImport("org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory");
                                return getWebFactoryCustomizerIdentifier(ctx);
                            }
                            return i;
                        })
                );
            }

            private J.ParameterizedType getWebFactoryCustomizerIdentifier(ExecutionContext ctx) {
                // Really no need to use a JavaTemplate in this recipe, we just compile a stubbed out class and extract
                // the J.ParameterizedType from the class's stub's implements.
                if (webFactoryCustomizerIdentifier == null) {
                    JavaParser parser = JavaParser
                            .fromJavaVersion()
                            .classpathFromResources(ctx, "spring-boot-2.*")
                            .build();
                    J.CompilationUnit cu = parser.parse(
                                    "import org.springframework.boot.web.server.WebServerFactoryCustomizer;\n" +
                                    "import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;\n" +
                                    "public abstract class Template implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {}"
                            )
                            .map(J.CompilationUnit.class::cast)
                            .findFirst()
                            .get();

                    webFactoryCustomizerIdentifier = (J.ParameterizedType) requireNonNull(cu.getClasses()
                            .get(0).getImplements()).get(0);
                }

                return webFactoryCustomizerIdentifier.withId(Tree.randomId());
            }
        });
    }
}
