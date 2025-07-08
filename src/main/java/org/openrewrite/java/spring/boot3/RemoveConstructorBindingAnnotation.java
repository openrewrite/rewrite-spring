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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

/**
 * @author Alex Boyko
 */
public class RemoveConstructorBindingAnnotation extends Recipe {

    private static final String ANNOTATION_CONSTRUCTOR_BINDING = "org.springframework.boot.context.properties.ConstructorBinding";
    private static final String ANNOTATION_CONFIG_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

    @Override
    public String getDisplayName() {
        return "Remove Unnecessary `@ConstructorBinding`";
    }

    @Override
    public String getDescription() {
        return "As of Boot 3.0 `@ConstructorBinding` is no longer needed at the type level on `@ConfigurationProperties` classes and should be removed.";
    }

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
                        .collect(Collectors.toList());

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
                                        return new RemoveTargetAnnotation(bindingAnnotation.get()).visitMethodDeclaration(m, ctx);
                                    }
                                    return s;
                                }))
                        );
                    }
                }

                if (c.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), ANNOTATION_CONFIG_PROPERTIES))) {
                    c = c.withLeadingAnnotations(ListUtils.map(c.getLeadingAnnotations(), anno -> {
                        if (TypeUtils.isOfClassType(anno.getType(), ANNOTATION_CONSTRUCTOR_BINDING)) {
                            if (constructors.size() <= 1) {
                                maybeRemoveImport(ANNOTATION_CONSTRUCTOR_BINDING);
                                return null;
                            }
                            return anno.withComments(maybeAddJavaDoc(anno.getComments(), anno.getPrefix().getIndent()));
                        }
                        return anno;
                    }));
                }
                return c;
            }

            /**
             * Adds the target Javadoc if it does not exist in the list of comments.
             * Generates a properly structured Javadoc to enable autoformatting features
             * like {@link org.openrewrite.xml.format.NormalizeLineBreaks}.
             */
            private List<Comment> maybeAddJavaDoc(List<Comment> comments, String indent) {
                String message = "You need to remove ConstructorBinding on class level and move it to appropriate";
                if (comments.isEmpty() || comments.stream()
                        .filter(org.openrewrite.java.tree.Javadoc.DocComment.class::isInstance)
                        .map(org.openrewrite.java.tree.Javadoc.DocComment.class::cast)
                        .flatMap(o -> o.getBody().stream().filter(org.openrewrite.java.tree.Javadoc.Text.class::isInstance))
                        .noneMatch(o -> o.print(getCursor()).equals(message))) {

                    List<Javadoc> javadoc = new ArrayList<>();
                    javadoc.add(new Javadoc.LineBreak(randomId(), "\n" + indent + " * ", Markers.EMPTY));
                    javadoc.add(new Javadoc.Text(randomId(), Markers.EMPTY, "TODO:"));
                    javadoc.add(new Javadoc.LineBreak(randomId(), "\n" + indent + " * ", Markers.EMPTY));
                    javadoc.add(new Javadoc.Text(randomId(), Markers.EMPTY, message));
                    javadoc.add(new Javadoc.LineBreak(randomId(), "\n" + indent + " * ", Markers.EMPTY));
                    javadoc.add(new Javadoc.Text(randomId(), Markers.EMPTY, "constructor."));
                    javadoc.add(new Javadoc.LineBreak(randomId(), "\n" + indent + " ", Markers.EMPTY));

                    List<Comment> newComments = new ArrayList<>(comments);
                    newComments.add(new Javadoc.DocComment(randomId(), Markers.EMPTY, javadoc, "\n" + indent));
                    return newComments;
                }
                return comments;
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
            class RemoveTargetAnnotation extends JavaIsoVisitor<ExecutionContext> {
                private final J.Annotation targetToRemove;

                public RemoveTargetAnnotation(J.Annotation targetToRemove) {
                    this.targetToRemove = targetToRemove;
                }

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
