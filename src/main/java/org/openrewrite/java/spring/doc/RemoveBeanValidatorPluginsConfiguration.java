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
package org.openrewrite.java.spring.doc;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoveBeanValidatorPluginsConfiguration extends Recipe {

    private static final String ANNOTATION_IMPORT = "org.springframework.context.annotation.Import";
    private static final AnnotationMatcher IMPORT_MATCHER = new AnnotationMatcher("@" + ANNOTATION_IMPORT);
    private static final String BEAN_VALIDATOR_PLUGINS_CONFIGURATION = "springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration";
    private static final TypeMatcher BEAN_VALIDATOR_TYPEMATCHER = new TypeMatcher("springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration");

    @Getter
    final String displayName = "Removes @Import(BeanValidatorPluginsConfiguration.class)";

    @Getter
    final String description = "As Springdoc OpenAPI supports Bean Validation out of the box, the BeanValidatorPluginsConfiguration is no longer supported nor needed. " +
            "Thus remove @Import(BeanValidatorPluginsConfiguration.class).";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesType<>(ANNOTATION_IMPORT, false),
                new UsesType<>(BEAN_VALIDATOR_PLUGINS_CONFIGURATION, false)
                ), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);


                AtomicBoolean changed = new AtomicBoolean(false);
                List<J.Annotation> leadingAnnotations = new ArrayList<>();
                for (J.Annotation a : c.getLeadingAnnotations()) {
                    if (a.getArguments() != null && IMPORT_MATCHER.matches(a)) {
                        if (a.getArguments().size() == 1 && isBeanValidator(a.getArguments().get(0))) {
                            maybeRemoveImport(ANNOTATION_IMPORT);
                            maybeRemoveImport(BEAN_VALIDATOR_PLUGINS_CONFIGURATION);
                            return (J.ClassDeclaration) new RemoveAnnotationVisitor(IMPORT_MATCHER).visitNonNull(c, ctx, getCursor().getParentOrThrow());
                        }
                        leadingAnnotations.add(a.withArguments(ListUtils.map(a.getArguments(), e -> {
                            if (e instanceof J.NewArray && ((J.NewArray) e).getInitializer() != null) {
                                List<Expression> initializer = ((J.NewArray) e).getInitializer();
                                for (Expression ex : initializer) {
                                    if (isBeanValidator(ex)) {
                                        changed.set(true);
                                        return ((J.NewArray) e).withInitializer(ListUtils.filter(initializer, it -> it != ex));
                                    }
                                }
                            }
                            return e;
                        })));
                    } else {
                        leadingAnnotations.add(a);
                    }
                }

                if (changed.get()) {
                    maybeRemoveImport(ANNOTATION_IMPORT);
                    maybeRemoveImport(BEAN_VALIDATOR_PLUGINS_CONFIGURATION);
                    return c.withLeadingAnnotations(leadingAnnotations);
                }
                return c;
            }

            private boolean isBeanValidator(Expression e) {
                if (e instanceof J.NewArray && ((J.NewArray) e).getInitializer() != null && ((J.NewArray) e).getInitializer().size() == 1) {
                     e = ((J.NewArray) e).getInitializer().get(0);
                }
                return e.getType() instanceof JavaType.Parameterized && BEAN_VALIDATOR_TYPEMATCHER.matches(((JavaType.Parameterized) e.getType()).getTypeParameters().get(0));
            }
        });
    }
}
