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
package org.openrewrite.java.spring.data;

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.EnumSet;
import java.util.Set;

import static java.util.Collections.emptyList;

public class MigratePageRequestAndSort extends Recipe {

    private static final MethodMatcher PAGE_REQUEST =
            new MethodMatcher("org.springframework.data.domain.PageRequest <constructor>(..)");
    private static final MethodMatcher SORT =
            new MethodMatcher("org.springframework.data.domain.Sort <constructor>(..)");
    private static final MethodMatcher SORT_OF_PROPERTY_LIST =
            new MethodMatcher("org.springframework.data.domain.Sort <constructor>(org.springframework.data.domain.Sort.Direction, java.util.List)");
    private static final MethodMatcher SORT_ORDER_OF_PROPERTY =
            new MethodMatcher("org.springframework.data.domain.Sort.Order <constructor>(java.lang.String)");

    @Getter
    final String displayName = "Use `PageRequest.of(..)` and `Sort.by(..)`";

    @Getter
    final String description = "Spring Data 2.0 deprecated the constructors of `PageRequest` and `Sort`, and " +
            "`new Sort.Order(property)`, in favor of static factory methods, and they are no longer accessible " +
            "as of Spring Data 2.2. `new Sort(direction, list)` has no factory method that takes a `List`, so its " +
            "properties become an array. A `Sort` or `Sort.Direction` that is `null` at runtime is left for the " +
            "application to handle: the constructors accepted them on Spring Data 1.x, and the factory methods reject " +
            "them by 2.7. Subclasses keep their constructors, constructor references such as `Sort.Order::new` are " +
            "left for the compiler to point out, and only Java sources are migrated.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                new UsesMethod<>(PAGE_REQUEST),
                new UsesMethod<>(SORT),
                new UsesMethod<>(SORT_ORDER_OF_PROPERTY)), new JavaVisitor<ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof J.CompilationUnit;
            }

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass nc = (J.NewClass) super.visitNewClass(newClass, ctx);
                String factoryMethod = PAGE_REQUEST.matches(nc) ? "of" :
                        SORT.matches(nc) || SORT_ORDER_OF_PROPERTY.matches(nc) ? "by" : null;
                if (factoryMethod == null || !(nc.getClazz() instanceof Expression)) {
                    return nc;
                }
                JavaType.Method constructor = nc.getMethodType();
                boolean listOfProperties = SORT_OF_PROPERTY_LIST.matches(nc);
                if (listOfProperties) {
                    nc = nc.withArguments(ListUtils.mapLast(nc.getArguments(), this::toArray));
                    constructor = constructor.withParameterTypes(ListUtils.mapLast(constructor.getParameterTypes(), list ->
                            new JavaType.Array(null, JavaType.ShallowClass.build("java.lang.String"), null)));
                }
                Set<Flag> flags = EnumSet.of(Flag.Public, Flag.Static);
                if (listOfProperties || constructor.hasFlags(Flag.Varargs)) {
                    flags.add(Flag.Varargs);
                }
                // The arguments move over as they are, so that they keep their formatting and any comments between them
                JavaType.Method type = constructor
                        .withName(factoryMethod)
                        .withReturnType(constructor.getDeclaringType())
                        .withFlags(flags);
                return new J.MethodInvocation(Tree.randomId(), nc.getPrefix(), Markers.EMPTY,
                        JRightPadded.build(((Expression) nc.getClazz()).withPrefix(Space.EMPTY)), null,
                        new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, emptyList(), factoryMethod, type, null),
                        nc.getPadding().getArguments(), type);
            }

            private Expression toArray(Expression properties) {
                Expression array = JavaTemplate.builder("#{any(java.util.List)}.toArray(new String[0])")
                        .build()
                        .apply(new Cursor(getCursor(), properties), properties.getCoordinates().replace(), properties);
                return array.withPrefix(properties.getPrefix());
            }
        });
    }
}
