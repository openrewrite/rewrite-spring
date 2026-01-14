/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.java.spring.boot3;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class RemoveEnableBatchProcessing extends Recipe {

    @Getter
    final String displayName = "Enable Spring Batch Annotation";

    @Getter
    final String description = "Add or remove the `@EnableBatchProcessing` annotation from a Spring Boot application.";

    private static final String ENABLE_BATCH_PROCESSING = "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ENABLE_BATCH_PROCESSING, true), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (!FindAnnotations.find(classDecl, "@org.springframework.boot.autoconfigure.SpringBootApplication").isEmpty() &&
                    !FindAnnotations.find(classDecl, "@" + ENABLE_BATCH_PROCESSING).isEmpty()) {
                    return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), a -> {
                        if (TypeUtils.isOfClassType(a.getType(), ENABLE_BATCH_PROCESSING)) {
                            maybeRemoveImport(ENABLE_BATCH_PROCESSING);
                            return null;
                        }
                        return a;
                    }));
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        });
    }
}
