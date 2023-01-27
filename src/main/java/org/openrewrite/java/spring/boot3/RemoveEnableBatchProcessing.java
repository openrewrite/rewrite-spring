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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class RemoveEnableBatchProcessing extends Recipe {

    @Override
    public String getDisplayName() {
        return "Enable Spring Batch Annotation";
    }

    @Override
    public String getDescription() {
        return "Add or remove the `@EnableBatchProcessing` annotation from a Spring Boot application.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        return super.visit(before, ctx);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (!FindAnnotations.find(classDecl, "org.springframework.boot.autoconfigure.SpringBootApplication").isEmpty() &&
                    !FindAnnotations.find(classDecl, "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing").isEmpty()) {
                    return classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), a -> {
                        if (TypeUtils.isOfClassType(a.getType(), "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing")) {
                            return null;
                        }
                        return a;
                    }));
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }
        };
    }
}
