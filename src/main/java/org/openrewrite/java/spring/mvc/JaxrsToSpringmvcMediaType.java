package org.openrewrite.java.spring.mvc;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = false)
public class JaxrsToSpringmvcMediaType extends Recipe {

    @Override
    public @NotNull String getDisplayName() {
        return "Migrate jax-rs MediaType to spring MVC MediaType";
    }

    @Override
    public @NotNull String getDescription() {
        return "Replaces all jax-rs MediaType with Spring MVC MediaType.";
    }

    @Override
    public @NotNull Set<String> getTags() {
        return Set.of("Java", "Spring");
    }

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
                if (typeName.equals("javax.ws.rs.core.MediaType") || typeName.equals("jakarta.ws.rs.core.MediaType")) {
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
