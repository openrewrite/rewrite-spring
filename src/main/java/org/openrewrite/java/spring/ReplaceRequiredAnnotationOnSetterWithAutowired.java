/*
 * Copyright 2024 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import java.util.Comparator;

public class ReplaceRequiredAnnotationOnSetterWithAutowired extends Recipe {

    public static final String ANNOTATION_REQUIRED_FQN = "org.springframework.beans.factory.annotation.Required";

    @Override
    public String getDisplayName() {
        return "Replace `@Required` annotation on setter with `@Autowired`";
    }

    @Override
    public String getDescription() {
        return "Replace setter methods annotated with `@Required` with `@Autowired`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ANNOTATION_REQUIRED_FQN, false),
                new ReplaceRequiredAnnotationVisitor());
    }

    private static class ReplaceRequiredAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (FindAnnotations.find(method, ANNOTATION_REQUIRED_FQN).isEmpty()) {
                return method;
            }

            J.MethodDeclaration md = (J.MethodDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + ANNOTATION_REQUIRED_FQN))
                    .visit(method, ctx, getCursor().getParentOrThrow());
            if (md != null) {
                md = JavaTemplate.builder("@org.springframework.beans.factory.annotation.Autowired")
                        .javaParser(JavaParser.fromJavaVersion().dependsOn("package org.springframework.beans.factory.annotation;public interface Autowired {}"))
                        .build().apply(updateCursor(md), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

                maybeRemoveImport(ANNOTATION_REQUIRED_FQN);
                doAfterVisit(ShortenFullyQualifiedTypeReferences.modifyOnly(md));
                return md;
            }
            return method;
        }
    }
}
