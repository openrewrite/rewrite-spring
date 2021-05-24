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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

public class UnnecessarySpringExtension extends Recipe {
    private static final String SPRING_BOOT_TEST_ANNOTATION_PATTERN = "@org.springframework.boot.test.context.SpringBootTest";
    private static final String EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN = "@org.junit.jupiter.api.extension.ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)";

    @Override
    public String getDisplayName() {
        return "Remove `@SpringExtension`";
    }

    @Override
    public String getDescription() {
        return "`@SpringBootTest` already applies `@SpringExtension` as of Spring Boot 2.1.0.";
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.test.context.junit.jupiter.SpringExtension");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;
                //noinspection ConstantConditions
                if (!FindAnnotations.find(c.withBody(null), SPRING_BOOT_TEST_ANNOTATION_PATTERN).isEmpty()) {
                    //noinspection ConstantConditions
                    if (!FindAnnotations.find(c.withBody(null), EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN).isEmpty()) {
                        //noinspection ConstantConditions
                        c = (J.ClassDeclaration) new RemoveAnnotation(EXTEND_WITH_SPRING_EXTENSION_ANNOTATION_PATTERN)
                                .getVisitor()
                                .visit(c.withBody(null), ctx, getCursor().getParentOrThrow());
                        assert c != null;
                        c = c.withBody(classDecl.getBody());
                        maybeRemoveImport("org.springframework.test.context.junit.jupiter.SpringExtension");
                    }
                }

                return super.visitClassDeclaration(c, ctx);
            }
        };
    }
}
