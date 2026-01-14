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
package org.openrewrite.java.spring.framework;

import lombok.Getter;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public class MigrateInstantiationAwareBeanPostProcessorAdapter extends Recipe {
    private final String fromExtendingFqn = "org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter";
    private final String toImplementsFqn = "org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor";

    @Getter
    final String displayName = "Convert `InstantiationAwareBeanPostProcessorAdapter` to `SmartInstantiationAwareBeanPostProcessor`";

    @Getter
    final String description = "As of Spring-Framework 5.3 `InstantiationAwareBeanPostProcessorAdapter` is deprecated in favor of the existing default methods in `SmartInstantiationAwareBeanPostProcessor`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(fromExtendingFqn, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getExtends() != null && TypeUtils.isOfClassType(cd.getExtends().getType(), fromExtendingFqn)) {
                    cd = cd.withExtends(null);
                    J.Identifier ident = new J.Identifier(Tree.randomId(), Space.format(" "), Markers.EMPTY, emptyList(),
                            "SmartInstantiationAwareBeanPostProcessor", JavaType.buildType(toImplementsFqn), null);
                    J.Block body = cd.getBody();
                    cd = maybeAutoFormat(cd, cd.withBody(cd.getBody().withStatements(emptyList())).withImplements(ListUtils.concat(cd.getImplements(), ident)), ctx, getCursor().getParentOrThrow());
                    cd = cd.withBody(body);
                }
                return cd;
            }

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, ctx);
                doAfterVisit(new ChangeType(fromExtendingFqn, toImplementsFqn, false).getVisitor());
                return compilationUnit;
            }
        });
    }
}
