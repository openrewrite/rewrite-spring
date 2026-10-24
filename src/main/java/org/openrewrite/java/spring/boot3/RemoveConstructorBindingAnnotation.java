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
package org.openrewrite.java.spring.boot3;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.trait.Comments;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class RemoveConstructorBindingAnnotation extends Recipe {

    private static final String ANNOTATION_CONSTRUCTOR_BINDING = "org.springframework.boot.context.properties.ConstructorBinding";
    private static final String ANNOTATION_CONFIG_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String MOVE_CONSTRUCTOR_BINDING_TODO = " TODO: Remove ConstructorBinding on class level and move it to the appropriate constructor.";

    @Getter
    final String displayName = "Remove Unnecessary `@ConstructorBinding`";

    @Getter
    final String description = "As of Boot 3.0 `@ConstructorBinding` is no longer needed at the type level on `@ConfigurationProperties` classes and should be removed.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.boot.context.properties.ConstructorBinding", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);

                // Collect the class constructors.
                List<J.MethodDeclaration> constructors = c.getBody().getStatements().stream()
                        .filter(org.openrewrite.java.tree.J.MethodDeclaration.class::isInstance)
                        .map(org.openrewrite.java.tree.J.MethodDeclaration.class::cast)
                        .filter(J.MethodDeclaration::isConstructor)
                        .collect(toList());

                if (constructors.size() == 1) {
                    Optional<J.Annotation> bindingAnnotation = constructors.get(0).getLeadingAnnotations().stream()
                            .filter(a -> TypeUtils.isOfClassType(a.getType(), ANNOTATION_CONSTRUCTOR_BINDING))
                            .findAny();

                    // A single class constructor with a ConstructorBinding annotation is present.
                    if (bindingAnnotation.isPresent()) {
                        c = c.withBody(c.getBody().withStatements(
                                ListUtils.map(c.getBody().getStatements(), s -> {
                                    if (s == constructors.get(0)) {
                                        J.MethodDeclaration m = (J.MethodDeclaration) s;
                                        // Only visit the `J.MethodDeclaration` subtree and remove the target annotation.
                                        maybeRemoveImport(ANNOTATION_CONSTRUCTOR_BINDING);
                                        return (J.MethodDeclaration) new RemoveTargetAnnotation(bindingAnnotation.get()).visit(m, ctx, getCursor().getParentTreeCursor());
                                    }
                                    return s;
                                }))
                        );
                    }
                }

                if (c.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), ANNOTATION_CONFIG_PROPERTIES))) {
                    String todoSuffix = "\n" + c.getPrefix().getIndent();
                    c = c.withLeadingAnnotations(ListUtils.map(c.getLeadingAnnotations(), anno -> {
                        if (TypeUtils.isOfClassType(anno.getType(), ANNOTATION_CONSTRUCTOR_BINDING)) {
                            if (constructors.size() <= 1) {
                                maybeRemoveImport(ANNOTATION_CONSTRUCTOR_BINDING);
                                return null;
                            }
                            return Comments.of(new Cursor(getCursor(), anno)).comment(MOVE_CONSTRUCTOR_BINDING_TODO, Comments.Placement.BEFORE, todoSuffix);
                        }
                        return anno;
                    }));
                }
                return c;
            }

            /**
             * Removes a target annotation from a `Tree`.
             *
             * Note:
             * This may be useful in other Spring recipes to remove specific annotations
             * based on a set of criteria.
             *
             * Move the visitor into a new class if it's reused.
             */
            @RequiredArgsConstructor
            class RemoveTargetAnnotation extends JavaIsoVisitor<ExecutionContext> {
                private final J.Annotation targetToRemove;

                @Override
                public J.@Nullable Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    if (targetToRemove == annotation) {
                        return null;
                    }
                    return super.visitAnnotation(annotation, ctx);
                }
            }
        });
    }

}
