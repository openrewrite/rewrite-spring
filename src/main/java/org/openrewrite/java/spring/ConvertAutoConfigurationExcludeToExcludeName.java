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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JContainer;
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;

@Value
@EqualsAndHashCode(callSuper = false)
public class ConvertAutoConfigurationExcludeToExcludeName extends Recipe {

    private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
    private static final String ENABLE_AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";
    // `@SpringBootApplication` is meta-annotated with `@EnableAutoConfiguration`, so meta-annotation
    // matching lets a single matcher cover both annotations.
    private static final AnnotationMatcher EAC_MATCHER = new AnnotationMatcher(ENABLE_AUTO_CONFIGURATION, true);

    @Option(displayName = "Fully qualified name",
            description = "The fully qualified name of the auto-configuration class to move from the " +
                    "`exclude` attribute (as a class literal) to the `excludeName` attribute (as a string literal).",
            example = "org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration")
    String fullyQualifiedName;

    String displayName = "Convert auto-configuration `exclude` to `excludeName`";

    String description = "Rewrite a class literal in the `exclude` attribute of `@SpringBootApplication` or " +
            "`@EnableAutoConfiguration` to a string literal in the `excludeName` attribute. Useful when the " +
            "excluded auto-configuration is not on the compile classpath (for example because it became " +
            "package-private in a newer version of its library). If the target was the last entry in " +
            "`exclude`, that attribute is removed. If `excludeName` already contains the value, no duplicate " +
            "is added.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                        new UsesType<>(SPRING_BOOT_APPLICATION, true),
                        new UsesType<>(ENABLE_AUTO_CONFIGURATION, true)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (!EAC_MATCHER.matches(a) || !hasTargetInExclude(a)) {
                            return a;
                        }

                        boolean[] mergedIntoExisting = {false};
                        List<Expression> newArgs = ListUtils.map(a.getArguments(), arg -> {
                            if (!(arg instanceof J.Assignment)) {
                                return arg;
                            }
                            J.Assignment as = (J.Assignment) arg;
                            if (!(as.getVariable() instanceof J.Identifier)) {
                                return arg;
                            }
                            String name = ((J.Identifier) as.getVariable()).getSimpleName();
                            if ("exclude".equals(name)) {
                                return trimExclusion(arg, as);
                            }
                            if ("excludeName".equals(name)) {
                                mergedIntoExisting[0] = true;
                                return mergeIntoExcludeName(as);
                            }
                            return arg;
                        });

