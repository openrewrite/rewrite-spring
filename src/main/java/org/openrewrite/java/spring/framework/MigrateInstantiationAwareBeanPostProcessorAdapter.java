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
package org.openrewrite.java.spring.framework;

import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.UUID;

public class MigrateInstantiationAwareBeanPostProcessorAdapter extends Recipe {
    private final String fromExtendingFqn = "org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter";
    private final String toImplementsFqn = "org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor";

    @Override
    public String getDisplayName() {
        return "Convert `InstantiationAwareBeanPostProcessorAdapter` to `SmartInstantiationAwareBeanPostProcessor`";
    }

    @Override
    public String getDescription() {
        return "As of Spring-Framework 5.3 `InstantiationAwareBeanPostProcessorAdapter` is deprecated in favor of the existing default methods in `SmartInstantiationAwareBeanPostProcessor`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(fromExtendingFqn, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() != null && TypeUtils.isOfClassType(cd.getExtends().getType(), fromExtendingFqn)) {
                    cd = cd.withExtends(null);
                    J.Identifier ident = new J.Identifier(UUID.randomUUID(), Space.format(" "), Markers.EMPTY,
                            "SmartInstantiationAwareBeanPostProcessor", JavaType.buildType(toImplementsFqn), null);
                    J.Block body = cd.getBody();
                    cd = maybeAutoFormat(cd, cd.withBody(cd.getBody().withStatements(Collections.emptyList())).withImplements(ListUtils.concat(cd.getImplements(), ident)), ctx, getCursor());
                    cd = cd.withBody(body);
                }
                return cd;
            }

            @Override
            public @Nullable J visit(@Nullable Tree tree, ExecutionContext ctx) {
                if(tree instanceof JavaSourceFile) {
                    tree = new ChangeType(fromExtendingFqn, toImplementsFqn, false)
                            .getVisitor().visitNonNull(tree, ctx);
                }
                return super.visit(tree, ctx);
            }
        });
    }
}
