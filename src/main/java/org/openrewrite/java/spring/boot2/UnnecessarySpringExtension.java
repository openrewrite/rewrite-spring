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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotation;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JRightPadded;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;

import static org.openrewrite.Tree.randomId;

public class UnnecessarySpringExtension extends Recipe {
    private static final J.Block EMPTY_BLOCK = new J.Block(randomId(), Space.EMPTY,
            Markers.EMPTY, new JRightPadded<>(false, Space.EMPTY, Markers.EMPTY),
            Collections.emptyList(), Space.EMPTY);

    @Override
    public String getDisplayName() {
        return "Remove `@SpringExtension` if `@SpringBootTest` is present";
    }

    @Override
    public String getDescription() {
        return "`@SpringBootTest` already applies `@SpringExtension` as of Spring Boot 2.1.0.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = classDecl;

                if (!FindAnnotations.find(c.withBody(EMPTY_BLOCK),
                        "@org.springframework.boot.test.context.SpringBootTest").isEmpty()) {
                    c = (J.ClassDeclaration) new RemoveAnnotation(
                            "@org.junit.jupiter.api.extension.ExtendWith(" +
                                    "org.springframework.test.context.junit.jupiter.SpringExtension.class)")
                            .getVisitor()
                            .visit(c.withBody(EMPTY_BLOCK), ctx, getCursor().getParentOrThrow());
                    assert c != null;
                    c = c.withBody(classDecl.getBody());
                    maybeRemoveImport("org.springframework.test.context.junit.jupiter.SpringExtension");
                }

                return super.visitClassDeclaration(c, ctx);
            }
        };
    }
}