                        if (!mergedIntoExisting[0]) {
                            newArgs = ListUtils.concat(newArgs, buildExcludeNameAssignment(!newArgs.isEmpty()));
                        }
                        maybeRemoveImport(fullyQualifiedName);
                        return a.withArguments(newArgs);
                    }

                    private boolean hasTargetInExclude(J.Annotation a) {
                        if (a.getArguments() == null) {
                            return false;
                        }
                        for (Expression arg : a.getArguments()) {
                            if (!(arg instanceof J.Assignment)) {
                                continue;
                            }
                            J.Assignment as = (J.Assignment) arg;
                            if (!(as.getVariable() instanceof J.Identifier) ||
                                    !"exclude".equals(((J.Identifier) as.getVariable()).getSimpleName())) {
                                continue;
                            }
                            Expression value = as.getAssignment();
                            if (isTargetClassReference(value)) {
                                return true;
                            }
                            if (value instanceof J.NewArray) {
                                List<Expression> init = ((J.NewArray) value).getInitializer();
                                if (init != null) {
                                    for (Expression e : init) {
                                        if (isTargetClassReference(e)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                        return false;
                    }

                    private Expression trimExclusion(Expression original, J.Assignment as) {
                        if (isTargetClassReference(as.getAssignment())) {
                            return null;
                        }
                        if (as.getAssignment() instanceof J.NewArray) {
                            J.NewArray array = (J.NewArray) as.getAssignment();
                            JContainer<Expression> initContainer = array.getPadding().getInitializer();
                            if (initContainer == null) {
                                return original;
                            }
                            List<JRightPadded<Expression>> padded = initContainer.getPadding().getElements();
                            if (padded.isEmpty()) {
                                return original;
                            }
                            List<JRightPadded<Expression>> newPadded = ListUtils.map(padded,
                                    rp -> isTargetClassReference(rp.getElement()) ? null : rp);
                            //noinspection DataFlowIssue
                            if (newPadded.isEmpty()) {
                                return null;
                            }
                            if (newPadded.size() == padded.size()) {
                                return original;
                            }
                            JRightPadded<Expression> firstOrig = padded.get(0);
                            JRightPadded<Expression> lastOrig = padded.get(padded.size() - 1);
                            newPadded = ListUtils.mapFirst(newPadded,
                                    rp -> rp.withElement(rp.getElement().withPrefix(firstOrig.getElement().getPrefix())));
                            newPadded = ListUtils.mapLast(newPadded, rp -> rp.withAfter(lastOrig.getAfter()));
                            return as.withAssignment(array.getPadding().withInitializer(
                                    initContainer.getPadding().withElements(newPadded)));
                        }
                        return original;
                    }

                    private Expression mergeIntoExcludeName(J.Assignment as) {
                        Expression rhs = as.getAssignment();
                        if (rhs instanceof J.Literal) {
                            J.Literal literal = (J.Literal) rhs;
                            if (fullyQualifiedName.equals(literal.getValue())) {
                                return as;
                            }
                            List<JRightPadded<Expression>> elements = new ArrayList<>();
                            elements.add(JRightPadded.build((Expression) literal.withPrefix(Space.EMPTY)));
                            elements.add(JRightPadded.build((Expression) newStringLiteral(Space.SINGLE_SPACE)));
                            J.NewArray array = new J.NewArray(
                                    Tree.randomId(), Space.SINGLE_SPACE, Markers.EMPTY,
                                    null, emptyList(),
                                    JContainer.build(Space.EMPTY, elements, Markers.EMPTY),
                                    null);
                            return as.withAssignment(array);
                        }
                        if (rhs instanceof J.NewArray) {
                            J.NewArray array = (J.NewArray) rhs;
                            List<Expression> init = array.getInitializer();
                            if (init != null) {
                                for (Expression e : init) {
                                    if (e instanceof J.Literal && fullyQualifiedName.equals(((J.Literal) e).getValue())) {
                                        return as;
                                    }
                                }
                            }
                            JContainer<Expression> initContainer = array.getPadding().getInitializer();
                            if (initContainer == null) {
                                return as;
                            }
                            List<JRightPadded<Expression>> padded = new ArrayList<>(initContainer.getPadding().getElements());
                            Space prefixBeforeNewElement = padded.isEmpty() ? Space.EMPTY : Space.SINGLE_SPACE;
                            // Transfer any trailing space (before `}`) from the current last element to the appended one.
                            Space trailingSpace = Space.EMPTY;
                            if (!padded.isEmpty()) {
                                JRightPadded<Expression> last = padded.get(padded.size() - 1);
                                trailingSpace = last.getAfter();
                                padded.set(padded.size() - 1, last.withAfter(Space.EMPTY));
                            }
                            padded.add(JRightPadded.build((Expression) newStringLiteral(prefixBeforeNewElement))
                                    .withAfter(trailingSpace));
                            return as.withAssignment(array.getPadding().withInitializer(
                                    initContainer.getPadding().withElements(padded)));
                        }
                        return as;
                    }

                    private J.Assignment buildExcludeNameAssignment(boolean followsOtherArg) {
                        J.Identifier ident = new J.Identifier(
                                Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                                emptyList(), "excludeName", null, null);
                        JLeftPadded<Expression> rhs = JLeftPadded.<Expression>build(newStringLiteral(Space.SINGLE_SPACE))
                                .withBefore(Space.SINGLE_SPACE);
                        return new J.Assignment(
                                Tree.randomId(),
                                followsOtherArg ? Space.SINGLE_SPACE : Space.EMPTY,
                                Markers.EMPTY, ident, rhs, null);
                    }

                    private J.Literal newStringLiteral(Space prefix) {
                        return new J.Literal(
                                Tree.randomId(), prefix, Markers.EMPTY,
                                fullyQualifiedName, "\"" + fullyQualifiedName + "\"",
                                null, JavaType.Primitive.String);
                    }

                    private boolean isTargetClassReference(Expression expr) {
                        return expr instanceof J.FieldAccess &&
                                TypeUtils.isAssignableTo(fullyQualifiedName, ((J.FieldAccess) expr).getTarget().getType());
                    }
                });
    }
}
