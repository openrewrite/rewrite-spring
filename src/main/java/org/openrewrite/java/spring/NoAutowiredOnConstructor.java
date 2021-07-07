/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.spring;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes the Autowired annotation from a bean's constructor when there is only one constructor.
 */
public class NoAutowiredOnConstructor extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove the `@Autowired` annotation on inferred constructor";
    }

    @Override
    public String getDescription() {
        return "Spring can infer an autowired constructor when there is a single constructor on the bean.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.beans.factory.annotation.Autowired");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NoAutowiredOnConstructor.NoAutowiredOnConstructorVisitor();
    }

    private static class NoAutowiredOnConstructorVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REQUIRED_AUTOWIRED_ANNOTATION_MATCHER =
                new AnnotationMatcher("@org.springframework.beans.factory.annotation.Autowired(true)");

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            int constructorCount = 0;

            for (Statement s : classDecl.getBody().getStatements()) {
                if (s instanceof J.MethodDeclaration) {
                    if (((J.MethodDeclaration) s).isConstructor()) {
                        constructorCount++;
                        getCursor().putMessage("METHOD_DECLARATION_KEY", s);
                    }
                }
            }

            if (constructorCount == 1) {
                return super.visitClassDeclaration(classDecl, executionContext);
            }

            return classDecl;
        }

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);

            Cursor cursor = getCursorToParentScope(getCursor());
            if (cursor.getValue() instanceof J.MethodDeclaration) {
                Cursor classDeclarationCursor = cursor.dropParentUntil(J.ClassDeclaration.class::isInstance);
                J.MethodDeclaration m = classDeclarationCursor.getMessage("METHOD_DECLARATION_KEY");
                if (cursor.getValue().equals(m)) {
                    if (REQUIRED_AUTOWIRED_ANNOTATION_MATCHER.matches(a)) {
                        cursor.putMessage("ANNOTATION_REMOVED_KEY", a);
                        maybeRemoveImport(TypeUtils.asFullyQualified(a.getType()));
                        //noinspection ConstantConditions
                        return null;
                    }
                }
            }
            return a;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
            J.Annotation removedAnnotation = getCursor().pollMessage("ANNOTATION_REMOVED_KEY");

            List<J.Annotation> leadingAnnotations = method.getLeadingAnnotations();
            if (removedAnnotation != null && !leadingAnnotations.isEmpty()) {
                if (leadingAnnotations.size() == 1 && leadingAnnotations.get(0) == removedAnnotation) {
                    if (!m.getModifiers().isEmpty()) {
                        m = m.withModifiers(Space.formatFirstPrefix(m.getModifiers(), Space.firstPrefix(m.getModifiers()).withWhitespace("")));
                    } else {
                        m = m.withName(m.getName().withPrefix(m.getName().getPrefix().withWhitespace("")));
                    }
                } else {
                    List<J.Annotation> newLeadingAnnotations = removeAnnotationOrEmpty(leadingAnnotations, removedAnnotation);
                    if (!newLeadingAnnotations.isEmpty()) {
                        m = m.withLeadingAnnotations(newLeadingAnnotations);
                    }
                }
            }

            return m;
        }

        /* Returns a list of leading annotations with the target removed or an empty list if no changes are necessary.
         * A prefix only needs to change if the index == 0 and the prefixes of the target annotation and next annotation are not equal.
         */
        private List<J.Annotation> removeAnnotationOrEmpty(List<J.Annotation> leadingAnnotations, J.Annotation targetAnnotation) {
            int index = leadingAnnotations.indexOf(targetAnnotation);
            List<J.Annotation> newLeadingAnnotations = new ArrayList<>();
            if (index == 0) {
                J.Annotation nextAnnotation = leadingAnnotations.get(1);
                if (!nextAnnotation.getPrefix().equals(targetAnnotation.getPrefix())) {
                    newLeadingAnnotations.add(nextAnnotation.withPrefix(targetAnnotation.getPrefix()));
                    for (int i = 2; i < leadingAnnotations.size(); ++i) {
                        newLeadingAnnotations.add(leadingAnnotations.get(i));
                    }
                }
            }
            return newLeadingAnnotations;
        }

        /**
         * Returns either the current block or a J.Type that may create a reference to a variable.
         * I.E. for(int target = 0; target < N; target++) creates a new name scope for `target`.
         * The name scope in the next J.Block `{}` cannot create new variables with the name `target`.
         * <p>
         * J.* types that may only reference an existing name and do not create a new name scope are excluded.
         */
        private static Cursor getCursorToParentScope(Cursor cursor) {
            return cursor.dropParentUntil(is ->
                    is instanceof J.CompilationUnit ||
                            is instanceof J.ClassDeclaration ||
                            is instanceof J.Block ||
                            is instanceof J.MethodDeclaration
            );
        }
    }
}
