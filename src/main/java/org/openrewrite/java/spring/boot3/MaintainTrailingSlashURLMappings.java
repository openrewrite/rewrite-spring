/*
 * Copyright 2023 the original author or authors.
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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public class MaintainTrailingSlashURLMappings extends ScanningRecipe<AtomicBoolean> {

    private static final String WEB_MVC_CONFIGURER = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
    private static final String WEB_FLUX_CONFIGURER = "org.springframework.web.reactive.config.WebFluxConfigurer";

    @Override
    public String getDisplayName() {
        return "Maintain trailing slash URL mappings";
    }

    @Override
    public String getDescription() {
        return "This is part of Spring MVC and WebFlux URL Matching Changes, as of Spring Framework 6.0, the trailing" +
               " slash matching configuration option has been deprecated and its default value set to false. " +
               "This means that previously, a controller `@GetMapping(\"/some/greeting\")` would match both" +
               " `GET /some/greeting` and `GET /some/greeting/`, but it doesn't match `GET /some/greeting/` " +
               "anymore by default and will result in an HTTP 404 error. This recipe is to maintain trailing slash in " +
               "all HTTP url mappings.";
    }

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean(false);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (!acc.get()) {
                    acc.set(FindWebConfigurer.find(cu));
                }
                return cu;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (acc.get()) {
                    return new AddSetUseTrailingSlashMatch().getVisitor().visit(tree, ctx);
                }

                return new AddRouteTrailingSlash().getVisitor().visit(tree, ctx);
            }
        };
    }

    private static class FindWebConfigurer extends JavaIsoVisitor<AtomicBoolean> {
        static boolean find(J j) {
            return new FindWebConfigurer()
                .reduce(j, new AtomicBoolean()).get();
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean found) {
            if (classDecl.getImplements() != null) {
                for (TypeTree impl : classDecl.getImplements()) {
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(impl.getType());
                    if (fullyQualified != null &&
                        (WEB_MVC_CONFIGURER.equals(fullyQualified.getFullyQualifiedName()) ||
                         WEB_FLUX_CONFIGURER.equals(fullyQualified.getFullyQualifiedName()))
                    ) {
                        found.set(true);
                        return classDecl;
                    }
                }
            }
            return classDecl;
        }
    }
}
