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
package org.openrewrite.java.spring;

import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

public class AutowiredFieldsIntoRequiredArgsConstructor extends Recipe {
    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String REQUIRED_ARGS_CONSTRUCTOR = "lombok.RequiredArgsConstructor";
    private static final AnnotationMatcher AUTOWIRED_MATCHER = new AnnotationMatcher("@" + AUTOWIRED);

    @Getter
    final String displayName = "Replace `@Autowired` field injection with Lombok's `@RequiredArgsConstructor`";

    @Getter
    final String description = "Removes `@Autowired` from fields, makes them `final`, and adds Lombok's " +
            "`@RequiredArgsConstructor` annotation to generate the constructor instead of relying on field injection. " +
            "Only applies to classes where every other field is either `static` or an already-initialized `final` " +
            "field, so that the constructor Lombok generates ends up with exactly the parameters that were " +
            "previously `@Autowired`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(AUTOWIRED, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                if (cd.getKind() != J.ClassDeclaration.Kind.Type.Class ||
                        !FindAnnotations.find(cd, "@lombok.*Constructor").isEmpty()) {
                    return cd;
                }

                List<Statement> statements = cd.getBody().getStatements();
                List<J.VariableDeclarations> fields = statements.stream()
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .collect(toList());

                Set<String> autowiredFieldNames = fields.stream()
                        .filter(vd -> vd.getVariables().size() == 1 &&
                                service(AnnotationService.class).isAnnotatedWith(vd, AUTOWIRED))
                        .map(vd -> vd.getVariables().get(0).getSimpleName())
                        .collect(Collectors.toCollection(HashSet::new));
                if (autowiredFieldNames.isEmpty()) {
                    return cd;
                }

                boolean allOtherFieldsSafe = fields.stream()
                        .filter(vd -> vd.getVariables().size() != 1 ||
                                !autowiredFieldNames.contains(vd.getVariables().get(0).getSimpleName()))
                        .allMatch(vd -> isStatic(vd) || (isFinal(vd) && isInitialized(vd)));
                if (!allOtherFieldsSafe) {
                    return cd;
                }

                List<J.MethodDeclaration> constructors = statements.stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(J.MethodDeclaration::isConstructor)
                        .collect(toList());
                if (constructors.size() > 1) {
                    return cd;
                }
                J.MethodDeclaration redundantConstructor = null;
                if (constructors.size() == 1) {
                    if (!isEmptyNoArgConstructor(constructors.get(0))) {
                        return cd;
                    }
                    redundantConstructor = constructors.get(0);
                }

                Cursor bodyCursor = new Cursor(getCursor(), cd.getBody());
                J.MethodDeclaration toRemove = redundantConstructor;
                List<Statement> newStatements = ListUtils.map(statements, s -> {
                    if (s == toRemove) {
                        return null;
                    }
                    if (s instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) s;
                        if (vd.getVariables().size() == 1 &&
                                autowiredFieldNames.contains(vd.getVariables().get(0).getSimpleName())) {
                            return removeAutowiredAndMakeFinal(vd, ctx, bodyCursor);
                        }
                    }
                    return s;
                });

                maybeRemoveImport(AUTOWIRED);
                cd = cd.withBody(cd.getBody().withStatements(newStatements));

                maybeAddImport(REQUIRED_ARGS_CONSTRUCTOR);
                return JavaTemplate.builder("@RequiredArgsConstructor")
                        .imports(REQUIRED_ARGS_CONSTRUCTOR)
                        .javaParser(JavaParser.fromJavaVersion().dependsOn(
                                "package lombok;\n" +
                                        "public @interface RequiredArgsConstructor {}"))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation(comparing(J.Annotation::getSimpleName)));
            }
        });
    }

    private static J.VariableDeclarations removeAutowiredAndMakeFinal(J.VariableDeclarations vd, ExecutionContext ctx, Cursor parent) {
        J.VariableDeclarations mv = (J.VariableDeclarations) new RemoveAnnotationVisitor(AUTOWIRED_MATCHER).visit(vd, ctx, parent);
        if (mv == null || mv.getTypeExpression() == null) {
            return vd;
        }
        if (mv.getTypeExpression() instanceof J.AnnotatedType &&
                ((J.AnnotatedType) mv.getTypeExpression()).getAnnotations().isEmpty()) {
            J.AnnotatedType annotatedType = (J.AnnotatedType) mv.getTypeExpression();
            mv = mv.withTypeExpression(annotatedType.getTypeExpression().withPrefix(annotatedType.getPrefix()));
        }
        if (mv.getModifiers().stream().noneMatch(m -> m.getType() == J.Modifier.Type.Final)) {
            Space prefix = Space.firstPrefix(mv.getVariables());
            J.Modifier finalModifier = new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, null, J.Modifier.Type.Final, emptyList());
            if (mv.getModifiers().isEmpty()) {
                mv = mv.withTypeExpression(mv.getTypeExpression().withPrefix(prefix));
            } else {
                finalModifier = finalModifier.withPrefix(prefix);
            }
            mv = mv.withModifiers(ListUtils.concat(mv.getModifiers(), finalModifier));
        }
        return mv;
    }

    private static boolean isStatic(J.VariableDeclarations vd) {
        return vd.getModifiers().stream().anyMatch(m -> m.getType() == J.Modifier.Type.Static);
    }

    private static boolean isFinal(J.VariableDeclarations vd) {
        return vd.getModifiers().stream().anyMatch(m -> m.getType() == J.Modifier.Type.Final);
    }

    private static boolean isInitialized(J.VariableDeclarations vd) {
        return vd.getVariables().stream().allMatch(v -> v.getInitializer() != null);
    }

    private static boolean isEmptyNoArgConstructor(J.MethodDeclaration md) {
        return md.getParameters().stream().allMatch(J.Empty.class::isInstance) &&
                md.getBody() != null && md.getBody().getStatements().isEmpty();
    }
}
