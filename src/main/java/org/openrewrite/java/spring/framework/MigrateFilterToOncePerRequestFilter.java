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
package org.openrewrite.java.spring.framework;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

/**
 * Migrates classes that implement {@code javax.servlet.Filter} (or {@code jakarta.servlet.Filter})
 * to extend {@code org.springframework.web.filter.OncePerRequestFilter}.
 * <p>
 * This transformation includes:
 * <ul>
 *   <li>Changing {@code implements Filter} to {@code extends OncePerRequestFilter}</li>
 *   <li>Renaming {@code doFilter} to {@code doFilterInternal}</li>
 *   <li>Changing visibility from public to protected</li>
 *   <li>Changing parameter types from ServletRequest/ServletResponse to HttpServletRequest/HttpServletResponse</li>
 *   <li>Removing redundant casts to HttpServletRequest/HttpServletResponse</li>
 *   <li>Removing empty init() and destroy() methods</li>
 * </ul>
 */
public class MigrateFilterToOncePerRequestFilter extends Recipe {

    private static final String JAVAX_FILTER = "javax.servlet.Filter";
    private static final String JAKARTA_FILTER = "jakarta.servlet.Filter";
    private static final String ONCE_PER_REQUEST_FILTER = "org.springframework.web.filter.OncePerRequestFilter";

    private static final MethodMatcher JAVAX_DO_FILTER = new MethodMatcher(
            "* doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)");
    private static final MethodMatcher JAKARTA_DO_FILTER = new MethodMatcher(
            "* doFilter(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse, jakarta.servlet.FilterChain)");

    private static final MethodMatcher JAVAX_INIT = new MethodMatcher("* init(javax.servlet.FilterConfig)");
    private static final MethodMatcher JAKARTA_INIT = new MethodMatcher("* init(jakarta.servlet.FilterConfig)");
    private static final MethodMatcher JAVAX_DESTROY = new MethodMatcher("* destroy()");
    private static final MethodMatcher JAKARTA_DESTROY = new MethodMatcher("* destroy()");

    @Getter
    final String displayName = "Migrate `Filter` to `OncePerRequestFilter`";

