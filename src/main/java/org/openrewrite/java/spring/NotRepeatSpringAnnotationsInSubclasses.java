/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.java.spring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class NotRepeatSpringAnnotationsInSubclasses extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove Spring annotations if they repeating in subclasses";
    }

    @Override
    public String getDescription() {
        return "Remove Spring annotations in subclasses if they present in base classes.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        //return Preconditions.check(new UsesType<>("org.springframework.web.bind.annotation.PostMapping", false), new JavaIsoVisitor<ExecutionContext>() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                Optional<JavaType.Method> overriddenMethod = TypeUtils.findOverriddenMethod(md.getMethodType());
                if (overriddenMethod.isPresent()) {

                    JavaType.Method overrideMethod = overriddenMethod.get();

                    List<JavaType.FullyQualified> baseAnnotations = overrideMethod.getAnnotations();
                    List<JavaType.FullyQualified> methodAnnotations = md.getMethodType().getAnnotations();
                    List<JavaType.FullyQualified> repeated = methodAnnotations.stream()
                            .filter(a -> baseAnnotations.stream().anyMatch(b -> TypeUtils.isOfType(a, b)))
                            .collect(Collectors.toList());

                    List<J.Annotation> annotations = ListUtils.map(md.getLeadingAnnotations(),
                            a -> {
                                if (repeated.stream().anyMatch(n -> TypeUtils.isOfType(a.getType(), ((JavaType.Annotation) n).getType()))) {
                                    return (J.Annotation) new RemoveAnnotation(a.getType().toString()).getVisitor().visit(a, ctx, getCursor().getParentOrThrow());
                                }
                                return a;
                            });
                    md = md.withLeadingAnnotations(annotations);

                    repeated.forEach(this::maybeRemoveImport);
                }
                return md;
            }
        };
    }
}
