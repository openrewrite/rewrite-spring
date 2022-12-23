/*
 * Copyright 2021 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class ChangeEmbeddedServletContainerCustomizer extends Recipe {

    @Nullable
    private static J.ParameterizedType webFactoryCustomizerIdentifier;

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
    protected @Nullable TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>(DEPRECATED_INTERFACE_FQN);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {

        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                c = c.withImplements(
                        ListUtils.map(c.getImplements(), i -> {
                            if (TypeUtils.isOfClassType(i.getType(), DEPRECATED_INTERFACE_FQN)) {
                                maybeAddImport("org.springframework.boot.web.server.WebServerFactoryCustomizer");
                                maybeAddImport("org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory");
                                maybeRemoveImport(DEPRECATED_INTERFACE_FQN);
                                return getWebFactoryCustomizerIdentifier();
                            }
                            return i;
                        })
                );

                return c;
            }
        };
    }

    private static J.ParameterizedType getWebFactoryCustomizerIdentifier() {
        //Really no need to use a JavaTemplate in this recipe, we just compile a stubbed out class and extract
        //the J.ParameterizedType from the class's stub's implements.
        if (webFactoryCustomizerIdentifier == null) {
            JavaParser parser = JavaParser
                    .fromJavaVersion()
                    .dependsOn(
                            "package org.springframework.boot.web.server;\n" +
                            "public interface WebServerFactory {}",

                            "package org.springframework.boot.web.server;\n" +
                            "public interface ConfigurableWebServerFactory extends WebServerFactory {}",

                            "package org.springframework.boot.web.servlet.server;\n" +
                            "import org.springframework.boot.web.server.ConfigurableWebServerFactory;\n" +
                            "public interface ConfigurableServletWebServerFactory extends ConfigurableWebServerFactory {}",

                            "package org.springframework.boot.web.server;\n" +
                            "import org.springframework.boot.web.server.WebServerFactory;\n" +
                            "public interface WebServerFactoryCustomizer<T extends WebServerFactory> {\n" +
                            "  void customize(T factory);\n" +
                            "}"
                    ).build();
            J.CompilationUnit cu = parser.parse(
                    "import org.springframework.boot.web.server.WebServerFactoryCustomizer;\n" +
                    "import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;\n" +
                    "public abstract class Template implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {}"
            ).get(0);

            //noinspection DataFlowIssue
            webFactoryCustomizerIdentifier = (J.ParameterizedType) cu.getClasses().get(0).getImplements().get(0);
        }

        return webFactoryCustomizerIdentifier.withId(Tree.randomId());
    }
}
