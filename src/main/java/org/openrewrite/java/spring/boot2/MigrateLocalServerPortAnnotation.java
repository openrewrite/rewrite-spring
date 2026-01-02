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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;

import static java.util.Collections.singletonList;

public class MigrateLocalServerPortAnnotation extends Recipe {
    private static final AnnotationMatcher LOCAL_SERVER_PORT_MATCHER =
        new AnnotationMatcher("@org.springframework.boot.context.embedded.LocalServerPort");

    @Override
    public String getDisplayName() {
        return "Use `org.springframework.boot.web.server.LocalServerPort`";
    }

    @Override
    public String getDescription() {
        return "Updates the package and adds the necessary dependency if `LocalServerPort` is in use. The package of `LocalServerPort` was changed in Spring Boot 2.0, necessitating changes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.boot.context.embedded.LocalServerPort", false),
            new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    J.Annotation a = super.visitAnnotation(annotation, ctx);
                    if (LOCAL_SERVER_PORT_MATCHER.matches(annotation)) {
                        a = a.withAnnotationType(a.getAnnotationType().withType(JavaType.buildType("org.springframework.boot.web.server.LocalServerPort")));
                        maybeRemoveImport("org.springframework.boot.context.embedded.LocalServerPort");
                        maybeAddImport("org.springframework.boot.web.server.LocalServerPort");
                    }
                    return a;
                }
            });
    }

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(new AddDependency(
            "org.springframework.boot",
            "spring-boot-starter-web",
            "2.0.x",
            null,
            "org.springframework.boot.web.server.LocalServerPort",
            null,
            null,
            null, null, null, null, null, null, null));
    }
}
