/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.mvc;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class JaxrsToSpringmvcMediaType extends Recipe {

    String displayName = "Migrate jax-rs MediaType to spring MVC MediaType";
    String description = "Replaces all jax-rs MediaType with Spring MVC MediaType.";
    Set<String> tags = new HashSet<>(Arrays.asList("Java", "Spring"));

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                doAfterVisit(new ChangeType("javax.ws.rs.core.MediaType", "org.springframework.http.MediaType", true).getVisitor());
                doAfterVisit(new ChangeType("jakarta.ws.rs.core.MediaType", "org.springframework.http.MediaType", true).getVisitor());
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                String typeName = fieldAccess.getTarget().getType() != null ? fieldAccess.getTarget().getType().toString() : "";
                if ("javax.ws.rs.core.MediaType".equals(typeName) || "jakarta.ws.rs.core.MediaType".equals(typeName)) {
                    maybeRemoveImport(typeName);
                    maybeAddImport("org.springframework.http.MediaType");

                    String field = fieldAccess.getName().getSimpleName();
                    String newField = field.endsWith("_TYPE") ? field.substring(0, field.length() - "_TYPE".length()) : field + "_VALUE";
                    JavaCoordinates coordinates = fieldAccess.getCoordinates().replace();
                    return JavaTemplate.builder("MediaType.#{}")
                            .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-web"))
                            .imports("org.springframework.http.MediaType")
                            .build()
                            .apply(getCursor(), coordinates, newField);
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

        };
    }

}
