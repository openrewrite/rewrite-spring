/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.java.spring.data;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Objects;
import java.util.stream.Collectors;


public class MigrateJpaSort extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `JpaSort.of(..)`";
    }

    @Override
    public String getDescription() {
        return "Equivalent constructors in `JpaSort` were deprecated in Spring Data 2.3.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.data.jpa.domain.JpaSort");
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final String JPA_SORT_FQN = "org.springframework.data.jpa.domain.JpaSort";

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (newClass.getClazz() != null && TypeUtils.isOfClassType(newClass.getClazz().getType(), JPA_SORT_FQN) &&
                        newClass.getArguments() != null) {
                    String template = newClass.getArguments().stream()
                            .map(arg -> TypeUtils.asFullyQualified(arg.getType()))
                            .filter(Objects::nonNull)
                            .map(type -> "#{any(" + type.getFullyQualifiedName() + ")}")
                            .collect(Collectors.joining(",", "JpaSort.of(", ")"));

                    return newClass.withTemplate(
                            JavaTemplate.builder(this::getCursor, template)
                                    .imports(JPA_SORT_FQN)
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(
                                                    "package org.springframework.data.jpa.domain;" +
                                                    "import org.springframework.data.domain.Sort;" +
                                                    "import org.springframework.data.domain.Sort.Direction;" +
                                                    "import javax.persistence.metamodel.Attribute;" +
                                                    "public class JpaSort extends Sort {" +
                                                        "public static JpaSort of(Attribute<?, ?>... attributes) { return null; }" +
                                                        "public static JpaSort of(JpaSort.Path<?, ?>... paths) { return null; }" +
                                                        "public static JpaSort of(Direction direction, Attribute<?, ?>... attributes) { return null; }" +
                                                        "public static JpaSort of(Direction direction, Path<?, ?>... paths) { return null; }" +
                                                        "public static class Path<T, S> {}" +
                                                    "}",
                                                    "package javax.persistence.metamodel; public interface Attribute<X, Y> {}",
                                                    "package org.springframework.data.domain;" +
                                                    "public class Sort implements Streamable<org.springframework.data.domain.Sort.Order>, Serializable {" +
                                                        "public static enum Direction { ASC, DESC; }" +
                                                    "}")
                                            .build())
                                    .build(),
                            newClass.getCoordinates().replace(),
                            newClass.getArguments().toArray());
                }

                return super.visitNewClass(newClass, ctx);
            }
        };
    }
}
