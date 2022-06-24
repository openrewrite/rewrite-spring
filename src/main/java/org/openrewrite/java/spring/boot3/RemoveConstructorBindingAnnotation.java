/*
 * Copyright 2022 the original author or authors.
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
import org.openrewrite.Recipe;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

/**
 * @author Alex Boyko
 */
public class RemoveConstructorBindingAnnotation extends Recipe {

    private static final String ANNOTATION_CONSTRUCTOR_BINDING = "org.springframework.boot.context.properties.ConstructorBinding";
    private static final String ANNOTATION_CONFIG_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

    @Override
    public String getDisplayName() {
        return "Remove Unnecessary @ConstructorBinding";
    }

    @Override
    public String getDescription() {
        return "As of Boot 3.0 @ConstructorBinding is no longer needed at the type level on @ConfigurationProperties classes and should be removed.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private J.MethodDeclaration constructorToUpdate;
            private J.Annotation annotationToComment;
            private int numberOfConstructors;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext context) {
                findConstructorToUpdate(classDecl);
                findAnnotationToComment(classDecl);
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, context);
                if (classDecl.getLeadingAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), ANNOTATION_CONFIG_PROPERTIES))) {
                    c = c.withLeadingAnnotations(ListUtils.map(c.getLeadingAnnotations(), anno -> {
                        if (TypeUtils.isOfClassType(anno.getType(), ANNOTATION_CONSTRUCTOR_BINDING)) {
                            annotationToComment = anno;
                        }
                        if (
                                TypeUtils.isOfClassType(anno.getType(), ANNOTATION_CONSTRUCTOR_BINDING)
                                && numberOfConstructors <= 1
                        ) {
                            maybeRemoveImport(ANNOTATION_CONSTRUCTOR_BINDING);
                            return null;
                        }
                        return anno;
                    }));
                }
                return c;
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext executionContext) {
                J.Annotation annotation1 = super.visitAnnotation(annotation, executionContext);

                if(numberOfConstructors > 1 && annotation == annotationToComment) {
                    List<Comment> list  = new ArrayList<>();
                    Javadoc.Text javadoc = new Javadoc.Text(UUID.randomUUID(), Markers.EMPTY, "\n" +
                            " * TODO:\n" +
                            " * You need to remove ConstructorBinding on class level and move it to appropriate\n" +
                            " * constructor.\n" +
                            " * ");
                    list.add(new Javadoc.DocComment(UUID.randomUUID(), Markers.EMPTY, Collections.singletonList(javadoc), "\n"));

                    return annotation1.withComments(list);
                }

                return annotation1;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, executionContext);
                if (method == constructorToUpdate) {
                    List<J.Annotation> annotations = ListUtils.map(method.getLeadingAnnotations(), anno -> {
                        if (TypeUtils.isOfClassType(anno.getType(), ANNOTATION_CONSTRUCTOR_BINDING)) {
                            maybeRemoveImport(ANNOTATION_CONSTRUCTOR_BINDING);
                            return null;
                        }
                        return anno;
                    });
                    methodDeclaration = methodDeclaration.withLeadingAnnotations(annotations);
                }
                return methodDeclaration;
            }

            private void findConstructorToUpdate(J.ClassDeclaration c) {
                int constructorsCount = 0;
                J.MethodDeclaration annotatedConstructor = null;
                for (Statement s : c.getBody().getStatements()) {
                    if (!(s instanceof J.MethodDeclaration)) {
                        continue;
                    }
                    J.MethodDeclaration method = (J.MethodDeclaration) s;
                    if (method.isConstructor()) {
                        constructorsCount++;
                    }
                    if (method.getAllAnnotations().stream().anyMatch(a -> TypeUtils.isOfClassType(a.getType(), ANNOTATION_CONSTRUCTOR_BINDING))) {
                        annotatedConstructor = method;
                    }

                    this.numberOfConstructors = constructorsCount;
                }
                if ((constructorsCount == 1) && (annotatedConstructor != null)) {
                    this.constructorToUpdate = annotatedConstructor;
                }
            }

            private void findAnnotationToComment(J.ClassDeclaration classDecl) {
                for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                    if (TypeUtils.isOfClassType(annotation.getType(), ANNOTATION_CONSTRUCTOR_BINDING)) {
                        annotationToComment = annotation;
                    }
                }
            }
        };
    }
}
