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
package org.openrewrite.java.spring.batch;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateItemWriterWriteCalls extends Recipe {

    private static final String ITEM_WRITER_FQN = "org.springframework.batch.item.ItemWriter";
    private static final String CHUNK_FQN = "org.springframework.batch.item.Chunk";
    private static final String LIST_FQN = "java.util.List";

    @Getter
    final String displayName = "Migrate `ItemWriter.write` call sites";

    @Getter
    final String description = "Wrap `List` arguments to `ItemWriter.write` invocations in `new Chunk<>(...)` to match the Spring Batch 5 `write(Chunk)` signature.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ITEM_WRITER_FQN, true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                if (!"write".equals(mi.getSimpleName()) || mi.getArguments().size() != 1) {
                    return mi;
                }
                Expression arg = mi.getArguments().get(0);
                if (arg instanceof J.Empty) {
                    return mi;
                }
                Expression select = mi.getSelect();
                if (select == null || !TypeUtils.isAssignableTo(ITEM_WRITER_FQN, select.getType())) {
                    return mi;
                }
                if (TypeUtils.isAssignableTo(CHUNK_FQN, arg.getType())) {
                    return mi;
                }
                if (!TypeUtils.isAssignableTo(LIST_FQN, arg.getType())) {
                    return mi;
                }
                maybeAddImport(CHUNK_FQN);
                return JavaTemplate.builder("#{any(org.springframework.batch.item.ItemWriter)}.write(new Chunk<>(#{any(java.util.List)}))")
                        .imports(CHUNK_FQN)
                        .javaParser(JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "spring-batch-core-5.1.+", "spring-batch-infrastructure-5.1.+"))
                        .build()
                        .apply(getCursor(), mi.getCoordinates().replace(), select, arg);
            }
        });
    }
}