    @Getter
    final String description = "Migrates classes that implement `javax.servlet.Filter` (or `jakarta.servlet.Filter`) " +
            "to extend `org.springframework.web.filter.OncePerRequestFilter`. This transformation renames `doFilter` " +
            "to `doFilterInternal`, changes parameter types to HTTP variants, removes manual casting, and removes " +
            "empty `init()` and `destroy()` methods.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(JAVAX_FILTER, false),
                        new UsesType<>(JAKARTA_FILTER, false)
                ),
                new JavaVisitor<ExecutionContext>() {
                    private boolean isJakarta;

                    @Override
                    public J visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                        J.ClassDeclaration cd = (J.ClassDeclaration) super.visitClassDeclaration(classDecl, ctx);

                        // Check if this class directly implements Filter
                        if (cd.getImplements() == null) {
                            return cd;
                        }

                        TypeTree filterImplements = null;
                        for (TypeTree impl : cd.getImplements()) {
                            if (TypeUtils.isOfClassType(impl.getType(), JAVAX_FILTER)) {
                                filterImplements = impl;
                                isJakarta = false;
                                break;
                            } else if (TypeUtils.isOfClassType(impl.getType(), JAKARTA_FILTER)) {
                                filterImplements = impl;
                                isJakarta = true;
                                break;
                            }
                        }

                        if (filterImplements == null) {
                            return cd;
                        }

                        // Remove Filter from implements
                        List<TypeTree> newImplements = ListUtils.map(cd.getImplements(), impl -> {
                            if (TypeUtils.isOfClassType(impl.getType(), JAVAX_FILTER) ||
                                TypeUtils.isOfClassType(impl.getType(), JAKARTA_FILTER)) {
                                return null;
                            }
                            return impl;
                        });

                        // Add OncePerRequestFilter as extends
                        maybeRemoveImport(JAVAX_FILTER);
                        maybeRemoveImport(JAKARTA_FILTER);
                        maybeAddImport(ONCE_PER_REQUEST_FILTER);

                        TypeTree extendsType = TypeTree.build("OncePerRequestFilter")
                                .withType(JavaType.buildType(ONCE_PER_REQUEST_FILTER));

                        cd = cd.withImplements(newImplements.isEmpty() ? null : newImplements)
                                .withExtends(extendsType);

                        return autoFormat(cd, extendsType, ctx, getCursor().getParentOrThrow());
                    }

                    @Override
                    public @Nullable J visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                        J.MethodDeclaration md = (J.MethodDeclaration) super.visitMethodDeclaration(method, ctx);

                        // Check if this is the doFilter method
                        if (JAVAX_DO_FILTER.matches(md.getMethodType()) || JAKARTA_DO_FILTER.matches(md.getMethodType())) {
                            isJakarta = JAKARTA_DO_FILTER.matches(md.getMethodType());

                            String httpRequestType = isJakarta ? "jakarta.servlet.http.HttpServletRequest" : "javax.servlet.http.HttpServletRequest";
                            String httpResponseType = isJakarta ? "jakarta.servlet.http.HttpServletResponse" : "javax.servlet.http.HttpServletResponse";

                            // Update the method type with new name and parameter types
                            JavaType.Method oldMethodType = md.getMethodType();
                            if (oldMethodType != null) {
                                List<JavaType> newParamTypes = new java.util.ArrayList<>(oldMethodType.getParameterTypes());
                                if (newParamTypes.size() >= 2) {
                                    newParamTypes.set(0, JavaType.buildType(httpRequestType));
                                    newParamTypes.set(1, JavaType.buildType(httpResponseType));
                                }
                                JavaType.Method newMethodType = oldMethodType
                                        .withName("doFilterInternal")
                                        .withParameterTypes(newParamTypes);
                                md = md.withMethodType(newMethodType);
                            }

                            // Change method name to doFilterInternal
                            md = md.withName(md.getName().withSimpleName("doFilterInternal"));

                            // Change visibility to protected
                            List<J.Modifier> newModifiers = ListUtils.map(md.getModifiers(), modifier -> {
                                if (modifier.getType() == J.Modifier.Type.Public) {
                                    return modifier.withType(J.Modifier.Type.Protected);
                                }
                                return modifier;
                            });
                            md = md.withModifiers(newModifiers);

                            // Change parameter types to HTTP variants
                            md = changeParameterTypes(md, ctx);

                            // Add @Override if not present
                            if (!hasOverrideAnnotation(md)) {
                                md = addOverrideAnnotation(md);
                            }

                            // Update imports for parameter types
                            String servletRequestType = isJakarta ? "jakarta.servlet.ServletRequest" : "javax.servlet.ServletRequest";
                            String servletResponseType = isJakarta ? "jakarta.servlet.ServletResponse" : "javax.servlet.ServletResponse";

                            maybeRemoveImport(servletRequestType);
                            maybeRemoveImport(servletResponseType);
                            maybeAddImport(httpRequestType);
                            maybeAddImport(httpResponseType);

                            return md;
                        }

                        // Remove empty init() and destroy() methods
                        if ((JAVAX_INIT.matches(md.getMethodType()) || JAKARTA_INIT.matches(md.getMethodType()) ||
                             JAVAX_DESTROY.matches(md.getMethodType()) || JAKARTA_DESTROY.matches(md.getMethodType())) &&
                            isEmptyOrSuperOnly(md)) {
                            //noinspection DataFlowIssue
                            return null;
                        }

                        return md;
                    }

                    private J.MethodDeclaration changeParameterTypes(J.MethodDeclaration md, ExecutionContext ctx) {
                        String httpRequestType = isJakarta ? "jakarta.servlet.http.HttpServletRequest" : "javax.servlet.http.HttpServletRequest";
                        String httpResponseType = isJakarta ? "jakarta.servlet.http.HttpServletResponse" : "javax.servlet.http.HttpServletResponse";

                        List<Statement> newParams = ListUtils.map(md.getParameters(), (i, param) -> {
                            if (param instanceof J.VariableDeclarations) {
                                J.VariableDeclarations vd = (J.VariableDeclarations) param;
                                String newType = null;

                                if (i == 0) {
                                    newType = httpRequestType;
                                } else if (i == 1) {
                                    newType = httpResponseType;
                                }

                                if (newType != null) {
                                    String simpleName = newType.substring(newType.lastIndexOf('.') + 1);
                                    TypeTree newTypeTree = TypeTree.build(simpleName)
                                            .withType(JavaType.buildType(newType));
                                    return vd.withTypeExpression(newTypeTree);
                                }
                            }
                            return param;
                        });

                        return md.withParameters(newParams);
                    }

                    private boolean hasOverrideAnnotation(J.MethodDeclaration md) {
                        for (J.Annotation annotation : md.getLeadingAnnotations()) {
                            if (TypeUtils.isOfClassType(annotation.getType(), "java.lang.Override")) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private J.MethodDeclaration addOverrideAnnotation(J.MethodDeclaration md) {
                        // Create @Override annotation
                        J.Annotation override = new J.Annotation(
                                randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                                        "Override", JavaType.buildType("java.lang.Override"), null),
                                null
                        );

                        // Add annotation to leading annotations
                        md = md.withLeadingAnnotations(ListUtils.concat(override, md.getLeadingAnnotations()));

                        // Update the first modifier to have a newline prefix
                        if (!md.getModifiers().isEmpty()) {
                            J.Modifier firstMod = md.getModifiers().get(0);
                            Space newFirstModPrefix = md.getPrefix().withComments(emptyList());
                            md = md.withModifiers(ListUtils.mapFirst(md.getModifiers(), m -> m.withPrefix(newFirstModPrefix)));
                        }

                        return md;
                    }

                    private boolean isEmptyOrSuperOnly(J.MethodDeclaration md) {
                        if (md.getBody() == null) {
                            return false;
                        }
                        List<Statement> statements = md.getBody().getStatements();
                        if (statements.isEmpty()) {
                            return true;
                        }
                        // Check if only contains super call
                        if (statements.size() == 1) {
                            Statement stmt = statements.get(0);
                            if (stmt instanceof J.MethodInvocation) {
                                J.MethodInvocation mi = (J.MethodInvocation) stmt;
                                return mi.getSelect() instanceof J.Identifier &&
                                       "super".equals(((J.Identifier) mi.getSelect()).getSimpleName());
                            }
                        }
                        return false;
                    }
                }
        );
    }
}
